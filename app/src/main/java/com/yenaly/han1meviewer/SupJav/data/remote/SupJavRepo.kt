package com.yenaly.han1meviewer.SupJav.data.remote

import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.SupJav.common.SupJavSettings
import com.yenaly.han1meviewer.SupJav.data.model.SupJavDetail
import com.yenaly.han1meviewer.SupJav.data.model.SupJavListPage
import com.yenaly.han1meviewer.SupJav.data.parser.SupJavParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object SupJavRepo {

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val response = SupJavNetwork.service.get(url)
        if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
        response.body()?.string().orEmpty()
    }

    suspend fun category(path: String, page: Int): Result<SupJavListPage> = runCatching {
        withContext(Dispatchers.Default) {
            SupJavParser.parseListPage(
                fetchHtml(SupJavConstants.pagedUrl(path, page, SupJavSettings.language))
            )
        }
    }

    suspend fun search(query: String, page: Int): Result<SupJavListPage> = runCatching {
        withContext(Dispatchers.Default) {
            SupJavParser.parseListPage(
                fetchHtml(SupJavConstants.searchUrl(query, page, SupJavSettings.language))
            )
        }
    }

    suspend fun byUrl(url: String): Result<SupJavListPage> = runCatching {
        withContext(Dispatchers.Default) { SupJavParser.parseListPage(fetchHtml(url)) }
    }

    suspend fun detail(code: String): Result<SupJavDetail> = runCatching {
        withContext(Dispatchers.Default) {
            val html = fetchHtml(SupJavConstants.videoUrl(code))
            SupJavParser.parseDetail(html, code)
                ?: throw IOException("Could not read video page")
        }
    }
}
