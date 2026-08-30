package com.yenaly.han1meviewer.HentaiMama

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

object HentaiMamaNetworkRepo {

    private const val TAG = "HentaiMamaRepo"
    
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    fun getHomePage() = flow {
        emit(WebsiteState.Loading)
        try {
            val response = HentaiMamaNetwork.service.getPopularVideos(page = 1)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                emit(HentaiMamaParser.homePage(body))
            } else {
                emit(WebsiteState.Error(IllegalStateException("Failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getLatestVideos(page: Int) = flow {
        emit(PageLoadingState.Loading)
        try {
            val response = HentaiMamaNetwork.service.getLatestVideos(page)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                emit(HentaiMamaParser.parseVideoList(body))
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Search for videos. If query is empty, automatically uses filter mode.
     */
    fun searchVideos(page: Int, query: String) = flow {
        emit(PageLoadingState.Loading)
        try {
            // If query is empty, use filter mode
            val response = if (query.isBlank()) {
                HentaiMamaNetwork.service.getFilteredVideos(
                    page = page,
                    submit = "Submit",
                    filter = null,
                    genres = null,
                    years = null,
                    studios = null
                )
            } else {
                HentaiMamaNetwork.service.searchVideos(page, query)
            }
            
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                // Pass isFilterSearch = true when query is empty
                emit(HentaiMamaParser.parseSearchResults(body, isFilterSearch = query.isBlank()))
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Search failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Advanced filter search with genre, producer, and order parameters.
     */
    fun filterVideos(page: Int, genre: String?, producer: String?, order: String?) = flow {
        emit(PageLoadingState.Loading)
        try {
            // Build parameters like the Tachiyomi extension
            var parameters = "submit=Submit"
            if (!order.isNullOrEmpty()) parameters += "&filter=$order"
            if (!genre.isNullOrEmpty()) parameters += "&genres_filter%5B%5D=$genre"
            if (!producer.isNullOrEmpty()) parameters += "&studios_filter%5B%5D=$producer"
            
            val url = "${HentaiMamaConstants.BASE_URL}/advance-search/page/$page/?$parameters"
            Log.d(TAG, "filterVideos URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Referer", HentaiMamaConstants.BASE_URL)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: EMPTY_STRING
                // Filter search is always true
                emit(HentaiMamaParser.parseSearchResults(body, isFilterSearch = true))
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Filter failed: ${response.code}")))
            }
        } catch (e: Exception) {
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get video detail ONLY (title, description, episodes, etc.)
     * Does NOT extract video links - that's done on Play button tap
     */
    fun getVideoDetail(path: String) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val response = HentaiMamaNetwork.service.getVideoDetail(path)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                Log.d(TAG, "getVideoDetail: response length=${body.length}")
                
                val detailState = HentaiMamaParser.parseVideoDetail(body)
                emit(detailState)
            } else {
                emit(VideoLoadingState.Error(IllegalStateException("Failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract video links from video detail page using extension's videoListParse method.
     * Called when user taps Play button.
     */
    suspend fun extractVideoLinks(path: String): List<HentaiMamaVideoLink> {
        try {
            val response = HentaiMamaNetwork.service.getVideoDetail(path)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: ""
                Log.d(TAG, "extractVideoLinks: detail page length=${body.length}")
                
                val videoLinks = HentaiMamaParser.videoListParse(
                    body,
                    HentaiMamaConstants.BASE_URL,
                    HentaiMamaConstants.API_URL
                )
                
                Log.d(TAG, "extractVideoLinks: Found ${videoLinks.size} video links")
                return videoLinks
            } else {
                Log.e(TAG, "extractVideoLinks: Failed with code ${response.code()}")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractVideoLinks error: ${e.message}", e)
            return emptyList()
        }
    }
}
