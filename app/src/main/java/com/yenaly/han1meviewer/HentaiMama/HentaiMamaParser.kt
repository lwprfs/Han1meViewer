package com.yenaly.han1meviewer.HentaiMama

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

object HentaiMamaParser {

    private const val TAG = "HentaiMamaParser"

    // ==================== HOME PAGE ====================
    
    fun homePage(body: String): WebsiteState<HentaiMamaHomePage> {
        try {
            val doc = Jsoup.parse(body)
            val elements = doc.select("article.tvshows")
            Log.d(TAG, "homePage: Found ${elements.size} elements with 'article.tvshows'")
            
            if (elements.isEmpty()) {
                return WebsiteState.Error(IllegalStateException("No videos found"))
            }
            
            val videos = elements.mapNotNull { popularAnimeFromElement(it) }
                .filter { it.videoCode.isNotEmpty() && it.videoCode != "unknown" }
            
            return if (videos.isNotEmpty()) {
                WebsiteState.Success(HentaiMamaHomePage(popularVideos = videos, latestVideos = videos))
            } else {
                WebsiteState.Error(IllegalStateException("Parsed 0 valid videos"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing homepage", e)
            return WebsiteState.Error(e)
        }
    }

    private fun popularAnimeFromElement(element: Element): HanimeInfo? {
        return try {
            val url = element.select("a").attr("href")
            val videoCode = url.trimEnd('/').substringAfterLast("/")
            if (videoCode.isBlank()) return null
            
            val title = element.select("div.data h3 a").text()
            if (title.isBlank()) return null
            
            val thumbnailUrl = element.select("div.poster img").attr("data-src")
                .ifEmpty { element.select("div.poster img").attr("src") }
            
            HanimeInfo(title = title, coverUrl = thumbnailUrl, videoCode = videoCode, itemType = HanimeInfo.NORMAL)
        } catch (e: Exception) { null }
    }

    fun parseVideoList(body: String): PageLoadingState<List<HanimeInfo>> {
        try {
            val doc = Jsoup.parse(body)
            val elements = doc.select("article.tvshows")
            val videos = elements.mapNotNull { popularAnimeFromElement(it) }.filter { it.videoCode.isNotEmpty() }
            return if (videos.isEmpty()) PageLoadingState.NoMoreData else PageLoadingState.Success(videos)
        } catch (e: Exception) { return PageLoadingState.Error(e) }
    }

    // ==================== SEARCH ====================
    
    private var filterSearch = false

    fun parseSearchResults(body: String, isFilterSearch: Boolean = false): PageLoadingState<List<HanimeInfo>> {
        try {
            filterSearch = isFilterSearch
            val doc = Jsoup.parse(body)
            val elements = doc.select("article")
            val videos = elements.mapNotNull { searchAnimeFromElement(it) }.filter { it.videoCode.isNotEmpty() }
            return if (videos.isEmpty()) PageLoadingState.NoMoreData else PageLoadingState.Success(videos)
        } catch (e: Exception) { return PageLoadingState.Error(e) }
    }

    private fun searchAnimeFromElement(element: Element): HanimeInfo? {
        return try {
            if (filterSearch) {
                val url = element.select("a").attr("href")
                val videoCode = url.trimEnd('/').substringAfterLast("/")
                if (videoCode.isBlank()) return null
                val title = element.select("div.data h3 a").text()
                if (title.isBlank()) return null
                val thumbnailUrl = element.select("div.poster img").attr("data-src")
                    .ifEmpty { element.select("div.poster img").attr("src") }
                HanimeInfo(title = title, coverUrl = thumbnailUrl, videoCode = videoCode, itemType = HanimeInfo.NORMAL)
            } else {
                val linkElement = element.select("div.details > div.title a").first()
                val url = linkElement?.attr("href") ?: return null
                val videoCode = url.trimEnd('/').substringAfterLast("/")
                if (videoCode.isBlank()) return null
                val title = linkElement.text()
                val thumbnailUrl = element.select("div.image div a img").attr("src")
                HanimeInfo(title = title, coverUrl = thumbnailUrl, videoCode = videoCode, itemType = HanimeInfo.NORMAL)
            }
        } catch (e: Exception) { null }
    }

    // ==================== VIDEO DETAIL ====================
    
    fun parseVideoDetail(body: String): VideoLoadingState<HentaiMamaVideoInfo> {
        try {
            val doc = Jsoup.parse(body)
            val thumbnailUrl = doc.selectFirst("div.sheader div.poster img")?.attr("data-src") ?: EMPTY_STRING
            val title = doc.select("#info1 div:nth-child(2) span").text()
            if (title.isBlank()) return VideoLoadingState.Error(IllegalStateException("Title not found"))
            
            val videoCode = doc.location().substringAfterLast("/")
            val genre = doc.select("div.sheader div.data div.sgeneros a").joinToString(", ") { it.text() }
            val description = doc.select("#info1 div.wp-content p").text()
            val author = doc.select("#info1 div:nth-child(3) span div div a").joinToString(", ") { it.text() }
            val statusText = doc.select("#info1 div:nth-child(6) span").text()
            val status = when (statusText) { "Ongoing" -> "Ongoing"; else -> "Completed" }
            
            val episodeElements = doc.select("div.series div.items article")
            Log.d(TAG, "parseVideoDetail: Found ${episodeElements.size} episode elements")
            
            val episodes = episodeElements.mapNotNull { 
                try { 
                    episodeFromElement(it) 
                } catch (e: Exception) { 
                    Log.e(TAG, "Error parsing episode: ${e.message}")
                    null 
                } 
            }
            
            // Parse related videos - try different selectors
            val relatedVideos = mutableListOf<HanimeInfo>()
            // Try related videos section
            val relatedElements = doc.select("div.related-videos div.tvshows, div.series-related div.tvshows, section.related-videos div.tvshows")
            if (relatedElements.isNotEmpty()) {
                relatedVideos.addAll(relatedElements.mapNotNull { popularAnimeFromElement(it) })
            } else {
                // Fallback: use episode elements as related
                relatedVideos.addAll(episodeElements.mapNotNull { popularAnimeFromElement(it) })
            }
            
            return VideoLoadingState.Success(
                HentaiMamaVideoInfo(
                    title = title, 
                    coverUrl = thumbnailUrl, 
                    videoCode = videoCode,
                    description = description, 
                    genre = genre, 
                    author = author, 
                    status = status,
                    videoUrls = emptyList(), 
                    episodes = episodes, 
                    relatedVideos = relatedVideos.distinctBy { it.videoCode },
                )
            )
        } catch (e: Exception) { 
            return VideoLoadingState.Error(e) 
        }
    }

    private fun episodeFromElement(element: Element): HentaiMamaEpisode {
        val url = element.select("div.season_m a").attr("href")
        val title = element.select("div.data h3").text().ifEmpty { 
            element.select("div.season_m a span.c").text() 
        }
        val dateText = element.select("div.data > span").text()
        
        val epNumPattern = Regex("Episode (\\d+\\.?\\d*)")
        val epNumMatch = epNumPattern.find(element.select("div.season_m a span.c").text())
        val episodeNumber = runCatching { epNumMatch?.groups?.get(1)?.value?.toFloat() }.getOrNull() ?: 1F
        
        val dateTimestamp = runCatching {
            SimpleDateFormat("MMM. dd, yyyy", Locale.US).parse(dateText)?.time
        }.getOrNull() ?: 0L
        
        return HentaiMamaEpisode(
            title = title, 
            url = url, 
            date = dateText.takeIf { it.isNotBlank() }, 
            episodeNumber = episodeNumber,
            dateTimestamp = dateTimestamp
        )
    }

    // ==================== VIDEO EXTRACTION - FIXED ====================
    
    /**
     * Extracts video links using the exact same method as the Tachiyomi extension.
     * This is the most reliable approach.
     */
    fun videoListParse(detailPageBody: String, baseUrl: String, apiUrl: String): List<HentaiMamaVideoLink> {
        try {
            val document = Jsoup.parse(detailPageBody)
            
            // Step 1: Get the 'a' parameter - using the exact same selector as the extension
            val postReport = document.selectFirst("#post_report")
            if (postReport == null) {
                Log.e(TAG, "videoListParse: #post_report not found")
                return emptyList()
            }
            
            // Extension uses: document.selectFirst("#post_report input:nth-child(5)")?.attr("value")
            val aParam = postReport.select("input").getOrNull(4)?.attr("value")
                ?: postReport.select("input").firstOrNull { it.attr("value").isNotEmpty() }?.attr("value")
            
            if (aParam.isNullOrEmpty()) {
                Log.e(TAG, "videoListParse: Failed to find 'a' parameter")
                return emptyList()
            }

            Log.d(TAG, "videoListParse: Found aParam='${aParam.take(50)}'")

            // Step 2: Build POST request exactly like the extension
            val body = FormBody.Builder()
                .add("action", "get_player_contents")
                .add("a", aParam)
                .build()
            
            val newHeaders = okhttp3.Headers.headersOf("referer", "$baseUrl/")
            
            val postRequest = okhttp3.Request.Builder()
                .url(apiUrl)
                .post(body)
                .headers(newHeaders)
                .build()
            
            val client = okhttp3.OkHttpClient.Builder().followRedirects(true).build()
            
            // Step 3: Execute POST request
            Log.d(TAG, "videoListParse: POSTing to $apiUrl")
            val postResponse = client.newCall(postRequest).execute()
            val responseString = postResponse.body?.string() ?: ""
            postResponse.close()
            
            Log.d(TAG, "videoListParse: AJAX response length=${responseString.length}")

            // Step 4: Extract all iframe URLs from the response (extension's approach)
            val iframeHtml = Jsoup.parse(responseString).body().select("iframe").toString()
            
            // Extension uses: Regex("https?[\\S][^\"]+")
            val regex = Regex("https?[\\S][^\"]+")
            val allLinks = regex.findAll(iframeHtml).map { it.value }.toList()
            
            Log.d(TAG, "videoListParse: Found ${allLinks.size} iframe URLs")
            
            if (allLinks.isEmpty()) {
                // Try direct video links
                return extractDirectVideoLinks(responseString)
            }

            // Step 5: Parse each iframe for video URLs (extension's approach)
            val videoRegex = Regex("(https:[^\"]+\\.mp4*)")
            val videoList = mutableListOf<HentaiMamaVideoLink>()
            
            for (url in allLinks) {
                try {
                    Log.d(TAG, "videoListParse: Fetching iframe: $url")
                    val iframeReq = okhttp3.Request.Builder()
                        .url(url)
                        .addHeader("Referer", baseUrl)
                        .build()
                    val iframeResp = client.newCall(iframeReq).execute()
                    val pageHtml = iframeResp.body?.string() ?: ""
                    iframeResp.close()
                    
                    val videoLink = videoRegex.find(pageHtml)
                    val videoRes = when {
                        url.contains("newr2") -> "Beta"
                        url.contains("new1") -> "Mirror 1"
                        url.contains("new2") -> "Mirror 2"
                        url.contains("new3") -> "Mirror 3"
                        else -> "Unknown"
                    }
                    
                    if (videoLink != null) {
                        Log.d(TAG, "videoListParse: Found video: $videoRes")
                        videoList.add(HentaiMamaVideoLink(videoRes, videoLink.value))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "videoListParse: Error processing iframe $url: ${e.message}")
                }
            }
            
            // If no videos found in iframes, try direct links
            if (videoList.isEmpty()) {
                return extractDirectVideoLinks(responseString)
            }
            
            Log.d(TAG, "videoListParse: Total videos found: ${videoList.size}")
            return videoList
            
        } catch (e: Exception) {
            Log.e(TAG, "videoListParse error: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Attempt to extract direct video links from the AJAX response
     * when no iframes are found.
     */
    private fun extractDirectVideoLinks(responseString: String): List<HentaiMamaVideoLink> {
        val directLinks = mutableListOf<HentaiMamaVideoLink>()
        
        // Direct MP4
        Regex("""https?://[^\s"']+\.mp4[^\s"']*""").findAll(responseString).forEach { 
            directLinks.add(HentaiMamaVideoLink("Direct MP4", it.value))
        }
        // Direct m3u8
        Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").findAll(responseString).forEach { 
            directLinks.add(HentaiMamaVideoLink("HLS", it.value))
        }
        
        if (directLinks.isNotEmpty()) {
            Log.d(TAG, "extractDirectVideoLinks: Found ${directLinks.size} direct links")
            return directLinks
        }
        
        // Check if response contains JSON with video URLs
        if (responseString.contains("http") && (responseString.contains(".mp4") || responseString.contains(".m3u8"))) {
            Regex("""(https?://[^\s"',}]+\.(mp4|m3u8)[^\s"',}]*)""").findAll(responseString).forEach {
                directLinks.add(HentaiMamaVideoLink("Video", it.value))
            }
            if (directLinks.isNotEmpty()) {
                Log.d(TAG, "extractDirectVideoLinks: Found ${directLinks.size} links in response")
                return directLinks
            }
        }
        
        return emptyList()
    }
}
