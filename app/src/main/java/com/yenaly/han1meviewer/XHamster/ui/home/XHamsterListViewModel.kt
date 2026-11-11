package com.yenaly.han1meviewer.XHamster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterAuth
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface XHamsterSource {

    data class Path(val path: String) : XHamsterSource
    data class Search(val query: String) : XHamsterSource
}

data class XHamsterListUiState(
    val items: List<HanimeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null,
)

class XHamsterListViewModel : ViewModel() {

    private val _state = MutableStateFlow(XHamsterListUiState())
    val state: StateFlow<XHamsterListUiState> = _state.asStateFlow()

    private var source: XHamsterSource? = null
    private var nextUrl: String? = null
    private var job: Job? = null

    fun setSource(newSource: XHamsterSource, force: Boolean = false) {
        if (!force && newSource == source && _state.value.items.isNotEmpty()) return
        source = newSource
        nextUrl = null
        job?.cancel()
        _state.value = XHamsterListUiState(isLoading = true)
        job = viewModelScope.launch {
            firstPage(newSource)
                .onSuccess { applyFirst(it) }
                .onFailure { e ->
                    _state.value = XHamsterListUiState(error = e.message ?: e.javaClass.simpleName)
                }
        }
    }

    fun refresh() {
        source?.let { setSource(it, force = true) }
    }

    fun loadMore() {
        val current = _state.value
        val url = nextUrl ?: return
        if (current.isLoading || current.isLoadingMore || !current.hasNext) return
        _state.update { it.copy(isLoadingMore = true) }
        job = viewModelScope.launch {
            XHamsterRepo.list(url)
                .onSuccess { result ->
                    _state.update { old ->
                        val merged = (old.items + result.items).distinctBy { it.videoCode }

                        val progressed = merged.size > old.items.size
                        nextUrl = if (progressed) result.nextUrl else null
                        old.copy(
                            items = merged,
                            isLoadingMore = false,
                            hasNext = progressed && result.nextUrl != null,
                        )
                    }
                }
                .onFailure { _state.update { old -> old.copy(isLoadingMore = false) } }
        }
    }

    private fun applyFirst(result: XHamsterListPage) {
        nextUrl = result.nextUrl
        _state.value = XHamsterListUiState(
            items = result.items,
            hasNext = result.nextUrl != null && result.items.isNotEmpty(),
        )
    }

    private suspend fun firstPage(src: XHamsterSource): Result<XHamsterListPage> = when (src) {
        is XHamsterSource.Path ->
            if (src.path.startsWith("/my/") && !XHamsterAuth.isLoggedIn) {
                Result.failure(IOException(applicationContext.getString(R.string.xhamster_login_required)))
            } else {
                XHamsterRepo.list(XHamsterConstants.listUrl(src.path))
            }
        is XHamsterSource.Search -> XHamsterRepo.search(src.query)
    }
}
