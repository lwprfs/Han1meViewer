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

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun parseVideoList(body: String): PageLoadingState<List<HanimeInfo>> {
        return try {
            val videos = animeListFromDocument(Jsoup.parse(body))
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
            val doc = Jsoup.parse(body)
            val videos = if (isFilterSearch) {
                filterAnimeListFromDocument(doc)
            } else {
                animeListFromDocument(doc)
            }
            if (videos.isEmpty()) PageLoadingState.NoMoreData
            else PageLoadingState.Success(videos)
        } catch (e: Exception) {
            PageLoadingState.Error(e)
        }
    }

    private fun animeListFromDocument(document: Document): List<HanimeInfo> =
        document.select("article.series-card").mapNotNull { element ->
            try {
                val href = element.select("a.sc-poster").attr("href")
                val code = href.trimEnd('/').substringAfterLast("/")
                if (code.isBlank()) return@mapNotNull null
                val title = element.select("h3.sc-title a").text()
                if (title.isBlank()) return@mapNotNull null
                val thumb = element.select("a.sc-poster img").attr("src")
                    .ifEmpty { element.select("a.sc-poster img").attr("data-src") }
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

    private fun filterAnimeListFromDocument(document: Document): List<HanimeInfo> {
        val primary = animeListFromDocument(document)
        if (primary.isNotEmpty()) return primary
        return document.select("article").mapNotNull { searchAnimeFromElement(it) }
    }

    private fun searchAnimeFromElement(element: Element): HanimeInfo? {
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
            val thumb = element.selectFirst("a.sc-poster img")?.attr("src")
                ?: element.selectFirst("div.image div a img")?.attr("src")
                ?: ""
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
            val doc = Jsoup.parse(body)
            val title = doc.selectFirst("h1.dsc-title")?.text()
                ?: doc.selectFirst("h1")?.text()
                ?: return VideoLoadingState.Error(IllegalStateException("Title not found"))

            val thumbnailUrl = doc.selectFirst("div.dsc-poster img")?.attr("src")
                ?: doc.selectFirst("div.dsc-poster img")?.attr("data-src")
                ?: EMPTY_STRING

            val genre = doc.select("div.dsc-genres a").joinToString(", ") { it.text() }
            val description = doc.select("div.dsc-desc p").text()

            val author = doc.select("div.dsc-stats div.dsc-stat")
                .firstOrNull { it.select("span").text().equals("Studio", ignoreCase = true) }
                ?.select("b")?.text()
                ?.takeUnless { it.isBlank() || it == "\u2014" }

            val status = if (doc.select("span.dsc-chip.is-airing").isNotEmpty()) "Ongoing"
            else "Completed"

            val episodes = episodeListFromDocument(doc)
            val code = url.trimEnd('/').substringAfterLast("/")

            val related = doc.select("article.series-card").mapNotNull { el ->
                animeListFromDocument(Jsoup.parseBodyFragment(el.outerHtml()))
                    .firstOrNull()
            }
                .filter { it.videoCode.isNotBlank() && it.videoCode != code }
                .distinctBy { it.videoCode }

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

    private fun episodeListFromDocument(document: Document): List<HentaiMamaEpisode> =
        document.select("div.dt-se-list a.dt-se-item").mapNotNull { element ->
            try {
                val href = element.attr("href")
                if (href.isBlank()) return@mapNotNull null
                val titleText = element.select(".dt-se-title").text()
                val dateText = element.select("span.dt-se-date").text()
                val epNum = EPISODE_NUMBER_REGEX.find(titleText)
                    ?.groups?.get(1)?.value?.toFloatOrNull() ?: -1f
                val dateTimestamp = runCatching {
                    EPISODE_DATE_FORMAT.parse(dateText)?.time
                }.getOrNull() ?: 0L

                HentaiMamaEpisode(
                    title = titleText,
                    url = href,
                    date = dateText.takeIf { it.isNotBlank() },
                    episodeNumber = epNum.takeIf { it > 0f },
                    dateTimestamp = dateTimestamp,
                )
            } catch (e: Exception) {
                null
            }
        }.reversed()

    fun videoListParse(
        detailPageBody: String,
        baseUrl: String,
        apiUrl: String,
        optionNumber: Int,
    ): List<HentaiMamaVideoLink> {
        try {

            val document = Jsoup.parse(detailPageBody)
            val postId = document.select("#post_report input[name=idpost]").attr("value")
            if (postId.isBlank()) {
                Log.e(TAG, "videoListParse: idpost not found in detail page")
                return emptyList()
            }

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
            val iframeSrc = parsedFragment.selectFirst("iframe")?.attr("abs:src")
                ?: parsedFragment.selectFirst("iframe")?.attr("src")
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
