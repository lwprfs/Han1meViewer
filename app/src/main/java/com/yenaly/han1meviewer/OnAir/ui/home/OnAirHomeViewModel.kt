package com.yenaly.han1meviewer.OnAir.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.OnAir.data.local.OnAirProfileCache
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPageInfo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirUserProfile
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OnAirHomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OnAirHomeViewModel"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private val _homeState = MutableStateFlow<WebsiteState<OnAirHomeUiData>>(WebsiteState.Loading)
    val homeState = _homeState.asStateFlow()

    private val _cards = MutableStateFlow<List<OnAirVideoCard>>(emptyList())
    val cards = _cards.asStateFlow()

    private val _pageInfo = MutableStateFlow<OnAirPageInfo?>(null)
    val pageInfo = _pageInfo.asStateFlow()

    private val _loadedPage = MutableStateFlow(1)
    val loadedPage = _loadedPage.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _currentUser = MutableStateFlow<OnAirUserProfile?>(null)
    val currentUser = _currentUser.asStateFlow()

    private var lastFetchAt = 0L
    private var currentPage = 1
    private var hasMore = true

    private var currentQuery: String? = null
    private var currentField: String? = null
    private var currentLength: String? = null
    private var currentDate: String? = null
    private var currentSort: String? = null

    private var profileJob: Job? = null

    fun loadHomePage(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchAt < CACHE_TTL_MS &&
            _homeState.value is WebsiteState.Success
        ) return

        viewModelScope.launch {
            OnAirNetworkRepo.getHomePage(
                page = 1,
                query = currentQuery,
                field = currentField,
                length = currentLength,
                date = currentDate,
                sort = currentSort,
            ).collect { state ->
                if (!isActive) return@collect
                when (state) {
                    is WebsiteState.Loading -> {
                        if (_cards.value.isEmpty()) _homeState.value = WebsiteState.Loading
                    }

                    is WebsiteState.Success -> {
                        lastFetchAt = System.currentTimeMillis()
                        currentPage = 1
                        hasMore = true
                        _loadedPage.value = 1
                        val page = state.info
                        _cards.value = page.videos
                        _pageInfo.value = page.pageInfo
                        if (page.pageInfo == null) {
                            hasMore = page.videos.isNotEmpty()
                        }

                        page.currentUsername?.let { name ->
                            OnAirProfileCache.setCurrentUsername(name)
                            val cached = OnAirProfileCache.get(name)
                            if (cached != null) {
                                _currentUser.value = cached
                            } else {
                                refreshCurrentUser()
                            }
                        }

                        _homeState.value = WebsiteState.Success(
                            OnAirHomeUiData(
                                videos = page.videos,
                                totalVideos = page.totalVideos,
                                totalPictures = page.totalPictures,
                                totalMembers = page.totalMembers,
                                announcementTitle = page.announcement?.title,
                                announcementUrl = page.announcement?.url,
                            )
                        )
                    }

                    is WebsiteState.Error -> {
                        Log.e(TAG, "Home load failed", state.throwable)
                        _homeState.value = WebsiteState.Error(state.throwable)
                    }
                }
            }
        }
    }

    fun refresh() {
        lastFetchAt = 0L
        OnAirProfileCache.invalidate()
        _currentUser.value = null
        loadHomePage(force = true)
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !hasMore) return
        if (currentPage >= (_pageInfo.value?.total ?: Int.MAX_VALUE)) {
            hasMore = false
            return
        }
        val nextPage = currentPage + 1

        viewModelScope.launch {
            _isLoadingMore.value = true
            OnAirNetworkRepo.getHomePagePaged(
                page = nextPage,
                query = currentQuery,
                field = currentField,
                length = currentLength,
                date = currentDate,
                sort = currentSort,
            ).collect { state ->
                if (!isActive) return@collect
                when (state) {
                    is PageLoadingState.Success -> {
                        val pair = state.info as Pair<List<OnAirVideoCard>, OnAirPageInfo?>
                        val newCards = pair.first
                        _cards.update { prev ->
                            (prev + newCards).distinctBy { it.videoId }
                        }
                        pair.second?.let { _pageInfo.value = it }
                        currentPage = nextPage
                        _loadedPage.value = nextPage
                        _isLoadingMore.value = false
                        if (newCards.isEmpty()) hasMore = false
                        _homeState.update { s ->
                            if (s is WebsiteState.Success) {
                                s.copy(info = s.info.copy(videos = _cards.value))
                            } else s
                        }
                    }

                    is PageLoadingState.NoMoreData -> {
                        _isLoadingMore.value = false
                        hasMore = false
                    }

                    is PageLoadingState.Error -> {
                        _isLoadingMore.value = false
                    }

                    is PageLoadingState.Loading -> Unit
                }
            }
        }
    }

    fun applyHomeFilters(
        query: String? = null,
        field: String? = null,
        length: String? = null,
        date: String? = null,
        sort: String? = null,
    ) {
        currentQuery = query
        currentField = field
        currentLength = length
        currentDate = date
        currentSort = sort
        currentPage = 1
        hasMore = true
        lastFetchAt = 0L
        _cards.value = emptyList()
        _pageInfo.value = null
        _loadedPage.value = 1
        loadHomePage(force = true)
    }

    fun currentUsername(): String? =
        _currentUser.value?.username ?: OnAirProfileCache.getCurrentUsername()

    fun refreshCurrentUser(force: Boolean = false) {
        val name = OnAirProfileCache.getCurrentUsername() ?: return
        if (!force) {
            val cached = OnAirProfileCache.get(name)
            if (cached != null) {
                _currentUser.value = cached
                return
            }
        }
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            OnAirNetworkRepo.getUserProfile(name).collect { state ->
                when (state) {
                    is VideoLoadingState.Success -> {
                        val profile = state.info
                        _currentUser.value = profile
                        OnAirProfileCache.put(name, profile)
                    }

                    is VideoLoadingState.Error -> {
                        Log.w(TAG, "refreshCurrentUser failed", state.throwable)
                    }

                    else -> Unit
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        profileJob?.cancel()
    }
}

data class OnAirHomeUiData(
    val videos: List<OnAirVideoCard>,
    val totalVideos: Int?,
    val totalPictures: Int?,
    val totalMembers: Int?,
    val announcementTitle: String?,
    val announcementUrl: String?,
)
