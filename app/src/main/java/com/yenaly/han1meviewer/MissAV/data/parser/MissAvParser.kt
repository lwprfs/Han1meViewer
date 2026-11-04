package com.yenaly.han1meviewer.MissAV.data.parser

import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.yenaly.han1meviewer.MissAV.data.model.MissAvHomePage
import com.yenaly.han1meviewer.MissAV.data.model.MissAvVideoInfo
import com.yenaly.han1meviewer.MissAV.data.model.Recommendation
import com.yenaly.han1meviewer.MissAV.data.model.RecommendationsResponse
import com.yenaly.han1meviewer.MissAV.data.model.RelatedResponse

object MissAvParser {

    private const val STRIP_SUB_REGEX = "-uncensored-leak|-chinese-subtitle|-english-subtitle"

    fun homePage(body: String): WebsiteState<MissAvHomePage> {
        val document = Jsoup.parse(body)
        val popularVideos = document.select("div.thumbnail").map { parseVideoCard(it) }.toMutableList()
        return WebsiteState.Success(MissAvHomePage(popularVideos = popularVideos, latestVideos = mutableListOf()))
    }

    fun popularPage(body: String): PageLoadingState<MissAvHomePage> {
        val document = Jsoup.parse(body)
        val videos = document.select("div.thumbnail").map { parseVideoCard(it) }.toMutableList()
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return PageLoadingState.Success(MissAvHomePage(popularVideos = videos, latestVideos = mutableListOf(), hasNextPage = hasNextPage))
    }

    fun searchResults(body: String): PageLoadingState<MutableList<HanimeInfo>> {
        val document = Jsoup.parse(body)
        val videos = document.select("div.thumbnail").map { parseVideoCard(it) }.toMutableList()
        return if (videos.isEmpty()) PageLoadingState.NoMoreData else PageLoadingState.Success(videos)
    }

    fun parseApiSearchResults(jsonString: String): PageLoadingState<MutableList<HanimeInfo>> {
        val response = HJson.decodeFromString<RecommendationsResponse>(jsonString)
        val videos = response.recommendations.mapNotNull { parseApiVideoCard(it) }.toMutableList()
        return if (videos.isEmpty()) PageLoadingState.NoMoreData else PageLoadingState.Success(videos)
    }

    fun parseVideoDetail(body: String): VideoLoadingState<MissAvVideoInfo> {
        val document = Jsoup.parse(body)
        val title = document.selectFirst("h1.text-base")?.text()
            ?: return VideoLoadingState.Error(IllegalStateException("Title not found"))
        val coverUrl = document.selectFirst("video.player")?.attr("abs:data-poster") ?: EMPTY_STRING
        val videoCode = extractVideoCode(document)
        val genres = extractInfo(document, "/genres/")
        val directors = extractInfo(document, "/directors/")
        val makers = extractInfo(document, "/makers/")
        val actresses = extractInfo(document, "/actresses/")
        val label = extractInfo(document, "/labels/")
        val series = extractInfo(document, "/series/")
        val description = buildString {
            document.selectFirst("div.mb-1")?.text()?.also { append("$it\n") }
            document.select("div.text-secondary:not(:has(a)):has(span)").eachText().forEach { append("\n$it") }
        }
        val videoUrl = extractVideoUrl(document)
        val relatedVideos = document.select("div.thumbnail").map { parseVideoCard(it) }
        return VideoLoadingState.Success(
            MissAvVideoInfo(title = title, coverUrl = coverUrl, videoCode = videoCode,
                jpTitle = "", genres = genres, directors = directors, makers = makers,
                actresses = actresses, label = label, series = series,
                description = description, videoUrl = videoUrl, relatedVideos = relatedVideos)
        )
    }

    fun parseRelatedVideos(jsonString: String): List<HanimeInfo> {
        val responses = HJson.decodeFromString<List<RelatedResponse>>(jsonString)
        return responses.flatMap { response: RelatedResponse ->
            response.json.recommendations.mapNotNull { parseApiVideoCard(it) }
        }
    }

    fun parseVideoCardPublic(element: Element): HanimeInfo = parseVideoCard(element)

    private fun parseVideoCard(element: Element): HanimeInfo {
        val link = element.select("a.text-secondary")
        return HanimeInfo(
            title = link.text(),
            coverUrl = element.selectFirst("img")?.attr("abs:data-src") ?: EMPTY_STRING,
            videoCode = link.attr("href").substringAfterLast("/"),
            itemType = HanimeInfo.NORMAL,
        )
    }

    private fun parseApiVideoCard(recommendation: Recommendation): HanimeInfo? {
        val videoInfo = recommendation.videoInfo
        if (videoInfo.dm == null || videoInfo.titleEn == null) return null
        val id = recommendation.id
        val strippedId = id.lowercase().replace(Regex(STRIP_SUB_REGEX), "")
        return HanimeInfo(
            title = "${strippedId.uppercase()} ${videoInfo.titleEn}",
            coverUrl = "https://fourhoi.com/$strippedId/cover-t.jpg",
            videoCode = id,
            itemType = HanimeInfo.NORMAL,
        )
    }

    private fun extractVideoCode(document: Element): String {
        return document.selectFirst("meta[property='og:url']")?.attr("content")?.substringAfterLast("/") ?: EMPTY_STRING
    }

    private fun extractInfo(element: Element, urlPart: String): String? {
        return element.select("div.text-secondary > a[href*=$urlPart]").eachText().joinToString().takeIf(String::isNotBlank)
    }

    private val PACKED_SCRIPT = Regex(
        """\}\('((?:[^'\\]|\\.)*)',\s*(\d+),\s*(\d+),\s*'((?:[^'\\]|\\.)*)'\.split\('\|'\)"""
    )
    private val WORD = Regex("""\b\w+\b""")
    private val M3U8_URL = Regex("""https?://[^"'\s<>\\]+\.m3u8[^"'\s<>\\]*""")
    private val SOURCE_ASSIGNMENT = Regex("""source\s*=\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""")

    private fun unpackScripts(script: String): List<String> =
        PACKED_SCRIPT.findAll(script).mapNotNull { m ->
            runCatching {
                val payload = m.groupValues[1].replace("\\'", "'").replace("\\\\", "\\")
                val radix = m.groupValues[2].toInt()
                val count = m.groupValues[3].toInt()
                val dict = m.groupValues[4].split("|")
                if (radix < 2) return@runCatching null
                fun encode(c: Int): String {
                    val prefix = if (c < radix) "" else encode(c / radix)
                    val r = c % radix
                    return prefix + if (r > 35) (r + 29).toChar().toString() else r.toString(36)
                }
                val lookup = HashMap<String, String>(count * 2)
                for (i in 0 until count) {
                    val key = encode(i)
                    lookup[key] = dict.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: key
                }
                WORD.replace(payload) { lookup[it.value] ?: it.value }
            }.getOrNull()
        }.toList()

    private fun extractVideoUrl(document: Element): String? {
        val candidates = mutableListOf<String>()
        for (script in document.select("script")) {
            val data = script.data()
            if (data.isBlank()) continue
            unpackScripts(data).forEach { unpacked ->
                SOURCE_ASSIGNMENT.find(unpacked)?.groupValues?.get(1)?.let { candidates += it }
                M3U8_URL.findAll(unpacked).forEach { candidates += it.value }
            }
            M3U8_URL.findAll(data).forEach { candidates += it.value }
        }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.contains("playlist.m3u8", ignoreCase = true) }
            ?: candidates.first()
    }
}
