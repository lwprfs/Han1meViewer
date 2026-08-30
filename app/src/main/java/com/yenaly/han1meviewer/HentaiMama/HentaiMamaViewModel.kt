package com.yenaly.han1meviewer.HentaiMama

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HentaiMamaViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HentaiMamaVM"

    private val _homeState = MutableStateFlow<WebsiteState<HentaiMamaHomePage>>(WebsiteState.Loading)
    val homeState = _homeState.asStateFlow()

    private val _searchState = MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val searchState = _searchState.asStateFlow()

    private val _videoState = MutableStateFlow<VideoLoadingState<HentaiMamaVideoInfo>>(VideoLoadingState.Loading)
    val videoState = _videoState.asStateFlow()

    // Filter states
    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _selectedProducer = MutableStateFlow<String?>(null)
    val selectedProducer = _selectedProducer.asStateFlow()

    private val _selectedOrder = MutableStateFlow<String?>(null)
    val selectedOrder = _selectedOrder.asStateFlow()

    fun getHomePage() {
        viewModelScope.launch {
            Log.d(TAG, "getHomePage called")
            
            // Get popular videos first
            var popularVideos = emptyList<HanimeInfo>()
            
            HentaiMamaNetworkRepo.getHomePage().collect { state ->
                when (state) {
                    is WebsiteState.Success -> {
                        Log.d(TAG, "Got popular videos: ${state.info.popularVideos.size}")
                        popularVideos = state.info.popularVideos
                        
                        // Now get latest videos
                        HentaiMamaNetworkRepo.getLatestVideos(1).collect { latestState ->
                            when (latestState) {
                                is PageLoadingState.Success -> {
                                    Log.d(TAG, "Got latest videos: ${latestState.info.size}")
                                    _homeState.value = WebsiteState.Success(
                                        HentaiMamaHomePage(
                                            popularVideos = popularVideos,
                                            latestVideos = latestState.info
                                        )
                                    )
                                }
                                is PageLoadingState.Error -> {
                                    Log.e(TAG, "Error getting latest videos, using popular for both")
                                    // Fallback: use popular videos for both
                                    _homeState.value = WebsiteState.Success(
                                        HentaiMamaHomePage(
                                            popularVideos = popularVideos,
                                            latestVideos = popularVideos
                                        )
                                    )
                                }
                                else -> {
                                    // Keep current state
                                }
                            }
                        }
                    }
                    else -> {
                        _homeState.value = state
                    }
                }
            }
        }
    }

    fun searchVideos(page: Int, query: String) {
        viewModelScope.launch {
            Log.d(TAG, "searchVideos page=$page, query='$query'")
            HentaiMamaNetworkRepo.searchVideos(page, query).collect { state ->
                _searchState.value = state
            }
        }
    }

    fun filterVideos(page: Int) {
        viewModelScope.launch {
            Log.d(TAG, "filterVideos page=$page")
            HentaiMamaNetworkRepo.filterVideos(
                page = page,
                genre = _selectedGenre.value,
                producer = _selectedProducer.value,
                order = _selectedOrder.value
            ).collect { state ->
                _searchState.value = state
            }
        }
    }

    fun setGenre(genre: String?) {
        Log.d(TAG, "setGenre: $genre")
        _selectedGenre.value = genre
    }

    fun setProducer(producer: String?) {
        Log.d(TAG, "setProducer: $producer")
        _selectedProducer.value = producer
    }

    fun setOrder(order: String?) {
        Log.d(TAG, "setOrder: $order")
        _selectedOrder.value = order
    }

    fun clearFilters() {
        Log.d(TAG, "clearFilters")
        _selectedGenre.value = null
        _selectedProducer.value = null
        _selectedOrder.value = null
    }

    fun getVideoDetail(path: String) {
        viewModelScope.launch {
            Log.d(TAG, "getVideoDetail: $path")
            HentaiMamaNetworkRepo.getVideoDetail(path).collect { state ->
                _videoState.value = state
            }
        }
    }
}