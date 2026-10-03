package com.yenaly.han1meviewer.HentaiMama.ui.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistCard
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistHero
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistSortOption
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaPlaylistRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaylistsIndexUiState(
    val items: List<PlaylistCard> = emptyList(),
    val sortOptions: List<PlaylistSortOption> = emptyList(),
    val activeSort: String? = null,
    val sortKey: String? = null,
    val page: Int = 1,
    val totalPages: Int = 1,
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: Throwable? = null,
)

data class PlaylistDetailUiState(
    val hero: PlaylistHero? = null,
    val episodes: List<PlaylistEpisode> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: Throwable? = null,
)

class HentaiMamaPlaylistViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "HentaiMamaPlaylistVM"
    }

    private val _indexState = MutableStateFlow(PlaylistsIndexUiState())
    val indexState = _indexState.asStateFlow()

    private val _detailState = MutableStateFlow(PlaylistDetailUiState())
    val detailState = _detailState.asStateFlow()

    private var indexJob: Job? = null
    private var detailJob: Job? = null

    fun loadIndex(sortKey: String? = null) {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            _indexState.update {
                it.copy(
                    sortKey = sortKey ?: it.sortKey,
                    page = 1,
                    items = emptyList(),
                    isInitialLoading = true,
                    isLoadingMore = false,
                    hasMore = true,
                    error = null,
                )
            }
            collectIndexPage(1, replace = true)
        }
    }

    fun loadMoreIndex() {
        val s = _indexState.value
        if (s.isLoadingMore || s.isInitialLoading || !s.hasMore) return
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            _indexState.update { it.copy(isLoadingMore = true, error = null) }
            collectIndexPage(s.page + 1, replace = false)
        }
    }

    private suspend fun collectIndexPage(page: Int, replace: Boolean) {
        val sortKey = _indexState.value.sortKey
        try {
            HentaiMamaPlaylistRepo.getPlaylistsIndex(page, sortKey).collect { state ->
                if (!viewModelScope.isActive) return@collect
                when (state) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Error -> {
                        _indexState.update {
                            it.copy(
                                isInitialLoading = false,
                                isLoadingMore = false,
                                error = state.throwable,
                            )
                        }
                    }
                    is PageLoadingState.NoMoreData -> {
                        _indexState.update {
                            it.copy(
                                isInitialLoading = false,
                                isLoadingMore = false,
                                hasMore = false,
                            )
                        }
                    }
                    is PageLoadingState.Success -> {
                        val data = state.info
                        val newItems = data.cards
                        _indexState.update { prev ->
                            val merged = if (replace) newItems
                            else (prev.items + newItems).distinctBy { it.id }
                            prev.copy(
                                items = merged,
                                sortOptions = if (data.header.sortOptions.isNotEmpty())
                                    data.header.sortOptions else prev.sortOptions,
                                activeSort = data.header.activeSort ?: prev.activeSort,
                                page = page,
                                totalPages = data.paginator?.total ?: prev.totalPages,
                                isInitialLoading = false,
                                isLoadingMore = false,
                                hasMore = data.paginator?.nextUrl?.isNotBlank() == true ||
                                        (data.paginator != null && data.paginator.current < data.paginator.total),
                                error = null,
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadIndex failed", e)
            _indexState.update {
                it.copy(
                    isInitialLoading = false,
                    isLoadingMore = false,
                    error = e,
                )
            }
        }
    }

    fun loadDetail(id: String) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detailState.value = PlaylistDetailUiState(isInitialLoading = true)
            collectDetailPage(id, 1, replace = true)
        }
    }

    fun loadMoreDetail(id: String) {
        val s = _detailState.value
        if (s.isLoadingMore || s.isInitialLoading || !s.hasMore) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detailState.update { it.copy(isLoadingMore = true, error = null) }
            collectDetailPage(id, s.page + 1, replace = false)
        }
    }

    private suspend fun collectDetailPage(id: String, page: Int, replace: Boolean) {
        try {
            HentaiMamaPlaylistRepo.getPlaylistDetail(id, page).collect { state ->
                if (!viewModelScope.isActive) return@collect
                when (state) {
                    is VideoLoadingState.Loading -> Unit
                    is VideoLoadingState.NoContent -> {
                        _detailState.update {
                            it.copy(
                                isInitialLoading = false,
                                isLoadingMore = false,
                                hasMore = false,
                            )
                        }
                    }
                    is VideoLoadingState.Error -> {
                        _detailState.update {
                            it.copy(
                                isInitialLoading = false,
                                isLoadingMore = false,
                                error = state.throwable,
                            )
                        }
                    }
                    is VideoLoadingState.Success -> {
                        val data = state.info
                        _detailState.update { prev ->
                            val mergedEpisodes = if (replace) data.episodes
                            else (prev.episodes + data.episodes)
                                .distinctBy { it.postId }
                                .sortedBy { it.position }
                            prev.copy(
                                hero = data.hero,
                                episodes = mergedEpisodes,
                                page = page,
                                totalPages = data.paginator?.total ?: prev.totalPages,
                                isInitialLoading = false,
                                isLoadingMore = false,
                                hasMore = data.paginator?.nextUrl?.isNotBlank() == true ||
                                        (data.paginator != null && data.paginator.current < data.paginator.total),
                                error = null,
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadDetail failed", e)
            _detailState.update {
                it.copy(
                    isInitialLoading = false,
                    isLoadingMore = false,
                    error = e,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        indexJob?.cancel()
        detailJob?.cancel()
    }
}
