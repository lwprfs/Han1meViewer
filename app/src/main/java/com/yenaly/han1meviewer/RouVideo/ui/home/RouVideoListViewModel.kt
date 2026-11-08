package com.yenaly.han1meviewer.RouVideo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.SeriesFilter
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.SeriesSort
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.Sort
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoCard
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoListPage
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoTagCount
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RouVideoSource {

    data class Library(val sort: Sort) : RouVideoSource

    data class Tag(val tag: String, val sort: Sort) : RouVideoSource

    data class Search(
        val query: String,
        val tag: String,
        val sort: Sort,
        val series: Boolean,
    ) : RouVideoSource

    data class Series(
        val tag: String,
        val filter: SeriesFilter,
        val sort: SeriesSort,
    ) : RouVideoSource
}

data class RouVideoListUiState(
    val items: List<RouVideoCard> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null,
    val total: Int = 0,

    val siblings: List<RouVideoTagCount> = emptyList(),

    val tagOptions: List<RouVideoTagCount> = emptyList(),
)

class RouVideoListViewModel : ViewModel() {

    private val _state = MutableStateFlow(RouVideoListUiState())
    val state: StateFlow<RouVideoListUiState> = _state.asStateFlow()

    private var source: RouVideoSource? = null
    private var page = 1
    private var job: Job? = null

    fun setSource(newSource: RouVideoSource, force: Boolean = false) {
        if (!force && newSource == source && _state.value.items.isNotEmpty()) return
        source = newSource
        page = 1
        job?.cancel()
        _state.value = RouVideoListUiState(isLoading = true)
        job = viewModelScope.launch {
            fetch(newSource, 1)
                .onSuccess { applyFirst(it) }
                .onFailure { e ->
                    _state.value = RouVideoListUiState(error = e.message ?: e.javaClass.simpleName)
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
                        old.copy(
                            items = (old.items + result.items).distinctBy { it.id },
                            isLoadingMore = false,
                            hasNext = result.hasNext && result.items.isNotEmpty(),
                        )
                    }
                }
                .onFailure { _state.update { old -> old.copy(isLoadingMore = false) } }
        }
    }

    private fun applyFirst(result: RouVideoListPage) {
        _state.value = RouVideoListUiState(
            items = result.items.distinctBy { it.id },
            hasNext = result.hasNext && result.items.isNotEmpty(),
            total = result.total,
            siblings = result.siblings,
            tagOptions = result.tagOptions,
        )
    }

    private suspend fun fetch(src: RouVideoSource, page: Int): Result<RouVideoListPage> =
        when (src) {
            is RouVideoSource.Library -> RouVideoRepo.library(src.sort, page)
            is RouVideoSource.Tag -> RouVideoRepo.tag(src.tag, src.sort, page)
            is RouVideoSource.Search ->
                RouVideoRepo.search(src.query, src.tag, src.sort, page, src.series)

            is RouVideoSource.Series -> RouVideoRepo.series(src.tag, src.filter, src.sort, page)
        }
}
