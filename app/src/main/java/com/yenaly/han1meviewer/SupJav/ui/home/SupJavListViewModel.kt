package com.yenaly.han1meviewer.SupJav.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.SupJav.common.SupJavSettings
import com.yenaly.han1meviewer.SupJav.data.model.SupJavListPage
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavRepo
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SupJavSource {
    data class Category(val path: String) : SupJavSource
    data class Search(val query: String) : SupJavSource
}

data class SupJavListUiState(
    val items: List<HanimeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null,
)

class SupJavListViewModel : ViewModel() {

    private val _state = MutableStateFlow(SupJavListUiState())
    val state: StateFlow<SupJavListUiState> = _state.asStateFlow()

    private var source: SupJavSource? = null
    private var loadedLanguage: String = SupJavSettings.language
    private var page = 1
    private var job: Job? = null

    fun setSource(newSource: SupJavSource, force: Boolean = false) {
        val language = SupJavSettings.language
        if (!force && newSource == source && language == loadedLanguage &&
            _state.value.items.isNotEmpty()
        ) return
        source = newSource
        loadedLanguage = language
        page = 1
        job?.cancel()
        _state.value = SupJavListUiState(isLoading = true)
        job = viewModelScope.launch {
            fetch(newSource, 1)
                .onSuccess { applyFirst(it) }
                .onFailure { e ->
                    _state.value = SupJavListUiState(error = e.message ?: e.javaClass.simpleName)
                }
        }
    }

    fun refresh() {
        source?.let { setSource(it, force = true) }
    }

    fun loadMore() {
        val current = _state.value
        val src = source ?: return
        if (current.isLoading || current.isLoadingMore || !current.hasNext) return
        _state.update { it.copy(isLoadingMore = true) }
        job = viewModelScope.launch {
            val next = page + 1
            fetch(src, next)
                .onSuccess { result ->
                    page = next
                    _state.update { old ->
                        val merged = (old.items + result.items).distinctBy { it.videoCode }
                        old.copy(
                            items = merged,
                            isLoadingMore = false,
                            hasNext = result.hasNext && result.items.isNotEmpty(),
                        )
                    }
                }
                .onFailure { _state.update { old -> old.copy(isLoadingMore = false) } }
        }
    }

    private fun applyFirst(result: SupJavListPage) {
        _state.value = SupJavListUiState(
            items = result.items,
            hasNext = result.hasNext && result.items.isNotEmpty(),
        )
    }

    private suspend fun fetch(src: SupJavSource, page: Int): Result<SupJavListPage> = when (src) {
        is SupJavSource.Category -> SupJavRepo.category(src.path, page)
        is SupJavSource.Search -> SupJavRepo.search(src.query, page)
    }
}
