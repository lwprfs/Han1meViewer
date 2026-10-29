package com.yenaly.han1meviewer.FapFolder.ui.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSearchHelper
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAutocompleteSuggestion
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchResult
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchTab
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderAutocompleteParser
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderFeedPaging
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderSearchUiState(
    val rawQuery: String = "",
    val isHashtag: Boolean = false,
    val activeTab: FapFolderSearchTab = FapFolderSearchTab.POSTS,
    val activeFilter: String = FapFolderConstants.FILTER_ALL,
    val items: List<FapFolderFeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val empty: Boolean = false,
    val currentOffset: Int = 1,
    val totalPagesLoaded: Int = 0,
    val autocompleteQuery: String = "",
    val autocompleteRows: List<FapFolderAutocompleteSuggestion> = emptyList(),
    val autocompleteLoading: Boolean = false,
    val autocompleteVisible: Boolean = false,
    val autocompleteError: String? = null,
) {
    val posts: List<FapFolderFeedItem.Post>
        get() = items.filterIsInstance<FapFolderFeedItem.Post>()

    val users: List<FapFolderFeedItem.User>
        get() = items.filterIsInstance<FapFolderFeedItem.User>()

    val communities: List<FapFolderFeedItem.Community>
        get() = items.filterIsInstance<FapFolderFeedItem.Community>()
}

sealed interface FapFolderSearchEvent {
    data object Submit : FapFolderSearchEvent
    data object LoadMore : FapFolderSearchEvent
    data class ChangeTab(val tab: FapFolderSearchTab) : FapFolderSearchEvent
    data class ChangeQuery(val query: String) : FapFolderSearchEvent
    data class ChangeFilter(val filter: String) : FapFolderSearchEvent
    data object Retry : FapFolderSearchEvent
}

class FapFolderSearchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderSearchVM"
        private const val MAX_PAGES = 50
        private const val AUTOCOMPLETE_DEBOUNCE_MS = 300L
    }

    private val _uiState = MutableStateFlow(FapFolderSearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FapFolderSearchEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var autocompleteJob: Job? = null

    private val seenKeys = mutableSetOf<String>()

    fun initialise(
        query: String?,
        isHashtag: Boolean,
        tabParam: String,
    ) {
        val trimmed = query?.trim().orEmpty()
        val tab = FapFolderSearchTab.fromParam(tabParam)
        val hashtag = isHashtag || FapFolderSearchHelper.detectHashtag(trimmed)

        _uiState.update {
            it.copy(
                rawQuery = trimmed,
                isHashtag = hashtag,
                activeTab = tab,
            )
        }

        if (trimmed.isNotBlank()) {
            submit()
        }
    }

    fun setQuery(query: String) {
        _uiState.update {
            it.copy(
                rawQuery = query,
                isHashtag = FapFolderSearchHelper.detectHashtag(query),
            )
        }
        scheduleAutocomplete(query)
    }

    fun changeTab(tab: FapFolderSearchTab) {
        if (_uiState.value.activeTab == tab) return
        _uiState.update { it.copy(activeTab = tab) }
        submit()
    }

    fun changeFilter(filter: String) {
        if (_uiState.value.activeFilter == filter) return
        _uiState.update { it.copy(activeFilter = filter) }
        if (_uiState.value.rawQuery.isNotBlank()) submit()
    }

    fun submit() {
        val state = _uiState.value
        val query = state.rawQuery.trim()
        if (query.isBlank() && !state.isHashtag) return

        autocompleteJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        seenKeys.clear()

        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                hasMore = false,
                empty = false,
                errorMessage = null,
                items = emptyList(),
                currentOffset = 1,
                totalPagesLoaded = 0,
                autocompleteVisible = false,
                autocompleteRows = emptyList(),
            )
        }

        searchJob = viewModelScope.launch {
            FapFolderNetworkRepo.getSearchPage(
                query = query.removePrefix("#"),
                isHashtag = state.isHashtag,
                tab = state.activeTab,
                page = 1,
            ).collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> applyFirstPage(result.info)
                    is WebsiteState.Error -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.throwable.message ?: "Search failed.",
                        )
                    }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore) return
        if (state.totalPagesLoaded >= MAX_PAGES) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }

            val req = FapFolderFeedPaging.search(
                query = state.rawQuery,
                offset = state.currentOffset,
                filter = state.activeFilter,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = FapFolderConstants.TIME_NO_FILTER,
                hashtag = state.isHashtag,
            )

            FapFolderNetworkRepo.loadMore(req).collect { page ->
                when (page) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val newItems = page.info
                        val merged = mergeItems(_uiState.value.items, newItems)
                        _uiState.update {
                            it.copy(
                                items = merged,
                                isLoadingMore = false,
                                hasMore = newItems.isNotEmpty(),
                                errorMessage = null,
                                currentOffset = it.currentOffset + 1,
                                totalPagesLoaded = it.totalPagesLoaded + 1,
                            )
                        }
                    }
                    is PageLoadingState.NoMoreData ->
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                    is PageLoadingState.Error ->
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                errorMessage = page.throwable.message,
                                hasMore = false,
                            )
                        }
                }
            }
        }
    }

    fun retry() {
        if (_uiState.value.items.isEmpty()) submit() else loadMore()
    }

    fun saveHistory() {
        val state = _uiState.value
        val query = state.rawQuery.trim()
        if (query.isBlank() && !state.isHashtag) return
        FapFolderSearchHelper.save(
            rawQuery = query,
            isHashtag = state.isHashtag,
            tab = state.activeTab,
        )
    }

    fun hideAutocomplete() {
        autocompleteJob?.cancel()
        _uiState.update { it.copy(autocompleteVisible = false) }
    }

    fun submitAutocomplete(suggestion: FapFolderAutocompleteSuggestion) {
        val state = _uiState.value
        when (suggestion) {
            is FapFolderAutocompleteSuggestion.SearchFor -> {
                _uiState.update {
                    it.copy(
                        rawQuery = suggestion.query,
                        isHashtag = suggestion.isHashtag,
                        autocompleteVisible = false,
                        autocompleteRows = emptyList(),
                    )
                }
                submit()
            }
            is FapFolderAutocompleteSuggestion.Hashtag -> {
                _uiState.update {
                    it.copy(
                        rawQuery = suggestion.tag,
                        isHashtag = true,
                        autocompleteVisible = false,
                        autocompleteRows = emptyList(),
                    )
                }
                submit()
            }
            else -> Unit
        }
    }

    private fun scheduleAutocomplete(query: String) {
        val trimmed = query.trim()
        autocompleteJob?.cancel()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    autocompleteRows = emptyList(),
                    autocompleteVisible = false,
                    autocompleteLoading = false,
                    autocompleteQuery = "",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                autocompleteQuery = trimmed,
                autocompleteVisible = true,
                autocompleteLoading = true,
                autocompleteError = null,
            )
        }
        autocompleteJob = viewModelScope.launch {
            delay(AUTOCOMPLETE_DEBOUNCE_MS)
            FapFolderNetworkRepo.autocompleteRows(trimmed).collect { state ->
                when (state) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> {
                        val rows = state.info
                        val merged = if (rows.isEmpty()) {
                            listOf(
                                FapFolderAutocompleteParser.fallbackFor(
                                    query = trimmed,
                                    isHashtag = _uiState.value.isHashtag,
                                    baseUrl = FapFolderConstants.BASE_URL,
                                )
                            )
                        } else {
                            rows
                        }
                        _uiState.update {
                            it.copy(
                                autocompleteRows = merged,
                                autocompleteLoading = false,
                                autocompleteVisible = true,
                            )
                        }
                    }
                    is WebsiteState.Error -> {
                        val fallback = listOf(
                            FapFolderAutocompleteParser.fallbackFor(
                                query = trimmed,
                                isHashtag = _uiState.value.isHashtag,
                                baseUrl = FapFolderConstants.BASE_URL,
                            )
                        )
                        _uiState.update {
                            it.copy(
                                autocompleteRows = fallback,
                                autocompleteLoading = false,
                                autocompleteError = state.throwable.message,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        autocompleteJob?.cancel()
        Log.d(TAG, "onCleared")
    }

    private fun applyFirstPage(result: FapFolderSearchResult) {
        if (result.empty) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    empty = true,
                    hasMore = false,
                    items = emptyList(),
                )
            }
            return
        }

        val merged = mergeItems(emptyList(), result.items)
        val hasMore = result.hasMore && result.items.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                hasMore = hasMore,
                empty = false,
                errorMessage = null,
                items = merged,
                currentOffset = 1,
                totalPagesLoaded = 1,
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
}