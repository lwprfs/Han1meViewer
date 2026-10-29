package com.yenaly.han1meviewer.FapFolder.ui.album

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderFeedPaging
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderAlbumParser
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderAlbumUiState(
    val albumId: String = "",
    val title: String? = null,
    val ownerName: String? = null,
    val ownerUrl: String? = null,
    val photos: List<FapFolderMedia.Image> = emptyList(),
    val isLoading: Boolean = true,
    val headerError: String? = null,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val itemsError: String? = null,
    val streamGet: String? = null,
    val streamFilter: String? = null,
    val streamId: String? = null,
    val streamType: String? = null,
    val streamUid: String? = null,
    val currentOffset: Int = 1,
    val totalCount: Int? = null,
)

class FapFolderAlbumViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderAlbumVM"
        private const val MAX_PAGES = 100
    }

    private val _uiState = MutableStateFlow(FapFolderAlbumUiState())
    val uiState = _uiState.asStateFlow()

    private var headerJob: Job? = null
    private var itemsJob: Job? = null

    private val seenPhotoIds = mutableSetOf<Long?>()
    private val seenUrls = mutableSetOf<String>()

    fun load(albumId: String) {
        val clean = albumId.trim('/')
        if (clean.isBlank()) return
        if (_uiState.value.albumId == clean && _uiState.value.photos.isNotEmpty()) return

        headerJob?.cancel()
        itemsJob?.cancel()
        seenPhotoIds.clear()
        seenUrls.clear()

        _uiState.update {
            it.copy(
                albumId = clean,
                title = null,
                ownerName = null,
                ownerUrl = null,
                photos = emptyList(),
                isLoading = true,
                headerError = null,
                isLoadingMore = false,
                hasMore = false,
                itemsError = null,
                currentOffset = 1,
                totalCount = null,
                streamGet = null,
                streamFilter = null,
                streamId = null,
                streamType = null,
                streamUid = null,
            )
        }

        headerJob = viewModelScope.launch {
            FapFolderNetworkRepo.getAlbumPage(clean).collect { state ->
                when (state) {
                    is com.yenaly.han1meviewer.logic.state.VideoLoadingState.Loading -> Unit
                    is com.yenaly.han1meviewer.logic.state.VideoLoadingState.Success -> {
                        val page = state.info
                        val fresh = filterNewPhotos(page.photos)
                        _uiState.update {
                            it.copy(
                                title = page.title,
                                ownerName = page.ownerName,
                                ownerUrl = page.ownerUrl,
                                photos = fresh,
                                isLoading = false,
                                headerError = null,
                                hasMore = page.hasMore,
                                currentOffset = 2,
                                totalCount = page.photos.size.takeIf { it > 0 }?.plus(
                                    if (page.hasMore) 1 else 0
                                ),
                            )
                        }
                    }
                    is com.yenaly.han1meviewer.logic.state.VideoLoadingState.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                headerError = state.throwable.message
                                    ?: "Failed to load album.",
                            )
                        }
                    }
                    is com.yenaly.han1meviewer.logic.state.VideoLoadingState.NoContent -> {
                        _uiState.update {
                            it.copy(isLoading = false, headerError = "Album not found.")
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        val id = _uiState.value.albumId
        if (id.isBlank()) return
        _uiState.update { it.copy(albumId = "") }
        load(id)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (state.currentOffset >= MAX_PAGES) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }

        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, itemsError = null) }

            val request = buildStreamRequest(state, state.currentOffset)
            FapFolderNetworkRepo.loadMore(request).collect { page ->
                when (page) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val photos = page.info.mapNotNull { item ->
                            when (item) {
                                is com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem.Post ->
                                    item.post.primaryImage
                                else -> null
                            }
                        }
                        val fresh = filterNewPhotos(photos)
                        _uiState.update {
                            it.copy(
                                photos = it.photos + fresh,
                                isLoadingMore = false,
                                hasMore = page.info.isNotEmpty() && fresh.isNotEmpty(),
                                itemsError = null,
                                currentOffset = it.currentOffset + 1,
                            )
                        }
                    }
                    is PageLoadingState.NoMoreData -> _uiState.update {
                        it.copy(isLoadingMore = false, hasMore = false)
                    }
                    is PageLoadingState.Error -> _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            itemsError = page.throwable.message,
                            hasMore = false,
                        )
                    }
                }
            }
        }
    }

    fun retry() {
        if (_uiState.value.photos.isEmpty()) refresh() else loadMore()
    }

    private fun filterNewPhotos(photos: List<FapFolderMedia.Image>): List<FapFolderMedia.Image> {
        val result = mutableListOf<FapFolderMedia.Image>()
        photos.forEach { photo ->
            val url = photo.fullUrl
            val id = photo.photoId
            val urlTaken = seenUrls.contains(url)
            val idTaken = id != null && seenPhotoIds.contains(id)
            if (!urlTaken && !idTaken) {
                seenUrls.add(url)
                id?.let { seenPhotoIds.add(it) }
                result.add(photo)
            }
        }
        return result
    }

    private fun buildStreamRequest(
        state: FapFolderAlbumUiState,
        offset: Int,
    ): FapFolderLoadMoreRequest {
        val get = state.streamGet ?: "album_photos"
        val id = state.streamId ?: state.albumId
        return FapFolderFeedPaging.fromStream(
            get = get,
            filter = state.streamFilter,
            id = id,
            type = state.streamType,
            uid = state.streamUid,
            search = null,
            hashtag = null,
            offset = offset,
            premfeed = FapFolderConstants.PREMFEED_OFF,
            time = FapFolderConstants.TIME_NO_FILTER,
        )
    }

    fun currentPhotoUrls(): List<String> = _uiState.value.photos.map { it.fullUrl }

    fun photoContext(): String {
        val first = _uiState.value.photos.firstOrNull()?.context
        return when (first) {
            null -> "photos"
            else -> first
        }
    }

    fun resolveNextSource(source: String?): String? =
        FapFolderUrlUtils.resolveLightboxSource(source, uploadsBaseOf())

    private fun uploadsBaseOf(): String =
        com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache.uploadsBase()

    override fun onCleared() {
        super.onCleared()
        headerJob?.cancel()
        itemsJob?.cancel()
        Log.d(TAG, "onCleared")
    }
}

internal object FapFolderAlbumSnapshotAdapter {
    fun toMediaImages(
        snapshot: FapFolderAlbumParser.AlbumSnapshot,
    ): List<FapFolderMedia.Image> = snapshot.photos
}
