package com.yenaly.han1meviewer.RouVideo.data.remote

import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.Sort
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.SeriesFilter
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.SeriesSort
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoDetail
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoListPage
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoSearchLanding
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoTaxonomy
import com.yenaly.han1meviewer.RouVideo.data.parser.RouVideoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object RouVideoRepo {

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val response = RouVideoNetwork.service.get(url)
        if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
        response.body()?.string().orEmpty()
    }

    suspend fun library(sort: Sort, page: Int): Result<RouVideoListPage> = runCatching {
        val html = fetchHtml(RouVideoConstants.libraryUrl(sort, page))
        withContext(Dispatchers.Default) { RouVideoParser.parseVideoList(html) }
    }

    suspend fun tag(tag: String, sort: Sort, page: Int): Result<RouVideoListPage> = runCatching {
        val html = fetchHtml(RouVideoConstants.tagUrl(tag, sort, page))
        withContext(Dispatchers.Default) { RouVideoParser.parseVideoList(html) }
    }

    suspend fun search(
        query: String,
        tag: String,
        sort: Sort,
        page: Int,
        series: Boolean,
    ): Result<RouVideoListPage> = runCatching {
        val html = fetchHtml(RouVideoConstants.searchUrl(query, tag, sort, page, series))
        withContext(Dispatchers.Default) { RouVideoParser.parseSearch(html, series) }
    }

    suspend fun searchLanding(): Result<RouVideoSearchLanding> = runCatching {
        val html = fetchHtml(RouVideoConstants.searchUrl("", "", Sort.RELEVANCE, 1))
        withContext(Dispatchers.Default) { RouVideoParser.parseSearchLanding(html) }
    }

    suspend fun series(
        tag: String,
        filter: SeriesFilter,
        sort: SeriesSort,
        page: Int,
    ): Result<RouVideoListPage> = runCatching {
        val html = fetchHtml(RouVideoConstants.seriesUrl(tag, filter, sort, page))
        withContext(Dispatchers.Default) { RouVideoParser.parseSeriesList(html) }
    }

    suspend fun categories(): Result<RouVideoTaxonomy> = runCatching {
        val html = fetchHtml(RouVideoConstants.categoriesUrl())
        withContext(Dispatchers.Default) { RouVideoParser.parseTaxonomy(html) }
    }

    suspend fun detail(id: String): Result<RouVideoDetail> = runCatching {
        val html = fetchHtml(RouVideoConstants.videoPageUrl(id))
        withContext(Dispatchers.Default) {
            RouVideoParser.parseDetail(html, id) ?: throw IOException("Could not read video page")
        }
    }

    suspend fun reportPlay(id: String, tags: List<String>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = tags.joinToString(",", prefix = "{\"tags\":[", postfix = "]}") {
                    "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                }
                val request = Request.Builder()
                    .url(RouVideoConstants.playUrl(id))
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                RouVideoNetwork.sharedOkHttpClient.newCall(request).execute().close()
            }
        }
    }
}
