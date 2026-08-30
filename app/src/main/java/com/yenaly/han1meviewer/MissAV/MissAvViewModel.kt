package com.yenaly.han1meviewer.MissAV

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
import kotlinx.coroutines.isActive

class MissAvViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MissAvViewModel"
        private const val MAX_RETRY_COUNT = 2
    }

    private val _homePageFlow = MutableStateFlow<WebsiteState<MissAvHomePage>>(WebsiteState.Loading)
    val homePageFlow = _homePageFlow.asStateFlow()

    private val _popularFlow = MutableStateFlow<PageLoadingState<MissAvHomePage>>(PageLoadingState.Loading)
    val popularFlow = _popularFlow.asStateFlow()

    private val _searchFlow = MutableStateFlow<PageLoadingState<MutableList<HanimeInfo>>>(PageLoadingState.Loading)
    val searchFlow = _searchFlow.asStateFlow()

    private val _videoFlow = MutableStateFlow<VideoLoadingState<MissAvVideoInfo>>(VideoLoadingState.Loading)
    val videoFlow = _videoFlow.asStateFlow()

    private var retryCount = 0

    fun getHomePage() {
        viewModelScope.launch {
            try {
                _homePageFlow.value = WebsiteState.Loading
                MissAvNetworkRepo.getHomePage().collect { state ->
                    if (!isActive) return@collect
                    if (state is WebsiteState.Error) {
                        Log.e(TAG, "Home page error: ${state.throwable.message}")
                        if (state.throwable is java.net.SocketTimeoutException) {
                            _homePageFlow.value = WebsiteState.Error(
                                IllegalStateException("Network timeout. The site may be under heavy load.")
                            )
                        } else {
                            _homePageFlow.value = state
                        }
                    } else {
                        retryCount = 0
                        _homePageFlow.value = state
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Home page exception", e)
                _homePageFlow.value = WebsiteState.Error(e)
            }
        }
    }

    fun getPopularVideos(page: Int = 1) {
        viewModelScope.launch {
            try {
                _popularFlow.value = PageLoadingState.Loading
                MissAvNetworkRepo.getPopularVideos(page).collect { state ->
                    if (!isActive) return@collect
                    if (state is PageLoadingState.Error) {
                        Log.e(TAG, "Popular videos error: ${state.throwable.message}")
                    }
                    _popularFlow.value = state
                }
            } catch (e: Exception) {
                Log.e(TAG, "Popular videos exception", e)
                _popularFlow.value = PageLoadingState.Error(e)
            }
        }
    }

    fun getGenreVideos(
        genrePath: String,
        page: Int = 1,
        sort: String? = null,
        filter: String? = null,
        onResult: (List<HanimeInfo>) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val videos = MissAvNetworkRepo.getGenreVideosSync(genrePath, page, sort, filter)
                onResult(videos)
            } catch (e: Exception) {
                Log.e(TAG, "Genre videos error", e)
                onResult(emptyList())
            }
        }
    }

    fun searchVideos(query: String, page: Int = 1, sort: String? = null, filter: String? = null) {
        viewModelScope.launch {
            try {
                MissAvNetworkRepo.searchVideos(query, page, sort, filter).collect { state ->
                    if (!isActive) return@collect
                    if (state is PageLoadingState.Error) {
                        Log.e(TAG, "Search error: ${state.throwable.message}")
                    }
                    _searchFlow.value = state
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search exception", e)
                _searchFlow.value = PageLoadingState.Error(e)
            }
        }
    }

    fun searchVideosWithGenre(
        query: String,
        page: Int = 1,
        sort: String? = null,
        genre: String? = null,
        filter: String? = null,
    ) {
        viewModelScope.launch {
            try {
                if (genre != null && query.isBlank()) {
                    MissAvNetworkRepo.getGenreVideos(genre, page, sort, filter).collect { state ->
                        if (!isActive) return@collect
                        _searchFlow.value = state
                    }
                } else if (query.isNotBlank()) {
                    MissAvNetworkRepo.searchVideos(query, page, sort, filter).collect { state ->
                        if (!isActive) return@collect
                        _searchFlow.value = state
                    }
                } else {
                    MissAvNetworkRepo.getGenreVideos(genre ?: "en/release", page, sort, filter).collect { state ->
                        if (!isActive) return@collect
                        _searchFlow.value = state
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search with genre error", e)
                _searchFlow.value = PageLoadingState.Error(e)
            }
        }
    }

    fun getVideoDetail(path: String) {
        viewModelScope.launch {
            try {
                _videoFlow.value = VideoLoadingState.Loading
                MissAvNetworkRepo.getVideoDetail(path).collect { state ->
                    if (!isActive) return@collect
                    if (state is VideoLoadingState.Error) {
                        Log.e(TAG, "Video detail error: ${state.throwable.message}")
                    }
                    _videoFlow.value = state
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video detail exception", e)
                _videoFlow.value = VideoLoadingState.Error(e)
            }
        }
    }

    fun retryHomePage() {
        retryCount = 0
        getHomePage()
    }

    fun retryVideoDetail(path: String) {
        getVideoDetail(path)
    }
}