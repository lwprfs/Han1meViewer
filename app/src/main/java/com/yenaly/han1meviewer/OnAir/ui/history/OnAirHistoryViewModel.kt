package com.yenaly.han1meviewer.OnAir.ui.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.OnAir.data.local.OnAirHistoryEntity
import com.yenaly.han1meviewer.OnAir.data.local.OnAirHistoryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnAirHistoryUiState(
    val items: List<OnAirHistoryEntity> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: Throwable? = null,
)

class OnAirHistoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OnAirHistoryViewModel"
        private const val PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(OnAirHistoryUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var currentOffset = 0

    init {
        OnAirHistoryRepo.init(application)
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    isInitialLoading = true,
                    isLoadingMore = false,
                    hasMore = true,
                    error = null,
                )
            }
            currentOffset = 0
            loadPage(offset = 0, replace = true)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isInitialLoading || state.isLoadingMore || !state.hasMore) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, error = null) }
            loadPage(offset = currentOffset, replace = false)
        }
    }

    private suspend fun loadPage(offset: Int, replace: Boolean) {
        try {
            val page = withContext(Dispatchers.IO) {
                OnAirHistoryRepo.getPage(PAGE_SIZE, offset)
            }
            if (page.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                    )
                }
                return
            }
            currentOffset = offset + page.size
            _uiState.update { prev ->
                val merged = if (replace) {
                    page
                } else {
                    (prev.items + page).distinctBy { it.videoId }
                }
                prev.copy(
                    items = merged,
                    isInitialLoading = false,
                    isLoadingMore = false,
                    hasMore = page.size >= PAGE_SIZE,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadPage failed", e)
            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    isLoadingMore = false,
                    error = e,
                )
            }
        }
    }

    fun deleteItem(videoId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OnAirHistoryRepo.deleteByVideoId(videoId)
            }
            _uiState.update { prev ->
                val updated = prev.items.filterNot { it.videoId == videoId }
                prev.copy(
                    items = updated,
                    hasMore = prev.hasMore || updated.size < currentOffset,
                )
            }
            currentOffset = (currentOffset - 1).coerceAtLeast(0)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OnAirHistoryRepo.deleteAll()
            }
            currentOffset = 0
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    hasMore = false,
                    isInitialLoading = false,
                    isLoadingMore = false,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}
