package com.yenaly.han1meviewer.FapFolder.ui.followed

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupRow
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderFollowedCommunitiesUiState(
    val username: String = "",
    val userId: Long? = null,
    val items: List<FapFolderGroupRow> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
    val currentOffset: Int = 1,
)

class FapFolderFollowedCommunitiesViewModel(
    application: Application,
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFollowedCommVM"
        private const val MAX_PAGES = 100
    }

    private val _uiState = MutableStateFlow(FapFolderFollowedCommunitiesUiState())
    val uiState = _uiState.asStateFlow()

    private var headerJob: Job? = null
    private var moreJob: Job? = null

    private val seenKeys = mutableSetOf<String>()

    fun load() {
        if (_uiState.value.items.isNotEmpty() && _uiState.value.userId != null) return

        headerJob?.cancel()
        moreJob?.cancel()
        seenKeys.clear()

        _uiState.update {
            it.copy(
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMore = false,
                errorMessage = null,
                sessionExpired = false,
                currentOffset = 1,
            )
        }

        val cachedUsername = FapFolderSessionCache.viewerUsername()
        if (cachedUsername.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Not signed in. Sign in to view followed communities.",
                    sessionExpired = true,
                )
            }
            return
        }

        headerJob = viewModelScope.launch {
            FapFolderNetworkRepo.getFollowedCommunitiesFirstPage(cachedUsername)
                .collect { state ->
                    when (state) {
                        is VideoLoadingState.Loading -> Unit
                        is VideoLoadingState.Success -> {
                            val page = state.info
                            val fresh = page.groups.filter {
                                seenKeys.add(keyOf(it))
                            }
                            _uiState.update {
                                it.copy(
                                    username = page.username ?: cachedUsername,
                                    userId = page.userId,
                                    items = fresh,
                                    isLoading = false,
                                    hasMore = page.hasMore && fresh.isNotEmpty(),
                                    currentOffset = 1,
                                    errorMessage = null,
                                    sessionExpired = false,
                                )
                            }
                        }
                        is VideoLoadingState.NoContent -> _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Profile not found.",
                            )
                        }
                        is VideoLoadingState.Error -> {
                            val msg = state.throwable.message
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = msg ?: "Failed to load.",
                                    sessionExpired =
                                        msg?.contains("session", true) == true,
                                )
                            }
                        }
                    }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(items = emptyList(), userId = null) }
        load()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val uid = state.userId ?: return
        if (state.currentOffset >= MAX_PAGES) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }

        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }

            FapFolderNetworkRepo.loadMoreFollowedCommunities(uid, state.currentOffset)
                .collect { page ->
                    when (page) {
                        is PageLoadingState.Loading -> Unit
                        is PageLoadingState.Success -> {
                            val fresh = page.info.filter { seenKeys.add(keyOf(it)) }
                            _uiState.update {
                                it.copy(
                                    items = it.items + fresh,
                                    isLoadingMore = false,
                                    hasMore = page.info.isNotEmpty() && fresh.isNotEmpty(),
                                    currentOffset = it.currentOffset + 1,
                                    errorMessage = null,
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

    private fun keyOf(group: FapFolderGroupRow): String =
        group.groupId?.toString() ?: group.slug.lowercase()

    override fun onCleared() {
        super.onCleared()
        headerJob?.cancel()
        moreJob?.cancel()
        Log.d(TAG, "onCleared")
    }
}
