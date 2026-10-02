package com.yenaly.han1meviewer.HentaiMama.ui.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryEntity
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HentaiMamaHistoryUiState(
    val items: List<HentaiMamaHistoryEntity> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: Throwable? = null,
)

class HentaiMamaHistoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HentaiMamaHistoryVM"
        private const val PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(HentaiMamaHistoryUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var currentOffset = 0

    init {
        HentaiMamaHistoryRepo.init(application)
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { state: HentaiMamaHistoryUiState ->
                state.copy(
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
        val state: HentaiMamaHistoryUiState = _uiState.value
        if (state.isInitialLoading || state.isLoadingMore || !state.hasMore) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { current: HentaiMamaHistoryUiState ->
                current.copy(isLoadingMore = true, error = null)
            }
            loadPage(offset = currentOffset, replace = false)
        }
    }

    private suspend fun loadPage(offset: Int, replace: Boolean) {
        try {
            val page: List<HentaiMamaHistoryEntity> = withContext(Dispatchers.IO) {
                HentaiMamaHistoryRepo.getPage(PAGE_SIZE, offset)
            }

            if (page.isEmpty()) {
                _uiState.update { state: HentaiMamaHistoryUiState ->
                    state.copy(
                        isInitialLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                    )
                }
                return
            }

            currentOffset = offset + page.size

            _uiState.update { prev: HentaiMamaHistoryUiState ->
                val merged: List<HentaiMamaHistoryEntity> = if (replace) {
                    page
                } else {
                    (prev.items + page).distinctBy { it.videoCode }
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
            _uiState.update { state: HentaiMamaHistoryUiState ->
                state.copy(
                    isInitialLoading = false,
                    isLoadingMore = false,
                    error = e,
                )
            }
        }
    }

    fun deleteItem(videoCode: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                HentaiMamaHistoryRepo.deleteByVideoCode(videoCode)
            }
            _uiState.update { prev: HentaiMamaHistoryUiState ->
                val updated: List<HentaiMamaHistoryEntity> =
                    prev.items.filterNot { it.videoCode == videoCode }
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
                HentaiMamaHistoryRepo.deleteAll()
            }
            currentOffset = 0
            _uiState.update { state: HentaiMamaHistoryUiState ->
                state.copy(
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