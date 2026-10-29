package com.yenaly.han1meviewer.FapFolder.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupRow
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderProfileParser
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderFeedPaging
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderProfileTab(
    val label: String,
    val param: String,
    val url: String,
    val kind: Kind,
) {
    enum class Kind { POSTS, PHOTOS, VIDEOS, GROUPS, FRIENDS, OTHER }

    companion object {
        fun fromParam(value: String?): String {
            val v = value?.lowercase().orEmpty()
            return when {
                v.contains("photo") -> "photos"
                v.contains("video") -> "videos"
                v.contains("group") || v.contains("communit") -> "groups"
                v.contains("friend") -> "friends"
                else -> "posts"
            }
        }

        fun kindOf(param: String): Kind = when (param) {
            "photos" -> Kind.PHOTOS
            "videos" -> Kind.VIDEOS
            "groups" -> Kind.GROUPS
            "friends" -> Kind.FRIENDS
            "posts" -> Kind.POSTS
            else -> Kind.OTHER
        }

        val DEFAULT_LABELS = listOf(
            "Posts" to "posts",
            "Photos" to "photos",
            "Videos" to "videos",
            "Groups" to "groups",
            "Friends" to "friends",
        )
    }
}

data class FapFolderProfileUiState(
    val username: String = "",
    val activeTab: String = "posts",
    val tabs: List<FapFolderProfileTab> = emptyList(),
    val profile: FapFolderUserProfile? = null,
    val isLoading: Boolean = true,
    val headerError: String? = null,
    val sessionExpired: Boolean = false,
    val items: List<FapFolderFeedItem> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val itemsError: String? = null,
    val following: Boolean = false,
    val followPending: Boolean = false,
    val followError: String? = null,
    val streamGet: String? = null,
    val streamFilter: String? = null,
    val streamId: String? = null,
    val streamType: String? = null,
    val streamUid: String? = null,
    val streamSearch: String? = null,
    val streamHashtag: String? = null,
    val currentOffset: Int = 1,
) {
    val posts: List<FapFolderPost>
        get() = items.filterIsInstance<FapFolderFeedItem.Post>().map { it.post }

    val users: List<FapFolderUserRow>
        get() = items.filterIsInstance<FapFolderFeedItem.User>().map { it.user }

    val groups: List<FapFolderGroupRow>
        get() = items.filterIsInstance<FapFolderFeedItem.Community>().map { it.group }
}

class FapFolderProfileViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderProfileVM"
        private const val MAX_PAGES = 50
    }

    private val _uiState = MutableStateFlow(FapFolderProfileUiState())
    val uiState = _uiState.asStateFlow()

    private var headerJob: Job? = null
    private var itemsJob: Job? = null
    private var followJob: Job? = null

    private val seenKeys = mutableSetOf<String>()

    fun load(username: String, initialTab: String?) {
        val clean = username.trim('/')
        if (clean.isBlank()) return
        val tab = FapFolderProfileTab.fromParam(initialTab)
        val sameLoad = _uiState.value.username == clean &&
                _uiState.value.activeTab == tab &&
                _uiState.value.profile != null
        if (sameLoad) return

        headerJob?.cancel()
        itemsJob?.cancel()
        followJob?.cancel()
        seenKeys.clear()

        _uiState.update {
            it.copy(
                username = clean,
                activeTab = tab,
                profile = null,
                isLoading = true,
                headerError = null,
                sessionExpired = false,
                items = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
                following = false,
                followPending = false,
                followError = null,
                tabs = defaultTabs(),
            )
        }

        headerJob = viewModelScope.launch {
            FapFolderNetworkRepo.getProfilePage(clean).collect { state ->
                when (state) {
                    is VideoLoadingState.Loading -> Unit
                    is VideoLoadingState.Success -> {
                        val profile = state.info
                        _uiState.update {
                            it.copy(
                                profile = profile,
                                isLoading = false,
                                headerError = null,
                                following = profile.isFollowed,
                            )
                        }
                        loadFirstItems(tab)
                    }
                    is VideoLoadingState.Error -> {
                        val msg = state.throwable.message
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                headerError = msg ?: "Failed to load profile.",
                                sessionExpired = msg?.contains("session", true) == true,
                            )
                        }
                    }
                    is VideoLoadingState.NoContent -> _uiState.update {
                        it.copy(isLoading = false, headerError = "Profile not found.")
                    }
                }
            }
        }
    }

    fun switchTab(param: String) {
        val normalised = FapFolderProfileTab.fromParam(param)
        if (_uiState.value.activeTab == normalised) return
        _uiState.update {
            it.copy(
                activeTab = normalised,
                items = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
            )
        }
        seenKeys.clear()
        loadFirstItems(normalised)
    }

    fun refresh() {
        val username = _uiState.value.username
        if (username.isBlank()) return
        _uiState.update { it.copy(username = "") }
        load(username, _uiState.value.activeTab)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (state.currentOffset >= MAX_PAGES) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }

        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, itemsError = null) }

            val request = buildStreamRequest(state, state.currentOffset)
            FapFolderNetworkRepo.loadMore(request).collect { page ->
                when (page) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val fresh = page.info.filter { item ->
                            seenKeys.add(item.stableKey)
                        }
                        _uiState.update {
                            it.copy(
                                items = it.items + fresh,
                                isLoadingMore = false,
                                hasMore = page.info.isNotEmpty(),
                                itemsError = null,
                                currentOffset = it.currentOffset + 1,
                            )
                        }
                    }
                    is PageLoadingState.NoMoreData -> _uiState.update {
                        it.copy(isLoadingMore = false, hasMore = false)
                    }
                    is PageLoadingState.Error -> {
                        val msg = page.throwable.message
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                itemsError = msg,
                                hasMore = false,
                                sessionExpired = msg?.contains("session", true) == true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleFollow() {
        val state = _uiState.value
        val profile = state.profile ?: return
        val uid = profile.uid ?: return
        if (state.followPending) return

        val wasFollowing = state.following
        _uiState.update {
            it.copy(
                following = !wasFollowing,
                followPending = true,
                followError = null,
            )
        }

        followJob?.cancel()
        followJob = viewModelScope.launch {
            val flow = if (wasFollowing) {
                FapFolderNetworkRepo.unfollowUser(uid)
            } else {
                FapFolderNetworkRepo.followUser(uid)
            }
            flow.collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> _uiState.update {
                        it.copy(followPending = false)
                    }
                    is WebsiteState.Error -> _uiState.update {
                        it.copy(
                            following = wasFollowing,
                            followPending = false,
                            followError = result.throwable.message
                                ?: "Failed to update follow state.",
                        )
                    }
                }
            }
        }
    }

    private fun loadFirstItems(tab: String) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val username = state.username
        if (username.isBlank()) return

        val tabUrl = when (tab) {
            "photos" -> FapFolderNetwork.baseUrl.trimEnd('/') + "/$username/photos"
            "videos" -> FapFolderNetwork.baseUrl.trimEnd('/') + "/$username/videos"
            "groups" -> FapFolderNetwork.baseUrl.trimEnd('/') + "/$username/groups"
            "friends" -> FapFolderNetwork.baseUrl.trimEnd('/') + "/$username/friends"
            else -> FapFolderNetwork.baseUrl.trimEnd('/') + "/$username"
        }

        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, itemsError = null) }

            try {
                val response = FapFolderNetwork.service.getProfileUrl(tabUrl)
                val rawBody = response.body()?.string().orEmpty()
                if (!response.isSuccessful || rawBody.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            itemsError = "HTTP ${response.code()}",
                        )
                    }
                    return@launch
                }

                val snapshot = FapFolderNetworkRepo.parseFirstPageFromHtml(
                    html = rawBody,
                    pageUrl = tabUrl,
                    filter = tab.takeIf { it != "posts" },
                    fallbackId = profile.uid?.toString(),
                )

                _uiState.update {
                    it.copy(
                        items = snapshot.items,
                        isLoading = false,
                        hasMore = snapshot.hasMore,
                        currentOffset = 2,
                        streamGet = snapshot.get,
                        streamFilter = snapshot.filter,
                        streamId = snapshot.id,
                        streamType = snapshot.type,
                        streamUid = snapshot.uid,
                        streamSearch = snapshot.search,
                        streamHashtag = snapshot.hashtag,
                    )
                }
                snapshot.items.forEach { seenKeys.add(it.stableKey) }
            } catch (e: Exception) {
                Log.e(TAG, "loadFirstItems($tab) failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        itemsError = e.message ?: "Failed to load.",
                    )
                }
            }
        }
    }

    private fun buildStreamRequest(
        state: FapFolderProfileUiState,
        offset: Int,
    ): FapFolderLoadMoreRequest {
        val fallbackFilter = when (state.activeTab) {
            "photos" -> "photos"
            "videos" -> "videos"
            else -> FapFolderConstants.FILTER_ALL
        }
        val fallbackGet = when (state.activeTab) {
            "groups" -> "profile_groups"
            "friends" -> "friends"
            else -> FapFolderConstants.FEED_GET_POSTS_PROFILE
        }
        val id = state.streamId ?: state.profile?.uid?.toString()

        return FapFolderFeedPaging.fromStream(
            get = state.streamGet ?: fallbackGet,
            filter = state.streamFilter ?: fallbackFilter,
            id = id,
            type = state.streamType,
            uid = state.streamUid ?: id,
            search = state.streamSearch,
            hashtag = state.streamHashtag,
            offset = offset,
            premfeed = FapFolderConstants.PREMFEED_OFF,
            time = FapFolderConstants.TIME_NO_FILTER,
        )
    }

    private fun defaultTabs(): List<FapFolderProfileTab> =
        FapFolderProfileTab.DEFAULT_LABELS.map { (label, param) ->
            FapFolderProfileTab(
                label = label,
                param = param,
                url = "",
                kind = FapFolderProfileTab.kindOf(param),
            )
        }

    override fun onCleared() {
        super.onCleared()
        headerJob?.cancel()
        itemsJob?.cancel()
        followJob?.cancel()
    }
}