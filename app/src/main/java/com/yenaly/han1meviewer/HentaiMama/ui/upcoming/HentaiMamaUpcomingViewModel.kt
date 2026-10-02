package com.yenaly.han1meviewer.HentaiMama.ui.upcoming

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingCard
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingMonthOption
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingPage
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaUpcomingParser
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class HentaiMamaUpcomingUiState(
    val selectedMonth: UpcomingMonthOption = HentaiMamaUpcomingParser.currentMonthOption(),
    val availableMonths: List<UpcomingMonthOption> = emptyList(),
    val page: UpcomingPage? = null,
    val cards: List<UpcomingCard> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null,
)

class HentaiMamaUpcomingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HentaiMamaUpcomingVM"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(
        HentaiMamaUpcomingUiState(
            availableMonths = HentaiMamaUpcomingParser.buildMonthOptions(
                centerYear = HentaiMamaUpcomingParser.currentMonthOption().year,
                centerMonth = HentaiMamaUpcomingParser.currentMonthOption().monthNumber,
            )
        )
    )
    val uiState = _uiState.asStateFlow()

    private val cache = ConcurrentHashMap<String, CachedPage>()
    private var loadJob: Job? = null

    private data class CachedPage(val page: UpcomingPage, val fetchedAt: Long)

    init {
        load(_uiState.value.selectedMonth, force = false)
    }

    fun selectMonth(option: UpcomingMonthOption) {
        if (_uiState.value.selectedMonth.slug == option.slug) return
        _uiState.update { it.copy(selectedMonth = option) }
        load(option, force = false)
    }

    fun refresh() {
        load(_uiState.value.selectedMonth, force = true)
    }

    private fun load(option: UpcomingMonthOption, force: Boolean) {
        val slug = option.slug
        val cached = cache[slug]
        val now = System.currentTimeMillis()

        if (!force && cached != null && now - cached.fetchedAt < CACHE_TTL_MS) {
            _uiState.update {
                it.copy(
                    page = cached.page,
                    cards = cached.page.cards,
                    isLoading = false,
                    error = null,
                )
            }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null)
            }
            try {
                val url = "${HentaiMamaNetwork.baseUrl}/upcoming/$slug/"
                val body = withContext(Dispatchers.IO) {
                    val response = HentaiMamaNetwork.service.getVideoDetail(url)
                    if (response.isSuccessful) response.body()?.string().orEmpty() else ""
                }
                if (!isActive) return@launch

                if (body.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = IllegalStateException("Empty response for $slug"),
                        )
                    }
                    return@launch
                }

                val page = withContext(Dispatchers.IO) {
                    HentaiMamaUpcomingParser.parse(body, url)
                }

                cache[slug] = CachedPage(page, System.currentTimeMillis())

                _uiState.update {
                    it.copy(
                        page = page,
                        cards = page.cards,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load upcoming $slug", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}