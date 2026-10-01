package com.yenaly.han1meviewer.HentaiMama

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

object HentaiMamaParser {

    private const val TAG = "HentaiMamaParser"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val EPISODE_NUMBER_REGEX = Regex("Episode (\\d+\\.?\\d*)")
    private val EPISODE_DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    private val SOURCES_ARRAY_REGEX =
        Regex("sources:\\s*(\\[.+?\\])", RegexOption.DOT_MATCHES_ALL)

    private val SOURCES_QUOTED_REGEX =
        Regex("\"sources\"\\s*:\\s*(\\[.+?\\])", RegexOption.DOT_MATCHES_ALL)

    private val IDPOST_REGEX =
        Regex("""["']?idpost["']?\s*[:=]\s*["']?(\d+)""", RegexOption.IGNORE_CASE)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun resolveUrl(raw: String?, baseUrl: String): String {
        if (raw.isNullOrBlank()) return EMPTY_STRING
        return when {
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> baseUrl.trimEnd('/') + raw
            else -> baseUrl.trimEnd('/') + "/" + raw
        }
    }

    fun parseVideoList(body: String): PageLoadingState<List<HanimeInfo>> {
        return try {
            val base = HentaiMamaNetwork.baseUrl
            val videos = animeListFromDocument(Jsoup.parse(body, base), base)
            if (videos.isEmpty()) PageLoadingState.NoMoreData
            else PageLoadingState.Success(videos)
        } catch (e: Exception) {
            PageLoadingState.Error(e)
        }
    }

    fun parseSearchResults(
        body: String,
        isFilterSearch: Boolean,
    ): PageLoadingState<List<HanimeInfo>> {
        return try {
            val base = HentaiMamaNetwork.baseUrl
            val doc = Jsoup.parse(body, base)
            val videos = if (isFilterSearch) {
                filterAnimeListFromDocument(doc, base)
            } else {
                animeListFromDocument(doc, base)
            }
            if (videos.isEmpty()) PageLoadingState.NoMoreData
            else PageLoadingState.Success(videos)
        } catch (e: Exception) {
            PageLoadingState.Error(e)
        }
    }

    private fun animeListFromDocument(
        document: Document,
        baseUrl: String,
    ): List<HanimeInfo> =
        document.select("article.series-card").mapNotNull { element ->
            try {
                val href = element.select("a.sc-poster").attr("href")
                val code = href.trimEnd('/').substringAfterLast("/")
                if (code.isBlank()) return@mapNotNull null
                val title = element.select("h3.sc-title a").text()
                if (title.isBlank()) return@mapNotNull null
                val img = element.selectFirst("a.sc-poster img")
                val thumb = img?.absUrl("src").orEmpty()
                    .ifBlank { img?.absUrl("data-src").orEmpty() }
                    .ifBlank { resolveUrl(img?.attr("src"), baseUrl) }
                    .ifBlank { resolveUrl(img?.attr("data-src"), baseUrl) }
                HanimeInfo(
                    title = title,
                    coverUrl = thumb,
                    videoCode = code,
                    itemType = HanimeInfo.NORMAL,
                )
            } catch (e: Exception) {
                null
            }
        }

    private fun filterAnimeListFromDocument(
        document: Document,
        baseUrl: String,
    ): List<HanimeInfo> {
        val primary = animeListFromDocument(document, baseUrl)
        if (primary.isNotEmpty()) return primary
        return document.select("article").mapNotNull { searchAnimeFromElement(it, baseUrl) }
    }

    private fun searchAnimeFromElement(
        element: Element,
        baseUrl: String,
    ): HanimeInfo? {
        return try {
            val link = element.selectFirst("a.sc-poster")
                ?: element.selectFirst("div.details > div.title a")
                ?: element.selectFirst("a")
                ?: return null
            val href = link.attr("href")
            val code = href.trimEnd('/').substringAfterLast("/")
            if (code.isBlank()) return null
            val title = element.selectFirst("h3.sc-title a")?.text()
                ?: element.selectFirst("div.details > div.title a")?.text()
                ?: element.selectFirst("h3")?.text()
                ?: return null
            if (title.isBlank()) return null
            val img = element.selectFirst("a.sc-poster img")
                ?: element.selectFirst("div.image div a img")
            val thumb = img?.absUrl("src").orEmpty()
                .ifBlank { img?.absUrl("data-src").orEmpty() }
                .ifBlank { resolveUrl(img?.attr("src"), baseUrl) }
                .ifBlank { resolveUrl(img?.attr("data-src"), baseUrl) }
            HanimeInfo(
                title = title,
                coverUrl = thumb,
                videoCode = code,
                itemType = HanimeInfo.NORMAL,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseVideoDetail(body: String, url: String): VideoLoadingState<HentaiMamaVideoInfo> {
        return try {
            val base = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
            val doc = Jsoup.parse(body, base)

            val title = doc.selectFirst("h1.dsc-title")?.text()
                ?: doc.selectFirst("h1.title, h1.entry-title, h1")?.text()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: return VideoLoadingState.Error(IllegalStateException("Title not found"))

            val posterImg = doc.selectFirst("div.dsc-poster img")
            val thumbnailUrl = posterImg?.absUrl("src").orEmpty()
                .ifBlank { posterImg?.absUrl("data-src").orEmpty() }
                .ifBlank { resolveUrl(posterImg?.attr("src"), base) }
                .ifBlank { resolveUrl(posterImg?.attr("data-src"), base) }
                .ifBlank { doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }
                .ifBlank { doc.selectFirst("meta[name=twitter:image]")?.attr("content").orEmpty() }

            val genre = doc.select("div.dsc-genres a").joinToString(", ") { it.text() }
                .ifBlank {
                    doc.select("div.genres a, span.genres a, a[href*=/genres/]")
                        .joinToString(", ") { it.text() }
                }
                .ifBlank {
                    doc.selectFirst("meta[property=article:section]")?.attr("content").orEmpty()
                }

            val description = doc.select("div.dsc-desc p").text()
                .ifBlank { doc.select("div.description p, div.synopsis p, div.dsc-desc").text() }
                .ifBlank { doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty() }
                .ifBlank { doc.selectFirst("meta[name=description]")?.attr("content").orEmpty() }
                .ifBlank {
                    doc.select("div.entry-content p, p.storyline, div.storyline")
                        .joinToString("\n") { it.text() }
                }

            val author = doc.select("div.dsc-stats div.dsc-stat")
                .firstOrNull { it.select("span").text().equals("Studio", ignoreCase = true) }
                ?.select("b")?.text()
                ?.takeUnless { it.isBlank() || it == "\u2014" }
                ?: doc.select("div.dsc-stats div.dsc-stat")
                    .firstOrNull { it.select("span").text().equals("Author", ignoreCase = true) }
                    ?.select("b")?.text()
                    ?.takeUnless { it.isBlank() || it == "\u2014" }
                ?: doc.select("a[href*=/studios/], a[href*=/producers/]").firstOrNull()?.text()

            val status = when {
                doc.select("span.dsc-chip.is-airing").isNotEmpty() -> "Ongoing"
                doc.select("span.dsc-chip.is-completed").isNotEmpty() -> "Completed"
                else -> "Completed"
            }

            val episodes = episodeListFromDocument(doc, base)
            val code = url.trimEnd('/').substringAfterLast("/")

            val related = doc.select("article.series-card").mapNotNull { el ->
                animeListFromDocument(Jsoup.parseBodyFragment(el.outerHtml(), base), base)
                    .firstOrNull()
            }
                .filter { it.videoCode.isNotBlank() && it.videoCode != code }
                .distinctBy { it.videoCode }

            Log.d(
                TAG,
                "parseVideoDetail: url=$url len=${body.length} " +
                        "h1=${doc.selectFirst("h1")?.className().orEmpty()} " +
                        "genresEl=${doc.selectFirst("div.dsc-genres") != null} " +
                        "descEl=${doc.selectFirst("div.dsc-desc") != null} " +
                        "statsEl=${doc.selectFirst("div.dsc-stats") != null} " +
                        "genre='$genre' author='$author' " +
                        "descLen=${description.length} episodes=${episodes.size} " +
                        "episodeRange=${episodes.firstOrNull()?.episodeNumber}..${episodes.lastOrNull()?.episodeNumber}"
            )

            VideoLoadingState.Success(
                HentaiMamaVideoInfo(
                    title = title,
                    coverUrl = thumbnailUrl,
                    videoCode = code,
                    url = url,
                    description = description,
                    genre = genre,
                    author = author,
                    status = status,
                    videoUrls = emptyList(),
                    episodes = episodes,
                    relatedVideos = related,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseVideoDetail error", e)
            VideoLoadingState.Error(e)
        }
    }

    /**
     * Parses the episode list from the series page and returns them in
     * **ascending order by episode number** (Episode 1 first, Episode N last).
     *
     * The HentaiMama site renders episodes newest-first in the DOM, so we
     * sort by the parsed episode number to guarantee a stable order regardless
     * of DOM ordering quirks. Episodes whose number cannot be parsed fall back
     * to their DOM position, and remain at the end of the list.
     */
    private fun episodeListFromDocument(
        document: Document,
        baseUrl: String,
    ): List<HentaiMamaEpisode> {
        data class IndexedEpisode(
            val index: Int,
            val episode: HentaiMamaEpisode,
        )

        val indexed = document.select("div.dt-se-list a.dt-se-item")
            .mapIndexedNotNull { index, element ->
                try {
                    val href = element.attr("href")
                    if (href.isBlank()) return@mapIndexedNotNull null
                    val absoluteHref = resolveUrl(href, baseUrl)
                    val titleText = element.select(".dt-se-title").text()
                    val dateText = element.select("span.dt-se-date").text()
                    val epNum = EPISODE_NUMBER_REGEX.find(titleText)
                        ?.groups?.get(1)?.value?.toFloatOrNull() ?: -1f
                    val dateTimestamp = runCatching {
                        EPISODE_DATE_FORMAT.parse(dateText)?.time
                    }.getOrNull() ?: 0L

                    IndexedEpisode(
                        index = index,
                        episode = HentaiMamaEpisode(
                            title = titleText,
                            url = absoluteHref,
                            date = dateText.takeIf { it.isNotBlank() },
                            episodeNumber = epNum.takeIf { it > 0f },
                            dateTimestamp = dateTimestamp,
                        ),
                    )
                } catch (e: Exception) {
                    null
                }
            }

        return indexed
            .sortedWith(
                compareBy<IndexedEpisode> {
                    // Episodes with a valid number come first, sorted ascending.
                    // Episodes without a number fall to the end, ordered by DOM position.
                    it.episode.episodeNumber ?: Float.MAX_VALUE
                }.thenBy { it.index }
            )
            .map { it.episode }
    }

    fun videoListParse(
        detailPageBody: String,
        baseUrl: String,
        apiUrl: String,
        optionNumber: Int,
    ): List<HentaiMamaVideoLink> {
        try {
            val document = Jsoup.parse(detailPageBody, baseUrl)

            val postId = listOf(
                "#post_report input[name=idpost]",
                "input[name=idpost]",
                "input[name=post_id]",
                "input[name=post-id]",
                "#post_id",
                "meta[name=post_id]",
                "input[name=post-id-hidden]"
            ).firstNotNullOfOrNull { sel ->
                document.selectFirst(sel)?.let { el ->
                    el.attr("value").ifBlank { el.attr("content") }.takeIf { it.isNotBlank() }
                }
            } ?: IDPOST_REGEX.find(detailPageBody)?.groupValues?.getOrNull(1)

            if (postId.isNullOrBlank()) {
                Log.e(TAG, "videoListParse: idpost not found in detail page")
                Log.d(TAG, "videoListParse: page snippet = ${detailPageBody.take(1500)}")
                return emptyList()
            }

            Log.d(TAG, "videoListParse: postId=$postId option=$optionNumber")

            val body = FormBody.Builder()
                .add("action", HentaiMamaConstants.ACTION_PLAYER)
                .add("a", postId)
                .add("i", optionNumber.toString())
                .build()

            val headers = Headers.headersOf("referer", "$baseUrl/")

            val request = Request.Builder()
                .url(apiUrl)
                .post(body)
                .headers(headers)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()
            response.close()

            if (responseString.isBlank()) {
                Log.e(TAG, "videoListParse: empty AJAX response (option=$optionNumber)")
                return emptyList()
            }

            Log.d(TAG, "videoListParse: AJAX response length=${responseString.length}")

            val fragments: List<String> = try {
                json.decodeFromString(responseString)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "videoListParse: response was not a JSON array, using as single fragment: ${e.message}"
                )
                listOf(responseString)
            }

            val index = (optionNumber - 1).coerceAtLeast(0)
            val fragment = fragments.getOrNull(index)?.takeUnless { it.isBlank() }
                ?: fragments.firstOrNull { it.isNotBlank() }
                ?: run {
                    Log.e(
                        TAG,
                        "videoListParse: no fragment for option=$optionNumber (size=${fragments.size})"
                    )
                    return emptyList()
                }

            val parsedFragment = Jsoup.parseBodyFragment(fragment, baseUrl)
            val iframeSrc = parsedFragment.selectFirst("iframe")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
                ?: parsedFragment.selectFirst("iframe")?.attr("src")
                    ?.let { resolveUrl(it, baseUrl) }
                ?: run {
                    Log.e(TAG, "videoListParse: no <iframe> in fragment")
                    return emptyList()
                }

            Log.d(TAG, "videoListParse: iframeSrc=$iframeSrc")

            val playerRequest = Request.Builder()
                .url(iframeSrc)
                .addHeader("Referer", baseUrl)
                .build()
            val playerResponse = httpClient.newCall(playerRequest).execute()
            val playerBody = playerResponse.body?.string().orEmpty()
            playerResponse.close()

            if (playerBody.isBlank()) {
                Log.e(TAG, "videoListParse: empty player body from $iframeSrc")
                return emptyList()
            }

            val sourcesJson = SOURCES_ARRAY_REGEX.find(playerBody)
                ?.groupValues?.get(1)
                ?: SOURCES_QUOTED_REGEX.find(playerBody)
                    ?.groupValues?.get(1)
                ?: run {
                    Log.e(TAG, "videoListParse: `sources: [...]` not found in player body")
                    Log.d(TAG, "videoListParse: player body snippet = ${playerBody.take(800)}")
                    return emptyList()
                }

            Log.d(TAG, "videoListParse: raw sources JSON = $sourcesJson")

            val sources: List<HentaiMamaSource> = try {
                json.decodeFromString(sourcesJson)
            } catch (e: Exception) {
                Log.e(TAG, "videoListParse: sources decode failed", e)
                return emptyList()
            }

            val links = sources.mapNotNull { source ->
                val file = source.file.replace("\\/", "/")
                if (file.isBlank()) return@mapNotNull null
                val isHls = source.type == "hls" || file.contains(".m3u8")
                val label = source.label ?: if (isHls) "HLS" else "Video"
                HentaiMamaVideoLink(
                    quality = label,
                    url = file,
                    type = source.type ?: if (isHls) "hls" else "mp4",
                )
            }

            Log.d(TAG, "videoListParse: extracted ${links.size} link(s) for option=$optionNumber")
            return links
        } catch (e: Exception) {
            Log.e(TAG, "videoListParse error for option=$optionNumber", e)
            return emptyList()
        }
    }

    fun hosterTabs(detailPageBody: String): List<Pair<String, Int>> {
        val doc = Jsoup.parse(detailPageBody)
        val tabs = doc.select(".dt-mi-tabs a").mapNotNull { tab ->
            val optionNumber = tab.attr("href").removePrefix("#option-")
            val serverId = tab.text()
            if (optionNumber.isBlank() || serverId.isBlank()) null
            else serverId to (optionNumber.toIntOrNull() ?: return@mapNotNull null)
        }
        Log.d(TAG, "hosterTabs: found ${tabs.size} tab(s): ${tabs.joinToString()}")
        return tabs
    }
}
