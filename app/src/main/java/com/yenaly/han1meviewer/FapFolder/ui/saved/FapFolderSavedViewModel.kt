package com.yenaly.han1meviewer.FapFolder.ui.saved

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderFeedPaging
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderSavedUiState(
    val items: List<FapFolderFeedItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
    val feedFilter: String = FapFolderConstants.FILTER_ALL,
    val nextOffset: Int = 1,
    val totalPagesLoaded: Int = 0,
)

class FapFolderSavedViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderSavedVM"
        private const val MAX_PAGES = 100
    }

    private val _uiState = MutableStateFlow(FapFolderSavedUiState())
    val uiState = _uiState.asStateFlow()

    private var firstJob: Job? = null
    private var moreJob: Job? = null

    private val seenKeys = mutableSetOf<String>()

    init {
        refresh()
    }

    fun refresh() {
        firstJob?.cancel()
        moreJob?.cancel()
        seenKeys.clear()
        _uiState.update {
            it.copy(
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMore = true,
                errorMessage = null,
                sessionExpired = false,
                nextOffset = 1,
                totalPagesLoaded = 0,
            )
        }
        firstJob = viewModelScope.launch {
            FapFolderNetworkRepo.getSavedPage().collect { state ->
                when (state) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> {
                        val page = state.info
                        val merged = mergeItems(emptyList(), page.items)
                        _uiState.update {
                            it.copy(
                                items = merged,
                                isLoading = false,
                                hasMore = page.hasMore && page.items.isNotEmpty(),
                                errorMessage = null,
                                sessionExpired = false,
                                feedFilter = page.feedFilter
                                    ?: FapFolderConstants.FILTER_ALL,
                                nextOffset = 1,
                                totalPagesLoaded = 1,
                            )
                        }
                    }
                    is WebsiteState.Error -> {
                        val msg = state.throwable.message
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = msg ?: "Failed to load saved posts.",
                                sessionExpired =
                                    msg?.contains("session", true) == true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (state.totalPagesLoaded >= MAX_PAGES) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }

            val request: FapFolderLoadMoreRequest = FapFolderFeedPaging.saved(
                offset = state.nextOffset,
                filter = state.feedFilter,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = FapFolderConstants.TIME_NO_FILTER,
            )

            FapFolderNetworkRepo.loadMore(request).collect { page ->
                when (page) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val current = _uiState.value.items
                        val merged = mergeItems(current, page.info)
                        val added = merged.size - current.size
                        _uiState.update {
                            it.copy(
                                items = merged,
                                isLoadingMore = false,
                                hasMore = page.info.isNotEmpty() && added > 0,
                                errorMessage = null,
                                nextOffset = it.nextOffset + 1,
                                totalPagesLoaded = it.totalPagesLoaded + 1,
                            )
                        }
                    }
                    is PageLoadingState.NoMoreData -> _uiState.update {
                        it.copy(isLoadingMore = false, hasMore = false)
                    }
                    is PageLoadingState.Error -> {
                        val msg = page.throwable.message
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                errorMessage = msg,
                                hasMore = false,
                                sessionExpired =
                                    msg?.contains("session", true) == true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun retry() {
        if (_uiState.value.items.isEmpty()) refresh() else loadMore()
    }

    private fun mergeItems(
        old: List<FapFolderFeedItem>,
        incoming: List<FapFolderFeedItem>,
    ): List<FapFolderFeedItem> {
        if (incoming.isEmpty()) return old
        val merged = ArrayList<FapFolderFeedItem>(old.size + incoming.size)
        merged.addAll(old)
        incoming.forEach { item ->
            if (seenKeys.add(item.stableKey)) merged.add(item)
        }
        return merged
    }

    override fun onCleared() {
        super.onCleared()
        firstJob?.cancel()
        moreJob?.cancel()
        Log.d(TAG, "onCleared")
    }
}
