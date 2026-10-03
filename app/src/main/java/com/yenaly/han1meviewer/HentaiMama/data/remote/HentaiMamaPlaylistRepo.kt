package com.yenaly.han1meviewer.HentaiMama.data.remote

import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistsIndexPage
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaPlaylistParser
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object HentaiMamaPlaylistRepo {

    private const val TAG = "HentaiMamaPlaylistRepo"

    fun playlistsUrl(page: Int = 1, sort: String? = null): String {
        val base = "${HentaiMamaNetwork.baseUrl.trimEnd('/')}/playlists/"
        val sortSeg = when (sort?.lowercase()) {
            null, "", "views" -> ""
            "likes" -> "likes/"
            "recent" -> "recent/"
            else -> "$sort/"
        }
        return if (page <= 1) "$base$sortSeg" else "$base${sortSeg}page/$page/"
    }

    fun playlistDetailUrl(id: String, page: Int = 1): String {
        val base = "${HentaiMamaNetwork.baseUrl.trimEnd('/')}/playlist/$id/"
        return if (page <= 1) base else "${base}page/$page/"
    }

    fun getPlaylistsIndex(page: Int = 1, sort: String? = null) = flow {
        emit(PageLoadingState.Loading)
        try {
            val url = playlistsUrl(page, sort)
            val response = HentaiMamaNetwork.service.getVideoDetail(url)
            if (!response.isSuccessful) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            val pageData: PlaylistsIndexPage = HentaiMamaPlaylistParser.parseIndex(body, url)
            if (pageData.cards.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(PageLoadingState.Success(pageData))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistsIndex failed page=$page sort=$sort", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getPlaylistDetail(id: String, page: Int = 1) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val url = playlistDetailUrl(id, page)
            val response = HentaiMamaNetwork.service.getVideoDetail(url)
            if (!response.isSuccessful) {
                emit(
                    if (response.code() == 404) VideoLoadingState.NoContent
                    else VideoLoadingState.Error(IllegalStateException("HTTP ${response.code()}"))
                )
                return@flow
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                emit(VideoLoadingState.NoContent)
                return@flow
            }
            val pageData: PlaylistDetailPage = HentaiMamaPlaylistParser.parseDetail(body, url)
            emit(VideoLoadingState.Success(pageData))
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistDetail failed id=$id page=$page", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}