package com.yenaly.han1meviewer.FapFolder.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderHomeExtras
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSuggestedCommunity
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderTrendingTag
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderHomeUiState(
    val items: List<FapFolderFeedItem> = emptyList(),
    val firstLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
    val lastPageCount: Int = 0,
    val totalPagesLoaded: Int = 0,
    val feedGet: String = FapFolderConstants.FEED_GET_NEWSFEED,
    val feedFilter: String = FapFolderConstants.FILTER_ALL,
    val feedId: String? = null,
    val feedType: String? = null,
    val feedUid: String? = null,
    val currentOffset: Int = 1,
    val trending: List<FapFolderTrendingTag> = emptyList(),
    val suggested: List<FapFolderSuggestedCommunity> = emptyList(),
    val filterSwitching: Boolean = false,
    val extrasLoadedAt: Long = 0L,
    val feedLoadedAt: Long = 0L,
) {
    val posts: List<FapFolderFeedItem.Post>
        get() = items.filterIsInstance<FapFolderFeedItem.Post>()

    val isFresh: Boolean
        get() {
            if (feedLoadedAt == 0L) return false
            return System.currentTimeMillis() - feedLoadedAt < FRESH_LIMIT_MS
        }

    companion object {
        const val FRESH_LIMIT_MS = 10 * 60 * 1000L
    }
}

sealed interface FapFolderHomeEvent {
    data object Refresh : FapFolderHomeEvent
    data object LoadMore : FapFolderHomeEvent
    data class OpenPost(val slugOrId: String) : FapFolderHomeEvent
    data object DismissSessionExpired : FapFolderHomeEvent
    data object Retry : FapFolderHomeEvent
}

class FapFolderHomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderHomeVM"
        private const val MAX_PAGES = 50
    }

    private val _uiState = MutableStateFlow(FapFolderHomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FapFolderHomeEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var filterJob: Job? = null
    private var extrasJob: Job? = null

    private val seenKeys = mutableSetOf<String>()

    fun refreshIfStale() {
        val state = _uiState.value
        if (state.isRefreshing || state.filterSwitching) return
        if (state.items.isEmpty()) {
            refresh()
            return
        }
        if (!state.isFresh) refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        filterJob?.cancel()
        extrasJob?.cancel()
        seenKeys.clear()

        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    firstLoad = it.items.isEmpty(),
                    errorMessage = null,
                    sessionExpired = false,
                    hasMore = true,
                    currentOffset = 1,
                    filterSwitching = false,
                )
            }

            FapFolderNetworkRepo.getHomePageWithExtras().collect { state ->
                when (state) {
                    is WebsiteState.Loading -> {
                        if (_uiState.value.items.isEmpty()) {
                            _uiState.update { it.copy(firstLoad = true) }
                        }
                    }

                    is WebsiteState.Success -> {
                        val bundle = state.info
                        val page = bundle.page
                        val extras = bundle.extras
                        val merged = mergeItems(emptyList(), page.items)
                        val now = System.currentTimeMillis()
                        _uiState.update {
                            it.copy(
                                items = merged,
                                firstLoad = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                hasMore = page.hasMore,
                                errorMessage = null,
                                sessionExpired = false,
                                feedGet = page.feedGet,
                                feedFilter = page.feedFilter ?: FapFolderConstants.FILTER_ALL,
                                feedId = page.feedId,
                                feedType = page.feedType,
                                feedUid = page.feedUid,
                                currentOffset = 1,
                                lastPageCount = page.items.size,
                                totalPagesLoaded = 1,
                                trending = extras.trending,
                                suggested = extras.suggested,
                                feedLoadedAt = now,
                                extrasLoadedAt = now,
                                filterSwitching = false,
                            )
                        }
                    }

                    is WebsiteState.Error -> {
                        _uiState.update {
                            it.copy(
                                firstLoad = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                errorMessage = state.throwable.message
                                    ?: "Failed to load FapFolder home.",
                                sessionExpired = state.throwable.message?.contains(
                                    "session",
                                    ignoreCase = true,
                                ) == true,
                                filterSwitching = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun changeFilter(filter: String) {
        val state = _uiState.value
        if (state.feedFilter == filter || state.filterSwitching) return

        filterJob?.cancel()
        loadMoreJob?.cancel()
        seenKeys.clear()

        _uiState.update {
            it.copy(
                feedFilter = filter,
                items = emptyList(),
                firstLoad = true,
                isLoadingMore = false,
                hasMore = true,
                currentOffset = 1,
                lastPageCount = 0,
                totalPagesLoaded = 0,
                filterSwitching = true,
                errorMessage = null,
            )
        }

        filterJob = viewModelScope.launch {
            FapFolderNetworkRepo.filterPosts(
                filter = filter,
                id = state.feedId,
                offset = 1,
            ).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val merged = mergeItems(emptyList(), pageState.info)
                        val now = System.currentTimeMillis()
                        _uiState.update {
                            it.copy(
                                items = merged,
                                firstLoad = false,
                                filterSwitching = false,
                                hasMore = pageState.info.isNotEmpty(),
                                currentOffset = 2,
                                lastPageCount = pageState.info.size,
                                totalPagesLoaded = 1,
                                feedLoadedAt = now,
                            )
                        }
                    }
                    is PageLoadingState.NoMoreData -> _uiState.update {
                        it.copy(
                            firstLoad = false,
                            filterSwitching = false,
                            hasMore = false,
                        )
                    }
                    is PageLoadingState.Error -> {
                        val msg = pageState.throwable.message
                        _uiState.update {
                            it.copy(
                                firstLoad = false,
                                filterSwitching = false,
                                errorMessage = msg,
                                sessionExpired = msg?.contains("session", true) == true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isRefreshing || state.filterSwitching || !state.hasMore) return
        if (state.totalPagesLoaded >= MAX_PAGES) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }

            val filterGet = when (state.feedFilter) {
                FapFolderConstants.FILTER_PHOTOS -> FapFolderConstants.FEED_GET_POSTS_GROUP
                FapFolderConstants.FILTER_VIDEOS -> FapFolderConstants.FEED_GET_POSTS_GROUP
                else -> state.feedGet
            }

            val request = FapFolderLoadMoreRequest(
                get = filterGet,
                offset = state.currentOffset,
                filter = state.feedFilter,
                id = state.feedId,
                type = state.feedType,
                uid = state.feedUid,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = FapFolderConstants.TIME_NO_FILTER,
            )

            FapFolderNetworkRepo.loadMore(request).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit

                    is PageLoadingState.Success -> {
                        val newItems = pageState.info
                        val merged = mergeItems(_uiState.value.items, newItems)
                        val now = System.currentTimeMillis()
                        _uiState.update {
                            it.copy(
                                items = merged,
                                isLoadingMore = false,
                                hasMore = newItems.isNotEmpty(),
                                errorMessage = null,
                                currentOffset = it.currentOffset + 1,
                                lastPageCount = newItems.size,
                                totalPagesLoaded = it.totalPagesLoaded + 1,
                                feedLoadedAt = now,
                            )
                        }
                    }

                    is PageLoadingState.NoMoreData -> {
                        _uiState.update {
                            it.copy(isLoadingMore = false, hasMore = false)
                        }
                    }

                    is PageLoadingState.Error -> {
                        val msg = pageState.throwable.message
                        val isSession = msg?.contains("session", ignoreCase = true) == true
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                errorMessage = if (isSession) null else msg,
                                sessionExpired = isSession,
                                hasMore = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun dismissSessionExpired() {
        _uiState.update { it.copy(sessionExpired = false) }
    }

    fun retry() {
        if (_uiState.value.items.isEmpty()) refresh() else loadMore()
    }

    fun handleEvent(event: FapFolderHomeEvent) {
        when (event) {
            is FapFolderHomeEvent.Refresh -> refresh()
            is FapFolderHomeEvent.LoadMore -> loadMore()
            is FapFolderHomeEvent.OpenPost -> Unit
            is FapFolderHomeEvent.DismissSessionExpired -> dismissSessionExpired()
            is FapFolderHomeEvent.Retry -> retry()
        }
    }

    fun emitEvent(event: FapFolderHomeEvent) {
        _events.tryEmit(event)
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        filterJob?.cancel()
        extrasJob?.cancel()
        Log.d(TAG, "onCleared: jobs cancelled")
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

    init {
        refresh()
    }
}
