package com.yenaly.han1meviewer.OnAir.data.parser

import android.util.Log
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.OnAir.data.model.OnAirAnnouncement
import com.yenaly.han1meviewer.OnAir.data.model.OnAirAnnouncementEntry
import com.yenaly.han1meviewer.OnAir.data.model.OnAirHomePage
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPageInfo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPictureCard
import com.yenaly.han1meviewer.OnAir.data.model.OnAirTag
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirHtmlUtils.cleanText
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirHtmlUtils.parseTrailingLong
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirHtmlUtils.parseViews
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirHtmlUtils.parseViewsCount
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object OnAirListingParser {

    private const val TAG = "OnAirListingParser"

    fun parseHomePage(html: String, baseUrl: String): OnAirHomePage {
        val doc = Jsoup.parse(html, baseUrl)

        val statsBanner = doc.selectFirst(".stats-banner")
        val statsSpans = statsBanner?.select(".stats-banner-item") ?: emptyList()
        val totalVideos = statsSpans.getOrNull(0)
            ?.selectFirst(".stats-banner-value")?.text()?.toIntOrNull()
        val totalPictures = statsSpans.getOrNull(1)
            ?.selectFirst(".stats-banner-value")?.text()?.toIntOrNull()
        val totalMembers = statsSpans.getOrNull(2)
            ?.selectFirst(".stats-banner-value")?.text()?.toIntOrNull()

        val announcement = doc.selectFirst(".announcement-banner-link")?.let { el ->
            val title = el.selectFirst(".announcement-banner-title")?.text()?.trim()
            val href = el.absUrl("href").takeIf { it.isNotBlank() }
            if (title != null && href != null) {
                OnAirAnnouncement(title = title, url = href)
            } else null
        }

        val cards = parseVideoCards(doc, baseUrl)
        val pageInfo = parsePaginator(doc, baseUrl)
        val currentUsername = parseCurrentUsername(doc)

        return OnAirHomePage(
            videos = cards,
            totalVideos = totalVideos,
            totalPictures = totalPictures,
            totalMembers = totalMembers,
            announcement = announcement,
            pageInfo = pageInfo,
            currentUsername = currentUsername,
        )
    }

    fun parseCurrentUsername(doc: Document): String? =
        doc.selectFirst("summary.nav-username")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }

    fun parseCurrentUsername(html: String): String? =
        parseCurrentUsername(Jsoup.parse(html))

    fun parseUserBio(doc: Document): String? =
        doc.selectFirst("div.profile-info p.watch-description")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }

    fun parseUserAvatar(doc: Document): String? {
        val header = doc.selectFirst("div.profile-header") ?: return null
        val img = header.selectFirst("span.avatar-wrap img")
            ?: header.selectFirst("span.thumb-wrap img")
        return img?.absUrl("src")?.takeIf { it.isNotBlank() }
    }

    fun parseUserFollowerCount(doc: Document): Int? =
        doc.selectFirst("div.profile-header p.video-meta a[href*=/followers]")
            ?.text()?.let(::parseTrailingLong)?.toInt()

    fun parseUserFollowingCount(doc: Document): Int? =
        doc.selectFirst("div.profile-header p.video-meta a[href*=/following]")
            ?.text()?.let(::parseTrailingLong)?.toInt()

    fun parseUserVideoCount(doc: Document): Int? {
        val meta = doc.selectFirst("div.profile-header p.video-meta") ?: return null
        val anchorTexts = meta.select("a").map { it.text() }.toSet()
        val tokens = meta.text().split("·").map { it.trim() }.filter { it.isNotEmpty() }
        val videoToken = tokens.firstOrNull { it.contains("video", ignoreCase = true) }
        if (videoToken != null) {
            return videoToken.substringBefore(' ').toIntOrNull()
        }
        return tokens.filter { it !in anchorTexts }
            .firstOrNull { !it.contains("picture", ignoreCase = true) }
            ?.substringBefore(' ')?.toIntOrNull()
    }

    fun parseUserPictureCount(doc: Document): Int? {
        val meta = doc.selectFirst("div.profile-header p.video-meta") ?: return null
        val tokens = meta.text().split("·").map { it.trim() }.filter { it.isNotEmpty() }
        return tokens.firstOrNull { it.contains("picture", ignoreCase = true) }
            ?.substringBefore(' ')?.toIntOrNull()
    }

    fun parseVideoCards(doc: Document, baseUrl: String): List<OnAirVideoCard> {
        return doc.select("div.video-grid a.video-card").mapNotNull { element ->
            runCatching { parseVideoCard(element, baseUrl) }
                .onFailure { Log.w(TAG, "Failed parsing card: ${it.message}") }
                .getOrNull()
        }
    }

    fun parseVideoCards(html: String, baseUrl: String): List<OnAirVideoCard> =
        parseVideoCards(Jsoup.parse(html, baseUrl), baseUrl)

    private fun parseVideoCard(element: Element, baseUrl: String): OnAirVideoCard? {
        val href = element.absUrl("href").takeIf { it.isNotBlank() }
            ?: return null
        val videoId = OnAirHtmlUtils.extractVideoId(href)
            ?: OnAirHtmlUtils.extractVideoId(element.attr("href"))
            ?: return null
        val title = element.selectFirst("span.video-title")?.text()?.let(::cleanText)
            ?: return null
        val thumbWrap = element.selectFirst("span.thumb-wrap")
        val img = thumbWrap?.selectFirst("img")
        val thumbUrl = img?.absUrl("src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-savepage-currentsrc")?.takeIf { it.isNotBlank() }
            ?: ""
        val previewUrl = img?.attr("data-preview")?.takeIf { it.isNotBlank() }
        val duration = thumbWrap?.selectFirst("span.thumb-duration")?.text()?.trim()
        val meta = element.selectFirst("span.video-meta")?.text()?.trim()
        var uploader: String? = null
        var viewsRaw: String? = null
        var views: Long? = null
        if (meta != null) {
            val parts = meta.split("·").map { it.trim() }
            if (parts.size >= 2) {
                uploader = parts.getOrNull(0)?.takeIf { it.isNotBlank() && it != "·" }
                viewsRaw = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                views = parseViews(viewsRaw)
            }
        }
        return OnAirVideoCard(
            videoId = videoId,
            title = title,
            thumbUrl = thumbUrl,
            previewUrl = previewUrl,
            duration = duration,
            uploaderName = uploader,
            viewsRaw = viewsRaw,
            views = views,
        )
    }

    fun parsePaginator(doc: Document, baseUrl: String): OnAirPageInfo? {
        val pagination = doc.selectFirst("nav.pagination")
            ?: doc.selectFirst("nav[aria-label=Pagination]")
            ?: return null

        val jumpForm = pagination.selectFirst("form.page-jump")
        val jumpInput = jumpForm?.selectFirst("input[name=page]")
        val hiddenField = jumpForm?.selectFirst("input[name=field]")?.attr("value").orEmpty()
        val hiddenSort = jumpForm?.selectFirst("input[name=sort]")?.attr("value").orEmpty()

        val statusText = pagination.text().orEmpty()
        val statusRegex = Regex("""Page\s+(\d+)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE)

        val currentFromInput = jumpInput?.attr("value")?.toIntOrNull()
        val totalFromMax = jumpInput?.attr("max")?.toIntOrNull()
        val statusMatch = statusRegex.find(statusText)
        val currentFromStatus = statusMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val totalFromStatus = statusMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

        val current = currentFromInput ?: currentFromStatus ?: 1
        val total = totalFromMax ?: totalFromStatus ?: 1

        val anchors = pagination.select("a.page-link[href]")
        val nextAnchor = anchors.firstOrNull {
            it.text().contains("next", ignoreCase = true) ||
                    it.text().contains("→", ignoreCase = true)
        }
        val prevAnchor = anchors.firstOrNull {
            it.text().contains("prev", ignoreCase = true) ||
                    it.text().contains("←", ignoreCase = true)
        }

        val nextUrl = nextAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
        val prevUrl = prevAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }

        val jumpAction = jumpForm?.absUrl("action")?.takeIf { it.isNotBlank() }
        val lastUrl = if (jumpAction != null && total > 0) {
            val builder = jumpAction.toHttpUrlOrNull()?.newBuilder()
            if (builder != null) {
                if (hiddenField.isNotBlank()) builder.setQueryParameter("field", hiddenField)
                if (hiddenSort.isNotBlank()) builder.setQueryParameter("sort", hiddenSort)
                builder.setQueryParameter("page", total.toString())
                builder.build().toString()
            } else {
                val base = jumpAction.trimEnd('/')
                "$base?page=$total"
            }
        } else null

        return OnAirPageInfo(
            current = current,
            total = total,
            nextUrl = nextUrl,
            prevUrl = prevUrl,
            lastUrl = lastUrl,
        )
    }

    fun parseHeading(html: String, baseUrl: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.selectFirst("h1.page-title")?.text()?.trim()?.takeIf { it.isNotBlank() }
    }

    fun parsePictureCards(doc: Document, baseUrl: String): List<OnAirPictureCard> {
        return doc.select("div.picture-grid a.picture-card").mapNotNull { element ->
            runCatching {
                val href = element.absUrl("href").takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val pictureId = OnAirHtmlUtils.extractVideoId(href)
                    ?: OnAirHtmlUtils.extractPathSegment(href)
                    ?: return@runCatching null
                val title = element.selectFirst("span.video-title")?.text()?.let(::cleanText)
                    ?: return@runCatching null
                val img = element.selectFirst("span.thumb-wrap img")
                val thumbUrl = img?.absUrl("src")?.takeIf { it.isNotBlank() } ?: ""
                val meta = element.selectFirst("span.video-meta")?.text()?.trim().orEmpty()
                val parts = meta.split("·").map { it.trim() }
                val uploader = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                val viewsRaw = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                OnAirPictureCard(
                    pictureId = pictureId,
                    title = title,
                    thumbUrl = thumbUrl,
                    uploaderName = uploader,
                    viewsRaw = viewsRaw,
                    views = parseViews(viewsRaw),
                )
            }.onFailure { Log.w(TAG, "Failed picture card: ${it.message}") }
                .getOrNull()
        }
    }

    fun parseTagCloud(doc: Document): List<OnAirTag> {
        return doc.select("div.tag-cloud a.tag-cloud-item").mapNotNull { element ->
            val href = element.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.ownText()?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val count = element.selectFirst("span.tag-cloud-count")
                ?.text()?.let(::parseTrailingLong)?.toInt()
            OnAirTag(name = name, url = href, count = count)
        }
    }

    fun parseAnnouncementEntries(doc: Document): List<OnAirAnnouncementEntry> {
        return doc.select("div.announcement-list article.announcement-card").map { card ->
            val title = card.selectFirst("h2.announcement-card-title, h3.announcement-card-title")
                ?.text()?.trim().orEmpty()
            val pinned = card.selectFirst("span.announcement-pin") != null
            val meta = card.selectFirst("p.video-meta")?.text()?.trim().orEmpty()
            val author = meta.substringBefore("·").trim().takeIf { it.isNotBlank() }
            val body = card.selectFirst("p.announcement-card-body")?.text()?.trim().orEmpty()
            OnAirAnnouncementEntry(
                title = title,
                author = author,
                body = body,
                isPinned = pinned,
            )
        }
    }

    fun isLoginRequiredPage(html: String): Boolean {
        return html.contains("action=\"/login\"", ignoreCase = true) &&
                !html.contains("user-modal-name")
    }

    fun parseConnLabel(doc: Document): String? =
        doc.selectFirst("#conn-label")?.text()?.trim()?.takeIf { it.isNotBlank() }

    fun expectedDefaultPageSize(): Int = OnAirConstants.DEFAULT_PAGE_SIZE
}
