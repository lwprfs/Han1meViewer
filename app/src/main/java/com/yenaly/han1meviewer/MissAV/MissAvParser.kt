package com.yenaly.han1meviewer.MissAV

import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.MissAV.MissAvHomePage
import com.yenaly.han1meviewer.MissAV.MissAvVideoInfo
import com.yenaly.han1meviewer.MissAV.Recommendation
import com.yenaly.han1meviewer.MissAV.RecommendationsResponse
import com.yenaly.han1meviewer.MissAV.RelatedResponse
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

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

    private fun extractVideoUrl(document: Element): String? {
        val scripts = document.select("script")
        for (script in scripts) {
            val data = script.data()
            if (data.isBlank()) continue

            // Try to find m3u8 URL directly in the script
            val m3u8Regex = Regex("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""")
            val match = m3u8Regex.find(data)
            if (match != null) return match.value
            
            // Try to find video URL in JavaScript
            val videoRegex = Regex("""["'](https?://[^"'\s<>]+\.(?:mp4|m3u8)[^"'\s<>]*)["']""")
            val videoMatch = videoRegex.find(data)
            if (videoMatch != null) return videoMatch.groupValues[1]
        }
        return null
    }
}