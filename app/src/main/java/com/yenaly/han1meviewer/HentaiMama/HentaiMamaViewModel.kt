package com.yenaly.han1meviewer.HentaiMama

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HentaiMamaViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HentaiMamaVM"

    private val _homeState =
        MutableStateFlow<WebsiteState<HentaiMamaHomePage>>(WebsiteState.Loading)
    val homeState = _homeState.asStateFlow()

    private val _searchState =
        MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val searchState = _searchState.asStateFlow()

    private val _videoState =
        MutableStateFlow<VideoLoadingState<HentaiMamaVideoInfo>>(VideoLoadingState.Loading)
    val videoState = _videoState.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _selectedProducer = MutableStateFlow<String?>(null)
    val selectedProducer = _selectedProducer.asStateFlow()

    private val _selectedYear = MutableStateFlow<String?>(null)
    val selectedYear = _selectedYear.asStateFlow()

    private val _selectedOrder = MutableStateFlow<String?>(null)
    val selectedOrder = _selectedOrder.asStateFlow()

    fun getHomePage() {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.getHomePage().collect { _homeState.value = it }
        }
    }

    fun searchVideos(page: Int, query: String) {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.searchVideos(page, query).collect { _searchState.value = it }
        }
    }

    fun filterVideos(page: Int) {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.filterVideos(
                page = page,
                genre = _selectedGenre.value,
                producer = _selectedProducer.value,
                year = _selectedYear.value,
                order = _selectedOrder.value,
            ).collect { _searchState.value = it }
        }
    }

    fun setGenre(genre: String?) { _selectedGenre.value = genre }
    fun setProducer(producer: String?) { _selectedProducer.value = producer }
    fun setYear(year: String?) { _selectedYear.value = year }
    fun setOrder(order: String?) { _selectedOrder.value = order }

    fun clearFilters() {
        _selectedGenre.value = null
        _selectedProducer.value = null
        _selectedYear.value = null
        _selectedOrder.value = null
    }

    fun getVideoDetail(url: String) {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.getVideoDetail(url).collect { _videoState.value = it }
        }
    }

    suspend fun fetchDetailBody(url: String): String = withContext(Dispatchers.IO) {
        try {
            val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
            val response = HentaiMamaNetwork.service.getVideoDetail(full)
            if (response.isSuccessful) response.body()?.string().orEmpty() else ""
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetailBody error: ${e.message}")
            ""
        }
    }

    suspend fun fetchHosterTabs(url: String): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        try {
            val body = fetchDetailBody(url)
            if (body.isBlank()) emptyList()
            else HentaiMamaNetworkRepo.extractHosterTabs(body)
        } catch (e: Exception) {
            Log.e(TAG, "fetchHosterTabs error: ${e.message}")
            emptyList()
        }
    }
}
