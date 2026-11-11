package com.yenaly.han1meviewer.XHamster.data.remote

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterStream
import com.yenaly.han1meviewer.XHamster.data.parser.XHamsterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object XHamsterRepo {

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val response = XHamsterNetwork.service.get(url)
        if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
        response.body()?.string() ?: throw IOException("Empty response")
    }

    suspend fun list(url: String): Result<XHamsterListPage> = runCatching {
        XHamsterParser.parseListPage(fetchHtml(url))
    }

    suspend fun playlists(): Result<List<XHamsterLink>> = runCatching {
        XHamsterParser.parsePlaylists(fetchHtml(XHamsterConstants.listUrl("/my/favorites/videos")))
    }

    suspend fun search(query: String): Result<XHamsterListPage> =
        list(XHamsterConstants.searchUrl(query))

    suspend fun detail(code: String): Result<XHamsterDetail> = runCatching {
        val html = fetchHtml(XHamsterConstants.videoUrl(code))
        XHamsterParser.parseDetail(html, code) ?: throw IOException("Unable to read video page")
    }

    suspend fun resolveStreams(detail: XHamsterDetail): List<XHamsterStream> =
        XHamsterStreamResolver.resolve(detail)
}
