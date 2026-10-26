package com.yenaly.han1meviewer.OnAir.data.parser

import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.model.OnAirQualityLink
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoDetail
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoTag
import com.yenaly.han1meviewer.OnAir.data.model.StreamResult
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirHtmlUtils.cleanText
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirHtmlUtils.parseTrailingNumber
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirHtmlUtils.parseViews
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object OnAirWatchParser {

    private const val TAG = "OnAirWatchParser"
    private const val DEFAULT_QUALITY = "240"

    private val durationAttrRegex = Regex("""data-duration=["']([\d.]+)["']""")
    private val watchQueryQualityRegex = Regex("""[?&]quality=([^&]+)""")

    fun parseWatchPage(html: String, baseUrl: String): OnAirVideoDetail? {
        val doc = Jsoup.parse(html, baseUrl)
        if (doc.selectFirst("h1.watch-title") == null) {
            Log.w(TAG, "Watch page missing h1.watch-title")
            return null
        }

        val title = cleanText(doc.selectFirst("h1.watch-title")?.text())
        val videoId = extractVideoId(doc, baseUrl)
        val currentQuality = extractQualityFromPageUrl(baseUrl) ?: DEFAULT_QUALITY

        val infoCard = doc.selectFirst("div.watch-info-card")
        val uploaderAnchor = infoCard?.selectFirst("a.watch-uploader-name")
        val uploaderName = uploaderAnchor?.text()?.trim()?.takeIf { it.isNotBlank() }
        val uploaderUrl = uploaderAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }

        val statsText = infoCard?.selectFirst("span.watch-stats")?.text()?.trim().orEmpty()
        val statsParts = statsText.split("·").map { it.trim() }
        val viewsRaw = statsParts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val views = parseViews(viewsRaw)
        val duration = statsParts.getOrNull(1)?.takeIf { it.isNotBlank() }
        val fileSize = statsParts.getOrNull(2)?.takeIf { it.isNotBlank() }

        val favoriteButton = infoCard?.selectFirst("button#favorite-btn")
        val favoriteCount = favoriteButton
            ?.selectFirst("span#favorite-count")
            ?.text()?.let(::parseTrailingNumber)
        val isFavorited = favoriteButton?.attr("aria-pressed")?.equals("true", true) == true

        val tags = parseVideoTags(doc)
        val qualityLinks = parseQualityLinks(doc, baseUrl)
        val qualityLinksByQuality = qualityLinks.associate { it.quality to it.url }
        val currentStream = parseCurrentStreamFromDoc(doc, baseUrl, currentQuality)
        val streamUrlByQuality = if (currentStream != null) {
            linkedMapOf(currentQuality to currentStream.url)
        } else {
            linkedMapOf()
        }
        val currentMediaType = currentStream?.mediaType

        val recommended = parseRecommended(doc, baseUrl)

        Log.d(
            TAG,
            "parseWatchPage: id=$videoId quality=$currentQuality " +
                    "streams=${streamUrlByQuality.keys} type=$currentMediaType " +
                    "qualityLinks=${qualityLinksByQuality.keys}",
        )

        return OnAirVideoDetail(
            videoId = videoId,
            title = title,
            uploaderName = uploaderName,
            uploaderUrl = uploaderUrl,
            views = views,
            viewsRaw = viewsRaw,
            duration = duration,
            fileSize = fileSize,
            favoriteCount = favoriteCount,
            isFavorited = isFavorited,
            tags = tags,
            qualityLinks = qualityLinks,
            qualityLinksByQuality = qualityLinksByQuality,
            streamUrlByQuality = streamUrlByQuality,
            currentQuality = currentQuality,
            currentMediaType = currentMediaType,
            recommendedVideos = recommended,
        )
    }

    fun parseStreamFromPage(
        html: String,
        baseUrl: String,
        quality: String,
    ): StreamResult? {
        val doc = Jsoup.parse(html, baseUrl)
        return parseCurrentStreamFromDoc(doc, baseUrl, quality)
    }

    private fun parseCurrentStreamFromDoc(
        doc: Document,
        baseUrl: String,
        quality: String,
    ): StreamResult? {
        val videoEl = doc.selectFirst("video#watch-video")
            ?: doc.selectFirst("video#watch-video-fallback")
            ?: doc.selectFirst("video")
            ?: return null

        val hlsAttr = videoEl.attr("data-hls-src").takeIf { it.isNotBlank() }
        if (hlsAttr != null && quality == "original") {
            val abs = resolveUrl(hlsAttr, baseUrl) ?: return null
            return StreamResult(
                quality = quality,
                url = abs,
                mediaType = "application/vnd.apple.mpegurl",
                isHls = true,
            )
        }

        val sources = videoEl.select("source")
        for (source in sources) {
            val raw = source.attr("src").takeIf { it.isNotBlank() }
                ?: source.attr("data-savepage-src").takeIf { it.isNotBlank() }
                ?: continue
            val abs = resolveUrl(raw, baseUrl) ?: continue
            val fromUrl = qualityOfStreamUrl(abs)
            if (fromUrl == quality || (quality == "original" && fromUrl == null)) {
                val mime = source.attr("type").takeIf { it.isNotBlank() }
                    ?: guessMime(abs)
                return StreamResult(
                    quality = quality,
                    url = abs,
                    mediaType = mime,
                    isHls = abs.contains(".m3u8"),
                )
            }
        }

        if (hlsAttr != null) {
            val abs = resolveUrl(hlsAttr, baseUrl) ?: return null
            return StreamResult(
                quality = quality,
                url = abs,
                mediaType = "application/vnd.apple.mpegurl",
                isHls = true,
            )
        }

        return null
    }

    fun qualityOfStreamUrl(url: String): String? {
        val streamq = Regex("""/streamq/\d+/(\d+)""", RegexOption.IGNORE_CASE).find(url)
        if (streamq != null) return streamq.groupValues[1]
        if (url.contains("/hls/", ignoreCase = true) ||
            url.contains(".m3u8", ignoreCase = true) ||
            Regex("""/stream/[a-f0-9]{16,}""", RegexOption.IGNORE_CASE).containsMatchIn(url)
        ) {
            return "original"
        }
        return null
    }

    private fun extractQualityFromPageUrl(url: String): String? {
        return watchQueryQualityRegex.find(url)?.groupValues?.getOrNull(1)
    }

    private fun guessMime(url: String): String? = when {
        url.contains(".webm", ignoreCase = true) -> "video/webm"
        url.contains(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
        url.contains(".mp4", ignoreCase = true) -> "video/mp4"
        else -> null
    }

    private fun resolveUrl(raw: String, baseUrl: String): String? {
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "http:$raw"
            raw.startsWith("/") -> baseUrl.trimEnd('/') + raw
            else -> baseUrl.trimEnd('/') + "/" + raw
        }
    }

    private fun extractVideoId(doc: Document, baseUrl: String): String {
        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("href")
            ?.takeIf { it.isNotBlank() }
        val fromCanonical = canonical?.let(OnAirHtmlUtils::extractVideoId)
        if (fromCanonical != null) return fromCanonical

        val ogUrl = doc.selectFirst("meta[property=og:url]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
        val fromOg = ogUrl?.let(OnAirHtmlUtils::extractVideoId)
        if (fromOg != null) return fromOg

        val fromBase = OnAirHtmlUtils.extractVideoId(baseUrl)
        if (fromBase != null) return fromBase

        val segment = OnAirHtmlUtils.extractPathSegment(baseUrl)
        if (segment != null && segment.all(Char::isDigit)) return segment

        return ""
    }

    private fun parseVideoTags(doc: Document): List<OnAirVideoTag> {
        return doc.select("ul#tag-list li.tag-pill").mapNotNull { element: Element ->
            val id = element.id().removePrefix("tag-").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val name = element.selectFirst("span.tag-name")?.text()?.let(::cleanText)
                ?: return@mapNotNull null
            val score = element.selectFirst("span.tag-score")
                ?.text()?.toIntOrNull() ?: 0
            OnAirVideoTag(tagId = id, name = name, score = score)
        }
    }

    private fun parseQualityLinks(doc: Document, baseUrl: String): List<OnAirQualityLink> {
        return doc.select("nav.quality-links a.quality-link").mapNotNull { anchor ->
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: resolveUrl(anchor.attr("href"), baseUrl)
                ?: return@mapNotNull null
            val quality = Regex("""[?&]quality=([^&]+)""")
                .find(href)?.groupValues?.getOrNull(1)
                ?: return@mapNotNull null
            val fullText = anchor.text().trim()
            val label = fullText.substringBefore('(').trim().ifBlank { quality }
            val sizeLabel = fullText.substringAfter('(', "").substringBefore(')').trim()
                .takeIf { it.isNotBlank() }?.let { "($it)" }
            val isActive = anchor.hasClass("is-active") ||
                    anchor.attr("aria-current") == "page"
            OnAirQualityLink(
                quality = quality,
                label = label,
                url = href,
                sizeLabel = sizeLabel,
                isActive = isActive,
            )
        }
    }

    private fun parseRecommended(doc: Document, baseUrl: String): List<OnAirVideoCard> {
        val sidebar = doc.selectFirst("div.watch-sidebar-videos")
            ?: doc.selectFirst("div.sidebar-card.watch-sidebar-videos")
            ?: return emptyList()
        return sidebar.select("a.sidebar-item").mapNotNull { anchor ->
            runCatching {
                val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val videoId = OnAirHtmlUtils.extractVideoId(href)
                    ?: return@runCatching null
                val title = anchor.selectFirst("span.sidebar-title")?.text()?.let(::cleanText)
                    ?: return@runCatching null
                val uploader = anchor.selectFirst("span.sidebar-uploader")?.text()
                    ?.trim()?.takeIf { it.isNotBlank() }
                val thumb = anchor.selectFirst("span.sidebar-thumb-wrap img")
                val thumbUrl = thumb?.absUrl("src")?.takeIf { it.isNotBlank() } ?: ""
                val preview = thumb?.attr("data-preview")?.takeIf { it.isNotBlank() }
                val duration = anchor.selectFirst("span.thumb-duration-sm")?.text()?.trim()
                OnAirVideoCard(
                    videoId = videoId,
                    title = title,
                    thumbUrl = thumbUrl,
                    previewUrl = preview,
                    duration = duration,
                    uploaderName = uploader,
                    viewsRaw = null,
                    views = null,
                )
            }.onFailure { Log.w(TAG, "Failed rec card: ${it.message}") }
                .getOrNull()
        }
    }

    fun parseDataDurationSeconds(html: String): Long? {
        val match = durationAttrRegex.find(html) ?: return null
        return match.groupValues[1].toDoubleOrNull()?.toLong()
    }
}
