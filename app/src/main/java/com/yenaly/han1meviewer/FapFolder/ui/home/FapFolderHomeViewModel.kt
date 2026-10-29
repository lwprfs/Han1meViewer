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
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FapFolderHomeTab(val param: String) {
    MY_FEED(FapFolderConstants.HOME_TAB_MY_FEED),
    HOT(FapFolderConstants.HOME_TAB_HOT),
    DISCOVER(FapFolderConstants.HOME_TAB_DISCOVER);

    companion object {
        fun fromParam(value: String?): FapFolderHomeTab =
            entries.firstOrNull { it.param.equals(value, ignoreCase = true) } ?: MY_FEED
    }
}

enum class FapFolderHotRange(val param: Int, val label: String) {
    ALL_TIME(FapFolderConstants.RANGE_ALL_TIME, "All Time"),
    MONTH(FapFolderConstants.RANGE_MONTH, "This Month"),
    WEEK(FapFolderConstants.RANGE_WEEK, "This Week");
}

data class FapFolderHomeUiState(
    val activeTab: FapFolderHomeTab = FapFolderHomeTab.MY_FEED,
    val hotRange: FapFolderHotRange = FapFolderHotRange.ALL_TIME,

    // Generic feed items for the current tab (My Feed / Hot / Discover latest)
    val items: List<FapFolderFeedItem> = emptyList(),
    val firstLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,

    // Discover extras
    val trending: List<FapFolderTrendingTag> = emptyList(),
    val suggested: List<FapFolderSuggestedCommunity> = emptyList(),

    // Paging + stream state
    val feedGet: String = FapFolderConstants.FEED_GET_NEWSFEED,
    val feedFilter: String = FapFolderConstants.FILTER_ALL,
    val feedId: String? = null,
    val feedType: String? = null,
    val feedUid: String? = null,
    val currentOffset: Int = 1,
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
        private const val MAX_PAGES = 50
    }

    private val _uiState = MutableStateFlow(FapFolderHomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FapFolderHomeEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var tabJob: Job? = null

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
        tabJob?.cancel()
        seenKeys.clear()

        val tab = _uiState.value.activeTab
        val range = _uiState.value.hotRange

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
            when (tab) {
                FapFolderHomeTab.MY_FEED -> loadMyFeed(range)
                FapFolderHomeTab.HOT -> loadHot(range)
                FapFolderHomeTab.DISCOVER -> loadDiscover(range)
            }
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
                currentOffset = 1,
                totalPagesLoaded = 0,
                filterSwitching = true,
                errorMessage = null,
            )
        }
        seenKeys.clear()
        refresh()
    }

    fun changeHotRange(range: FapFolderHotRange) {
        if (_uiState.value.hotRange == range) return
        _uiState.update {
            it.copy(
                hotRange = range,
                items = emptyList(),
                firstLoad = true,
                hasMore = true,
                currentOffset = 1,
                totalPagesLoaded = 0,
                filterSwitching = true,
                errorMessage = null,
            )
        }
        seenKeys.clear()
        refresh()
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

            val tab = state.activeTab
            val request = when (tab) {
                FapFolderHomeTab.MY_FEED -> FapFolderLoadMoreRequest(
                    get = FapFolderConstants.FEED_GET_NEWSFEED,
                    offset = state.currentOffset,
                    filter = FapFolderConstants.FILTER_ALL,
                    id = state.feedId,
                    type = state.feedType,
                    uid = state.feedUid,
                    premfeed = FapFolderConstants.PREMFEED_OFF,
                    time = FapFolderConstants.TIME_NO_FILTER,
                )
                FapFolderHomeTab.HOT -> FapFolderLoadMoreRequest(
                    get = FapFolderConstants.FEED_GET_NEWSFEED,
                    offset = state.currentOffset,
                    filter = FapFolderConstants.FILTER_ALL,
                    id = state.feedId,
                    type = state.feedType,
                    uid = state.feedUid,
                    premfeed = FapFolderConstants.PREMFEED_ON,
                    time = state.hotRange.param,
                )
                FapFolderHomeTab.DISCOVER -> FapFolderLoadMoreRequest(
                    get = state.feedGet,
                    offset = state.currentOffset,
                    filter = state.feedFilter,
                    id = state.feedId,
                    type = state.feedType,
                    uid = state.feedUid,
                    premfeed = FapFolderConstants.PREMFEED_OFF,
                    time = FapFolderConstants.TIME_NO_FILTER,
                )
            }

            FapFolderNetworkRepo.loadMore(request).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val newItems = pageState.info
                        val merged = mergeItems(_uiState.value.items, newItems)
                        _uiState.update {
                            it.copy(
                                items = merged,
                                isLoadingMore = false,
                                hasMore = newItems.isNotEmpty(),
                                errorMessage = null,
                                currentOffset = it.currentOffset + 1,
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
        tabJob?.cancel()
        Log.d(TAG, "onCleared: jobs cancelled")
    }

    // ---- Tab loaders ----

    private suspend fun loadMyFeed(@Suppress("UNUSED_PARAMETER") range: FapFolderHotRange) {
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
                            totalPagesLoaded = 1,
                            trending = bundle.extras.trending,
                            suggested = bundle.extras.suggested,
                            feedLoadedAt = now,
                            filterSwitching = false,
                        )
                    }
                }
                is WebsiteState.Error -> handleError(state.throwable)
            }
        }
    }

    private suspend fun loadHot(range: FapFolderHotRange) {
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
                            totalPagesLoaded = 1,
                            trending = bundle.extras.trending,
                            suggested = bundle.extras.suggested,
                            feedLoadedAt = now,
                            filterSwitching = false,
                        )
                    }
                    // The initial home HTML ignores premfeed/time; trigger a
                    // dedicated reload so "Hot" respects the range.
                    if (page.feedId != null) {
                        reloadHotFirstPage(range)
                    }
                }
                is WebsiteState.Error -> handleError(state.throwable)
            }
        }
    }

    private suspend fun reloadHotFirstPage(range: FapFolderHotRange) {
        val state = _uiState.value
        val request = FapFolderLoadMoreRequest(
            get = state.feedGet,
            offset = 1,
            filter = FapFolderConstants.FILTER_ALL,
            id = state.feedId,
            type = state.feedType,
            uid = state.feedUid,
            premfeed = FapFolderConstants.PREMFEED_ON,
            time = range.param,
        )
        FapFolderNetworkRepo.loadMore(request).collect { pageState ->
            when (pageState) {
                is PageLoadingState.Loading -> Unit
                is PageLoadingState.Success -> {
                    val merged = mergeItems(emptyList(), pageState.info)
                    _uiState.update {
                        it.copy(
                            items = merged,
                            isLoadingMore = false,
                            hasMore = pageState.info.isNotEmpty(),
                            currentOffset = 2,
                            totalPagesLoaded = 1,
                            feedLoadedAt = System.currentTimeMillis(),
                        )
                    }
                }
                is PageLoadingState.NoMoreData -> _uiState.update {
                    it.copy(hasMore = false, currentOffset = 2)
                }
                is PageLoadingState.Error -> handleError(pageState.throwable)
            }
        }
    }

    private suspend fun loadDiscover(@Suppress("UNUSED_PARAMETER") range: FapFolderHotRange) {
        // Discover = combined latest videos + photos feed on the home page.
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
                            totalPagesLoaded = 1,
                            trending = bundle.extras.trending,
                            suggested = bundle.extras.suggested,
                            feedLoadedAt = now,
                            filterSwitching = false,
                        )
                    }
                }
                is WebsiteState.Error -> handleError(state.throwable)
            }
        }
    }

    private fun handleError(throwable: Throwable) {
        val msg = throwable.message
        val isSession = msg?.contains("session", ignoreCase = true) == true
        _uiState.update {
            it.copy(
                firstLoad = false,
                isRefreshing = false,
                isLoadingMore = false,
                errorMessage = if (isSession) null else (msg ?: "Failed to load FapFolder home."),
                sessionExpired = isSession,
                filterSwitching = false,
            )
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
