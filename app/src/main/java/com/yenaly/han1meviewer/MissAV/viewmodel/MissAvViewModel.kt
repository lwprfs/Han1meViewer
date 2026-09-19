package com.yenaly.han1meviewer.MissAV.viewmodel
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.yenaly.han1meviewer.MissAV.data.model.MissAvHomePage
import com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetworkRepo
import com.yenaly.han1meviewer.MissAV.common.MissAvOptions
import com.yenaly.han1meviewer.MissAV.data.model.MissAvVideoInfo
class MissAvViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MissAvViewModel"
    }

    private val _homePageFlow =
        MutableStateFlow<WebsiteState<MissAvHomePage>>(WebsiteState.Loading)
    val homePageFlow = _homePageFlow.asStateFlow()

    private val _popularFlow =
        MutableStateFlow<PageLoadingState<MissAvHomePage>>(PageLoadingState.Loading)
    val popularFlow = _popularFlow.asStateFlow()

    private val _videoFlow =
        MutableStateFlow<VideoLoadingState<MissAvVideoInfo>>(VideoLoadingState.Loading)
    val videoFlow = _videoFlow.asStateFlow()

    private val _searchFlow =
        MutableStateFlow<PageLoadingState<MutableList<HanimeInfo>>?>(null)
    val searchFlow = _searchFlow.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HanimeInfo>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedSort = MutableStateFlow<String?>(null)
    val selectedSort = _selectedSort.asStateFlow()

    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage = _currentPage.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched = _hasSearched.asStateFlow()

    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages = _hasMorePages.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private var searchJob: Job? = null

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setSort(sort: String?) { _selectedSort.value = sort }

    fun setFilter(filter: String?) { _selectedFilter.value = filter }

    fun setGenre(genre: String?) { _selectedGenre.value = genre }

    fun resetSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _selectedSort.value = null
        _selectedFilter.value = null
        _selectedGenre.value = null
        _currentPage.value = 1
        _hasSearched.value = false
        _hasMorePages.value = true
        _isLoadingMore.value = false
        _searchResults.value = emptyList()
        _searchFlow.value = null
    }

    fun performSearch(resetPage: Boolean = true) {
        searchJob?.cancel()
        if (resetPage) {
            _currentPage.value = 1
            _searchResults.value = emptyList()
            _hasMorePages.value = true
        }
        _hasSearched.value = true
        _isLoadingMore.value = true

        val query = _searchQuery.value
        val isBrowsing = query.isBlank()
        val genreToUse = if (isBrowsing) {
            _selectedGenre.value ?: MissAvOptions.DEFAULT_GENRE_KEY
        } else {
            MissAvOptions.DEFAULT_GENRE_KEY
        }
        val page = _currentPage.value

        searchJob = viewModelScope.launch {
            runCatching {
                val flow = when {
                    genreToUse != MissAvOptions.DEFAULT_GENRE_KEY && query.isBlank() ->
                        MissAvNetworkRepo.getGenreVideos(genreToUse, page, _selectedSort.value, _selectedFilter.value)
                    query.isNotBlank() ->
                        MissAvNetworkRepo.searchVideos(query, page, _selectedSort.value, _selectedFilter.value)
                    else ->
                        MissAvNetworkRepo.getGenreVideos(genreToUse, page, _selectedSort.value, _selectedFilter.value)
                }
                flow.collect { state ->
                    if (!isActive) return@collect
                    _searchFlow.value = state
                    when (state) {
                        is PageLoadingState.Success -> {
                            val incoming = state.info
                            _searchResults.update { prev ->
                                if (resetPage) incoming
                                else (prev + incoming).distinctBy(HanimeInfo::videoCode)
                            }
                            if (incoming.isEmpty()) {
                                _hasMorePages.value = false
                                _searchFlow.value = PageLoadingState.NoMoreData
                            }
                            _isLoadingMore.value = false
                        }
                        is PageLoadingState.NoMoreData -> {
                            _hasMorePages.value = false
                            _isLoadingMore.value = false
                        }
                        is PageLoadingState.Error -> {
                            _isLoadingMore.value = false
                        }
                        is PageLoadingState.Loading -> Unit
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "performSearch failure", e)
                _searchFlow.value = PageLoadingState.Error(e)
                _isLoadingMore.value = false
            }
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !_hasMorePages.value) return
        if (_searchFlow.value is PageLoadingState.Loading) return
        _currentPage.value = _currentPage.value + 1
        performSearch(resetPage = false)
    }

    fun goToPage(page: Int) {
        if (_isLoadingMore.value) return
        _currentPage.value = page
        performSearch(resetPage = true)
    }

    fun getHomePage() {
        viewModelScope.launch {
            runCatching {
                _homePageFlow.value = WebsiteState.Loading
                MissAvNetworkRepo.getHomePage().collect { state ->
                    if (!isActive) return@collect
                    _homePageFlow.value = state
                }
            }.onFailure { e ->
                Log.e(TAG, "Home page exception", e)
                _homePageFlow.value = WebsiteState.Error(e)
            }
        }
    }

    fun getPopularVideos(page: Int = 1) {
        viewModelScope.launch {
            runCatching {
                _popularFlow.value = PageLoadingState.Loading
                MissAvNetworkRepo.getPopularVideos(page).collect { state ->
                    if (!isActive) return@collect
                    _popularFlow.value = state
                }
            }.onFailure { e ->
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
            runCatching {
                onResult(MissAvNetworkRepo.getGenreVideosSync(genrePath, page, sort, filter))
            }.onFailure { e ->
                Log.e(TAG, "Genre videos error for $genrePath", e)
                onResult(emptyList())
            }
        }
    }

    fun searchVideos(
        query: String,
        page: Int = 1,
        sort: String? = null,
        filter: String? = null,
    ) {
        setSearchQuery(query)
        setSort(sort)
        setFilter(filter)
        performSearch(resetPage = page == 1)
    }

    fun getVideoDetail(path: String) {
        viewModelScope.launch {
            runCatching {
                _videoFlow.value = VideoLoadingState.Loading
                MissAvNetworkRepo.getVideoDetail(path).collect { state ->
                    if (!isActive) return@collect
                    _videoFlow.value = state
                }
            }.onFailure { e ->
                Log.e(TAG, "Video detail exception", e)
                _videoFlow.value = VideoLoadingState.Error(e)
            }
        }
    }

    fun retryHomePage() = getHomePage()
    fun retryVideoDetail(path: String) = getVideoDetail(path)
}