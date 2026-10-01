package com.yenaly.han1meviewer.HentaiMama

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object HentaiMamaNetworkRepo {

    private const val TAG = "HentaiMamaRepo"

    fun getHomePage() = flow {
        emit(WebsiteState.Loading)
        try {
            val popularResp = HentaiMamaNetwork.service.getPopularVideos()
            val popularBody = if (popularResp.isSuccessful) {
                popularResp.body()?.string().orEmpty()
            } else ""

            val latestResp = HentaiMamaNetwork.service.getLatestVideos()
            val latestBody = if (latestResp.isSuccessful) {
                latestResp.body()?.string().orEmpty()
            } else ""

            val popularState = HentaiMamaParser.parseVideoList(popularBody)
            val latestState = HentaiMamaParser.parseVideoList(latestBody)

            val popular = (popularState as? PageLoadingState.Success)?.info ?: emptyList()
            val latest = (latestState as? PageLoadingState.Success)?.info ?: popular

            if (popular.isEmpty() && latest.isEmpty()) {
                emit(WebsiteState.Error(IllegalStateException("No videos found")))
            } else {
                emit(
                    WebsiteState.Success(
                        HentaiMamaHomePage(
                            popularVideos = popular,
                            latestVideos = latest,
                        )
                    )
                )
            }
        } catch (e: Exception) {
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getCategoryVideos(category: HentaiMamaHomeCategory): List<HanimeInfo> {
        val baseUrl = HentaiMamaNetwork.baseUrl

        val relative = category.genrePath.trimStart('/')
        val fullUrl = if (relative.startsWith("http")) relative else "$baseUrl/$relative"

        val finalUrl = if (!category.sort.isNullOrBlank() && !fullUrl.contains("filter=", ignoreCase = true)) {
            val sep = if ('?' in fullUrl) "&" else "?"
            "$fullUrl${sep}filter=${category.sort}"
        } else {
            fullUrl
        }

        Log.d(TAG, "getCategoryVideos: key=${category.key} url=$finalUrl")

        return try {
            val response = HentaiMamaNetwork.service.getVideoDetail(finalUrl)
            if (!response.isSuccessful) {
                Log.w(TAG, "getCategoryVideos HTTP ${response.code()} for ${category.key}")
                return emptyList()
            }

            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) return emptyList()

            val state = HentaiMamaParser.parseSearchResults(body, isFilterSearch = true)
            when (state) {
                is PageLoadingState.Success -> state.info
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCategoryVideos failed for '${category.key}'", e)
            emptyList()
        }
    }

    fun getLatestVideos(page: Int) = flow {
        emit(PageLoadingState.Loading)
        try {
            val response = if (page <= 1) {
                HentaiMamaNetwork.service.getLatestVideos()
            } else {
                HentaiMamaNetwork.service.getLatestVideosPaged(page)
            }
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

    fun searchVideos(page: Int, query: String) = flow {
        emit(PageLoadingState.Loading)
        try {
            val response = HentaiMamaNetwork.service.searchVideos(page, query)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                emit(HentaiMamaParser.parseSearchResults(body, isFilterSearch = false))
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Search failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun filterVideos(
        page: Int,
        genre: String?,
        producer: String?,
        year: String? = null,
        order: String? = null,
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val genres = genre?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            val studios = producer?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            val years = year?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            val filterOrder = order?.takeIf { it.isNotBlank() }

            Log.d(
                TAG,
                "filterVideos: page=$page genre=$genre producer=$producer year=$year order=$order"
            )

            val response = if (page <= 1) {
                HentaiMamaNetwork.service.getFilteredVideos(
                    filter = filterOrder,
                    genres = genres,
                    years = years,
                    studios = studios,
                )
            } else {
                HentaiMamaNetwork.service.getFilteredVideosPaged(
                    page = page,
                    filter = filterOrder,
                    genres = genres,
                    years = years,
                    studios = studios,
                )
            }

            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                emit(HentaiMamaParser.parseSearchResults(body, isFilterSearch = true))
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Filter failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "filterVideos error", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getVideoDetail(url: String) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
            val response = HentaiMamaNetwork.service.getVideoDetail(full)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                Log.d(TAG, "getVideoDetail: len=${body.length} url=$full")
                emit(HentaiMamaParser.parseVideoDetail(body, full))
            } else {
                emit(VideoLoadingState.Error(IllegalStateException("Failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun extractVideoLinks(
        detailPageBody: String,
        optionNumber: Int,
    ): List<HentaiMamaVideoLink> {
        return HentaiMamaParser.videoListParse(
            detailPageBody = detailPageBody,
            baseUrl = HentaiMamaNetwork.baseUrl,
            apiUrl = HentaiMamaNetwork.apiUrl,
            optionNumber = optionNumber,
        )
    }

    suspend fun extractHosterTabs(detailPageBody: String): List<Pair<String, Int>> {
        return HentaiMamaParser.hosterTabs(detailPageBody)
    }
}
