package com.yenaly.han1meviewer.XHamster.ui.entities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class XHamsterPagedState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null,
)

abstract class XHamsterPagedViewModel<T> : ViewModel() {

    private val _state = MutableStateFlow(XHamsterPagedState<T>())
    val state: StateFlow<XHamsterPagedState<T>> = _state.asStateFlow()

    private var url: String? = null
    private var nextUrl: String? = null
    private var job: Job? = null

    protected abstract suspend fun load(url: String): Result<XHamsterPage<T>>
    protected abstract fun keyOf(item: T): Any

    fun setUrl(newUrl: String, force: Boolean = false) {
        if (!force && newUrl == url && _state.value.items.isNotEmpty()) return
        url = newUrl
        nextUrl = null
        job?.cancel()
        _state.value = XHamsterPagedState(isLoading = true)
        job = viewModelScope.launch {
            load(newUrl)
                .onSuccess { page ->
                    nextUrl = page.nextUrl
                    _state.value = XHamsterPagedState(
                        items = page.items.distinctBy { keyOf(it) },
                        hasNext = page.nextUrl != null && page.items.isNotEmpty(),
                    )
                }
                .onFailure { e ->
                    _state.value = XHamsterPagedState(error = e.message ?: e.javaClass.simpleName)
                }
        }
    }

    fun refresh() {
        url?.let { setUrl(it, force = true) }
    }

    fun loadMore() {
        val current = _state.value
        val next = nextUrl ?: return
        if (current.isLoading || current.isLoadingMore || !current.hasNext) return
        _state.update { it.copy(isLoadingMore = true) }
        job = viewModelScope.launch {
            load(next)
                .onSuccess { page ->
                    _state.update { old ->
                        val merged = (old.items + page.items).distinctBy { keyOf(it) }
                        val progressed = merged.size > old.items.size
                        nextUrl = if (progressed) page.nextUrl else null
                        old.copy(
                            items = merged,
                            isLoadingMore = false,
                            hasNext = progressed && page.nextUrl != null,
                        )
                    }
                }
                .onFailure { _state.update { old -> old.copy(isLoadingMore = false) } }
        }
    }
}
