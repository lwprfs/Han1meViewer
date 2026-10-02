package com.yenaly.han1meviewer.HentaiMama.ui.recent

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.model.RecentEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.RecentEpisodesPageInfo
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaRecentEpisodesParser
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class HentaiMamaRecentUiState(
    val items: List<RecentEpisode> = emptyList(),
    val pageInfo: RecentEpisodesPageInfo? = null,
    val isLoadingFirst: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
)

class HentaiMamaRecentEpisodesViewModel(application: Application)
    : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HentaiMamaRecentVM"
        private const val MAX_CACHED_PAGES = 30
        private const val MIN_DELAY_MS = 700L
        private const val MAX_DELAY_MS = 1300L
    }

    private val _uiState = MutableStateFlow(HentaiMamaRecentUiState())
    val uiState = _uiState.asStateFlow()

    private val pageCache = ConcurrentHashMap<Int, List<RecentEpisode>>()
    private var loadJob: Job? = null

    init {
        loadPage(1, replace = true)
    }

    fun refresh() {
        pageCache.clear()
        loadPage(1, replace = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoadingFirst) return
        val info = state.pageInfo ?: return
        if (info.current >= info.total) return
        loadPage(info.current + 1, replace = false)
    }

    private fun loadPage(pageNumber: Int, replace: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingFirst = replace,
                    isLoadingMore = !replace,
                    error = null,
                )
            }

            val cached = pageCache[pageNumber]
            if (cached != null) {
                applyPage(pageNumber, cached, replace)
                return@launch
            }

            val url = buildUrl(pageNumber)
            try {
                val body = withContext(Dispatchers.IO) {
                    val response = HentaiMamaNetwork.service.getVideoDetail(url)
                    if (response.isSuccessful) response.body()?.string().orEmpty() else ""
                }
                if (!isActive) return@launch

                if (body.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoadingFirst = false,
                            isLoadingMore = false,
                            error = IllegalStateException("Empty page $pageNumber"),
                        )
                    }
                    return@launch
                }

                val page = withContext(Dispatchers.IO) {
                    HentaiMamaRecentEpisodesParser.parse(body, url, pageNumber)
                }

                if (pageCache.size >= MAX_CACHED_PAGES) {
                    val oldest = pageCache.keys.minOrNull()
                    if (oldest != null) pageCache.remove(oldest)
                }
                pageCache[pageNumber] = page.items

                applyPage(pageNumber, page.items, replace, page.pageInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recent page $pageNumber", e)
                _uiState.update {
                    it.copy(
                        isLoadingFirst = false,
                        isLoadingMore = false,
                        error = e,
                    )
                }
            }
        }
    }

    private fun applyPage(
        pageNumber: Int,
        items: List<RecentEpisode>,
        replace: Boolean,
        pageInfo: RecentEpisodesPageInfo? = null,
    ) {
        _uiState.update { prev ->
            val merged = if (replace) items
            else (prev.items + items).distinctBy { it.postId }

            val updatedPageInfo = pageInfo ?: prev.pageInfo?.copy(current = pageNumber)
                ?: RecentEpisodesPageInfo(
                    current = pageNumber,
                    total = pageNumber,
                    nextUrl = null,
                    lastUrl = null,
                )

            prev.copy(
                items = merged,
                pageInfo = updatedPageInfo,
                isLoadingFirst = false,
                isLoadingMore = false,
                error = null,
            )
        }

        if (!replace) {
            viewModelScope.launch {
                delay(MIN_DELAY_MS + Random.nextLong(MAX_DELAY_MS - MIN_DELAY_MS))
            }
        }
    }

    private fun buildUrl(pageNumber: Int): String {
        val base = HentaiMamaNetwork.baseUrl.trimEnd('/')
        return if (pageNumber <= 1) "$base/recent-episodes/"
        else "$base/recent-episodes/page/$pageNumber/"
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}
