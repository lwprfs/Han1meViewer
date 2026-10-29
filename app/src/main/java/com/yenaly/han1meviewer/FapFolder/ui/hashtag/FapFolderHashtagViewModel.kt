package com.yenaly.han1meviewer.FapFolder.ui.hashtag

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchResult
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchTab
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderFeedPaging
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderHashtagUiState(
    val normalisedTag: String = "",
    val items: List<FapFolderPost> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val empty: Boolean = false,
    val errorMessage: String? = null,
    val currentOffset: Int = 1,
    val totalPagesLoaded: Int = 0,
)

class FapFolderHashtagViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderHashtagVM"
        private const val MAX_PAGES = 50
    }

    private val _uiState = MutableStateFlow(FapFolderHashtagUiState())
    val uiState = _uiState.asStateFlow()

    private var firstJob: Job? = null
    private var moreJob: Job? = null

    private val seenPostIds = mutableSetOf<Long>()

    fun load(tag: String) {
        val normalised = tag.trim().removePrefix("#")
        if (normalised.isBlank()) return
        if (_uiState.value.normalisedTag == normalised && _uiState.value.items.isNotEmpty()) return

        firstJob?.cancel()
        moreJob?.cancel()
        seenPostIds.clear()

        _uiState.update {
            it.copy(
                normalisedTag = normalised,
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMore = false,
                empty = false,
                errorMessage = null,
                currentOffset = 1,
                totalPagesLoaded = 0,
            )
        }

        firstJob = viewModelScope.launch {
            FapFolderNetworkRepo.getSearchPage(
                query = normalised,
                isHashtag = true,
                tab = FapFolderSearchTab.POSTS,
                page = 1,
            ).collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> applyFirstPage(result.info)
                    is WebsiteState.Error -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.throwable.message
                                ?: "Failed to load #$normalised",
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        val tag = _uiState.value.normalisedTag
        if (tag.isBlank()) return
        _uiState.update { it.copy(normalisedTag = "") }
        load(tag)
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

            val request = FapFolderFeedPaging.search(
                query = state.normalisedTag,
                offset = state.currentOffset,
                filter = FapFolderConstants.FILTER_ALL,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = FapFolderConstants.TIME_NO_FILTER,
                hashtag = true,
            )

            FapFolderNetworkRepo.loadMore(request).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit

                    is PageLoadingState.Success -> {
                        val newPosts = pageState.info.mapNotNull {
                            (it as? FapFolderFeedItem.Post)?.post
                        }
                        val filtered = newPosts.filter { seenPostIds.add(it.postId) }
                        _uiState.update {
                            it.copy(
                                items = it.items + filtered,
                                isLoadingMore = false,
                                hasMore = newPosts.isNotEmpty(),
                                errorMessage = null,
                                currentOffset = it.currentOffset + 1,
                                totalPagesLoaded = it.totalPagesLoaded + 1,
                            )
                        }
                    }

                    is PageLoadingState.NoMoreData -> _uiState.update {
                        it.copy(isLoadingMore = false, hasMore = false)
                    }

                    is PageLoadingState.Error -> _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            errorMessage = pageState.throwable.message,
                            hasMore = false,
                        )
                    }
                }
            }
        }
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

        val posts = result.items.mapNotNull {
            (it as? FapFolderFeedItem.Post)?.post
        }
        posts.forEach { seenPostIds.add(it.postId) }

        _uiState.update {
            it.copy(
                items = posts,
                isLoading = false,
                isLoadingMore = false,
                hasMore = result.hasMore && posts.isNotEmpty(),
                empty = false,
                errorMessage = null,
                currentOffset = 1,
                totalPagesLoaded = 1,
            )
        }

        if (posts.isEmpty()) Log.w(TAG, "First page produced 0 posts for #${result.query}")
    }

    override fun onCleared() {
        super.onCleared()
        firstJob?.cancel()
        moreJob?.cancel()
    }
}
