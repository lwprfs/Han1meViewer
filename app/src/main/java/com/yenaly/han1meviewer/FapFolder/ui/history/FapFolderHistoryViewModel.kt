package com.yenaly.han1meviewer.FapFolder.ui.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryEntity
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderHistoryUiState(
    val entries: List<FapFolderHistoryEntity> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val currentPage: Int = 0,
) {
    val isEmpty: Boolean
        get() = !isLoading && entries.isEmpty()
}

class FapFolderHistoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderHistoryVM"
        const val PAGE_SIZE = 40
    }

    private val _uiState = MutableStateFlow(FapFolderHistoryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, currentPage = 0) }
            runCatching {
                val total = FapFolderHistoryRepo.getTotalCount()
                val page = FapFolderHistoryRepo.getPage(PAGE_SIZE, 0)
                total to page
            }.onSuccess { (total, page) ->
                _uiState.update {
                    it.copy(
                        entries = page,
                        totalCount = total,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = page.size >= PAGE_SIZE,
                        errorMessage = null,
                        currentPage = 1,
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "refresh failed", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = error.message ?: "Failed to load history.",
                    )
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            val offset = state.currentPage * PAGE_SIZE
            runCatching { FapFolderHistoryRepo.getPage(PAGE_SIZE, offset) }
                .onSuccess { page ->
                    _uiState.update { current ->
                        val merged = (current.entries + page).distinctBy { it.postId }
                        current.copy(
                            entries = merged,
                            isLoadingMore = false,
                            hasMore = page.size >= PAGE_SIZE,
                            currentPage = current.currentPage + 1,
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "loadMore failed", error)
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            errorMessage = error.message ?: "Failed to load more.",
                        )
                    }
                }
        }
    }

    fun deleteEntry(postId: Long) {
        viewModelScope.launch {
            runCatching { FapFolderHistoryRepo.deleteByPostId(postId) }
                .onSuccess {
                    _uiState.update { current ->
                        val remaining = current.entries.filterNot { it.postId == postId }
                        current.copy(
                            entries = remaining,
                            totalCount = (current.totalCount - 1).coerceAtLeast(0),
                        )
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "deleteEntry failed", error)
                }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { FapFolderHistoryRepo.deleteAll() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            entries = emptyList(),
                            totalCount = 0,
                            hasMore = false,
                            currentPage = 0,
                        )
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "clearAll failed", error)
                }
        }
    }

    fun markStartedOver(postId: Long) {
        viewModelScope.launch {
            runCatching { FapFolderHistoryRepo.markStartedOver(postId) }
                .onSuccess {
                    _uiState.update { current ->
                        val updated = current.entries.map { entry ->
                            if (entry.postId == postId) {
                                entry.copy(
                                    lastPosition = 0L,
                                    completed = false,
                                    watchDate = System.currentTimeMillis(),
                                )
                            } else {
                                entry
                            }
                        }
                        current.copy(entries = updated)
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "markStartedOver failed", error)
                }
        }
    }
}
