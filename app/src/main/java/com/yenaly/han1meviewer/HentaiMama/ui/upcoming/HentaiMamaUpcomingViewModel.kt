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
    val selectedMonth: UpcomingMonthOption? = null,
    val availableMonths: List<UpcomingMonthOption> = emptyList(),
    val page: UpcomingPage? = null,
    val cards: List<UpcomingCard> = emptyList(),
    val isLoadingMonths: Boolean = true,
    val isLoading: Boolean = false,
    val error: Throwable? = null,
)

class HentaiMamaUpcomingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HentaiMamaUpcomingVM"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(HentaiMamaUpcomingUiState())
    val uiState = _uiState.asStateFlow()

    private val monthCache = ConcurrentHashMap<String, CachedPage>()
    private var monthsJob: Job? = null
    private var loadJob: Job? = null

    private data class CachedPage(val page: UpcomingPage, val fetchedAt: Long)

    init {
        loadMonthsAndInitial()
    }

    fun selectMonth(option: UpcomingMonthOption) {
        if (_uiState.value.selectedMonth?.slug == option.slug) return
        _uiState.update { it.copy(selectedMonth = option) }
        load(option, force = false)
    }

    fun refresh() {
        loadMonthsAndInitial(preferSlug = _uiState.value.selectedMonth?.slug)
    }

    private fun loadMonthsAndInitial(preferSlug: String? = null) {
        monthsJob?.cancel()
        monthsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMonths = true, error = null) }
            try {
                val url = "${HentaiMamaNetwork.baseUrl}/upcoming/"
                val body = withContext(Dispatchers.IO) {
                    val response = HentaiMamaNetwork.service.getVideoDetail(url)
                    if (response.isSuccessful) response.body()?.string().orEmpty() else ""
                }
                if (!isActive) return@launch

                if (body.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoadingMonths = false,
                            error = IllegalStateException("Empty response for /upcoming/"),
                        )
                    }
                    return@launch
                }

                val months = withContext(Dispatchers.IO) {
                    HentaiMamaUpcomingParser.parseMonths(body, url)
                }

                if (months.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoadingMonths = false,
                            error = IllegalStateException("No months found on /upcoming/"),
                        )
                    }
                    return@launch
                }

                val chosen = months.firstOrNull { it.slug == preferSlug } ?: months.first()

                _uiState.update {
                    it.copy(
                        availableMonths = months,
                        selectedMonth = chosen,
                        isLoadingMonths = false,
                    )
                }

                load(chosen, force = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load upcoming index", e)
                _uiState.update { it.copy(isLoadingMonths = false, error = e) }
            }
        }
    }

    private fun load(option: UpcomingMonthOption, force: Boolean) {
        val slug = option.slug
        val cached = monthCache[slug]
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
            _uiState.update { it.copy(isLoading = true, error = null) }
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

                monthCache[slug] = CachedPage(page, System.currentTimeMillis())

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
                _uiState.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        monthsJob?.cancel()
        loadJob?.cancel()
    }
}
