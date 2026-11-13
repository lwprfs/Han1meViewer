package com.yenaly.han1meviewer.FapFolder.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSuggestedCommunity
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderTrendingTag
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.util.applicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FapFolderHomeTab(
    val param: String,
    val pagePath: String,
    val feedGet: String,
) {
    MY_FEED(
        param = FapFolderConstants.HOME_TAB_MY_FEED,
        pagePath = FapFolderConstants.PATH_HOME,
        feedGet = FapFolderConstants.FEED_GET_NEWSFEED,
    ),
    HOT(
        param = FapFolderConstants.HOME_TAB_HOT,
        pagePath = FapFolderConstants.PATH_HOT,
        feedGet = FapFolderConstants.FEED_GET_POPULAR,
    ),
    DISCOVER(
        param = FapFolderConstants.HOME_TAB_DISCOVER,
        pagePath = FapFolderConstants.PATH_DISCOVER,
        feedGet = FapFolderConstants.FEED_GET_DISCOVER,
    );

    companion object {
        fun fromParam(value: String?): FapFolderHomeTab =
            entries.firstOrNull { it.param.equals(value, ignoreCase = true) } ?: MY_FEED
    }
}

enum class FapFolderHotRange(val time: Int) {
    ALL_TIME(0),
    MONTH(1),
    WEEK(2),
}

data class FapFolderHomeUiState(
    val activeTab: FapFolderHomeTab = FapFolderHomeTab.MY_FEED,
    val hotRange: FapFolderHotRange = FapFolderHotRange.ALL_TIME,

    val items: List<FapFolderFeedItem> = emptyList(),
    val firstLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,

    val trending: List<FapFolderTrendingTag> = emptyList(),
    val suggested: List<FapFolderSuggestedCommunity> = emptyList(),

    val feedGet: String = FapFolderConstants.FEED_GET_NEWSFEED,
    val feedFilter: String = FapFolderConstants.FILTER_ALL,
    val feedId: String? = null,
    val feedType: String? = null,
    val feedUid: String? = null,

    val nextTime: Int = 1,
    val offset: Int = 0,

    val totalPagesLoaded: Int = 0,
    val filterSwitching: Boolean = false,
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
        private const val MAX_BATCHES = 100
    }

    private val _uiState = MutableStateFlow(FapFolderHomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FapFolderHomeEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null

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
        seenKeys.clear()

        val tab = _uiState.value.activeTab
        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    firstLoad = it.items.isEmpty(),
                    errorMessage = null,
                    sessionExpired = false,
                    hasMore = true,
                    nextTime = 1,
                    offset = 0,
                    totalPagesLoaded = 0,
                    filterSwitching = false,
                )
            }
            loadFirstPage(tab)
        }
    }

    fun switchTab(tab: FapFolderHomeTab) {
        if (_uiState.value.activeTab == tab) return
        _uiState.update {
            it.copy(
                activeTab = tab,
                items = emptyList(),
                firstLoad = true,
                hasMore = true,
                nextTime = 1,
                offset = 0,
                totalPagesLoaded = 0,
                filterSwitching = true,
                errorMessage = null,
                trending = emptyList(),
                suggested = emptyList(),
                feedLoadedAt = 0L,
            )
        }
        seenKeys.clear()
        refresh()
    }

    fun changeHotRange(range: FapFolderHotRange) {
        val state = _uiState.value
        if (state.activeTab != FapFolderHomeTab.HOT || state.hotRange == range) return
        _uiState.update {
            it.copy(
                hotRange = range,
                items = emptyList(),
                firstLoad = true,
                hasMore = true,
                filterSwitching = true,
                feedLoadedAt = 0L,
            )
        }
        refresh()
    }

    private fun currentTime(state: FapFolderHomeUiState): Int =
        if (state.activeTab == FapFolderHomeTab.HOT && state.hotRange != FapFolderHotRange.ALL_TIME) {
            state.hotRange.time
        } else {
            FapFolderConstants.TIME_NO_FILTER
        }

    fun loadMore() = loadMoreInternal(forceAfterError = false)

    private fun loadMoreInternal(forceAfterError: Boolean) {
        val state = _uiState.value

        val canRetryAfterError = forceAfterError && state.errorMessage != null
        if (state.isLoadingMore || state.isRefreshing || state.filterSwitching || (!state.hasMore && !canRetryAfterError)) return
        if (state.totalPagesLoaded >= MAX_BATCHES) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }

            val tab = state.activeTab
            val request = FapFolderLoadMoreRequest(
                get = state.feedGet.takeIf { it.isNotBlank() } ?: tab.feedGet,
                offset = state.nextTime,
                filter = state.feedFilter,
                id = state.feedId,
                type = state.feedType,
                uid = state.feedUid,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = currentTime(state),
            )

            FapFolderNetworkRepo.loadMore(request).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val newItems = pageState.info
                        val currentItems = _uiState.value.items
                        val merged = mergeItems(currentItems, newItems)
                        val added = merged.size - currentItems.size
                        _uiState.update {
                            it.copy(
                                items = merged,
                                isLoadingMore = false,
                                hasMore = newItems.isNotEmpty() && added > 0,
                                errorMessage = null,
                                nextTime = it.nextTime + 1,
                                totalPagesLoaded = it.totalPagesLoaded + 1,
                                feedLoadedAt = System.currentTimeMillis(),
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
        if (_uiState.value.items.isEmpty()) refresh() else loadMoreInternal(forceAfterError = true)
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
        Log.d(TAG, "onCleared: jobs cancelled")
    }

    private suspend fun loadFirstPage(tab: FapFolderHomeTab) {
        val range = _uiState.value.hotRange
        if (tab == FapFolderHomeTab.HOT && range != FapFolderHotRange.ALL_TIME) {
            loadHotRangeFirstPage(range)
            return
        }
        val flow = when (tab) {
            FapFolderHomeTab.MY_FEED -> FapFolderNetworkRepo.getHomePageWithExtras()
            FapFolderHomeTab.HOT -> FapFolderNetworkRepo.getHotPageWithExtras()
            FapFolderHomeTab.DISCOVER -> FapFolderNetworkRepo.getDiscoverPageWithExtras()
        }

        flow.collect { state ->
            when (state) {
                is WebsiteState.Loading -> {
                    if (_uiState.value.items.isEmpty()) {
                        _uiState.update { it.copy(firstLoad = true) }
                    }
                }
                is WebsiteState.Success -> {
                    val bundle = state.info
                    val page = bundle.page
                    val merged = mergeItems(emptyList(), page.items)
                    val now = System.currentTimeMillis()
                    _uiState.update {
                        it.copy(
                            items = merged,
                            firstLoad = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            hasMore = page.hasMore && page.items.isNotEmpty(),
                            errorMessage = null,
                            sessionExpired = false,
                            feedGet = page.feedGet,
                            feedFilter = page.feedFilter ?: FapFolderConstants.FILTER_ALL,
                            feedId = page.feedId,
                            feedType = page.feedType,
                            feedUid = page.feedUid,
                            nextTime = 1,
                            offset = 0,
                            totalPagesLoaded = 1,
                            trending = bundle.extras.trending,
                            suggested = bundle.extras.suggested,
                            feedLoadedAt = now,
                            filterSwitching = false,
                        )
                    }
                }
                is WebsiteState.Error -> {
                    val msg = state.throwable.message
                    val isSession = msg?.contains("session", ignoreCase = true) == true
                    _uiState.update {
                        it.copy(
                            firstLoad = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = if (isSession) null
                            else (msg ?: applicationContext.getString(R.string.fap_failed_to_load_fapfolder)),
                            sessionExpired = isSession,
                            filterSwitching = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadHotRangeFirstPage(range: FapFolderHotRange) {
        val request = FapFolderLoadMoreRequest(
            get = FapFolderConstants.FEED_GET_POPULAR,
            offset = 0,
            filter = FapFolderConstants.FILTER_ALL,
            premfeed = FapFolderConstants.PREMFEED_OFF,
            time = range.time,
        )
        FapFolderNetworkRepo.loadMore(request).collect { pageState ->
            when (pageState) {
                is PageLoadingState.Loading -> Unit
                is PageLoadingState.Success -> {
                    val merged = mergeItems(emptyList(), pageState.info)
                    _uiState.update {
                        it.copy(
                            items = merged,
                            firstLoad = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            hasMore = merged.isNotEmpty(),
                            errorMessage = null,
                            sessionExpired = false,
                            feedGet = FapFolderConstants.FEED_GET_POPULAR,
                            feedFilter = FapFolderConstants.FILTER_ALL,
                            feedId = null,
                            feedType = null,
                            feedUid = null,
                            nextTime = 1,
                            offset = 0,
                            totalPagesLoaded = 1,
                            feedLoadedAt = System.currentTimeMillis(),
                            filterSwitching = false,
                        )
                    }
                }
                is PageLoadingState.NoMoreData -> _uiState.update {
                    it.copy(
                        items = emptyList(),
                        firstLoad = false,
                        isRefreshing = false,
                        hasMore = false,
                        filterSwitching = false,
                    )
                }
                is PageLoadingState.Error -> {
                    val msg = pageState.throwable.message
                    val isSession = msg?.contains("session", ignoreCase = true) == true
                    _uiState.update {
                        it.copy(
                            firstLoad = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = if (isSession) null
                            else (msg ?: applicationContext.getString(R.string.fap_failed_to_load_fapfolder)),
                            sessionExpired = isSession,
                            filterSwitching = false,
                        )
                    }
                }
            }
        }
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
