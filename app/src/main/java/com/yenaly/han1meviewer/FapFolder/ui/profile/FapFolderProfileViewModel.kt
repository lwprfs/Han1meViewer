package com.yenaly.han1meviewer.FapFolder.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAuthor
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupRow
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.model.PostVariant
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderHtmlUtils
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderProfileParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderPostParser
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
import org.jsoup.Jsoup

enum class FapFolderProfileTab(val label: String, val param: String) {
    TIMELINE("Timeline", "posts"),
    PHOTOS("Gallery", "photos"),
    VIDEOS("Videos", "videos");

    companion object {
        fun fromParam(value: String?): FapFolderProfileTab {
            val v = value?.lowercase().orEmpty()
            return when {
                v.contains("photo") || v.contains("gallery") -> PHOTOS
                v.contains("video") -> VIDEOS
                else -> TIMELINE
            }
        }
    }
}

data class FapFolderProfileCounts(
    val followers: Int?,
    val posts: Int?,
    val photos: Int?,
    val videos: Int?,
) {
    val isEmpty: Boolean
        get() = followers == null && posts == null && photos == null && videos == null
}

fun FapFolderUserProfile.counts(): FapFolderProfileCounts = FapFolderProfileCounts(
    followers = followerCount,
    posts = postCount,
    photos = photoCount,
    videos = videoCount,
)

data class FapFolderProfileUiState(
    val username: String = "",
    val activeTab: FapFolderProfileTab = FapFolderProfileTab.TIMELINE,
    val tabs: List<FapFolderProfileTab> = listOf(
        FapFolderProfileTab.TIMELINE,
        FapFolderProfileTab.PHOTOS,
        FapFolderProfileTab.VIDEOS,
    ),
    val profile: FapFolderUserProfile? = null,
    val isLoading: Boolean = true,
    val headerError: String? = null,
    val sessionExpired: Boolean = false,
    val items: List<FapFolderFeedItem> = emptyList(),
    val photoTiles: List<FapFolderProfileParser.PhotoTile> = emptyList(),
    val videoTiles: List<FapFolderProfileParser.VideoTile> = emptyList(),
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

    val mediaImages: List<FapFolderMedia.Image>
        get() = FapFolderProfileParser.toMediaImages(photoTiles)
}

class FapFolderProfileViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderProfileVM"
        private const val MAX_PAGES = 100
    }

    private val _uiState = MutableStateFlow(FapFolderProfileUiState())
    val uiState = _uiState.asStateFlow()

    private var headerJob: Job? = null
    private var itemsJob: Job? = null
    private var followJob: Job? = null

    private val seenKeys = mutableSetOf<String>()
    private val seenPhotoIds = mutableSetOf<Long?>()
    private val seenPhotoUrls = mutableSetOf<String>()
    private val seenVideoPostIds = mutableSetOf<Long?>()
    private val seenVideoUrls = mutableSetOf<String>()

    fun load(username: String, initialTab: String?) {
        val clean = username.trim('/')
        if (clean.isBlank()) return
        val tab = FapFolderProfileTab.fromParam(initialTab)
        val sameLoad = _uiState.value.username == clean &&
                _uiState.value.profile != null
        if (sameLoad) {
            if (_uiState.value.activeTab != tab) {
                _uiState.update { it.copy(activeTab = tab) }
                loadTabItems(tab)
            }
            return
        }

        headerJob?.cancel()
        itemsJob?.cancel()
        followJob?.cancel()
        seenKeys.clear()
        seenPhotoIds.clear()
        seenPhotoUrls.clear()
        seenVideoPostIds.clear()
        seenVideoUrls.clear()

        _uiState.update {
            it.copy(
                username = clean,
                activeTab = tab,
                profile = null,
                isLoading = true,
                headerError = null,
                sessionExpired = false,
                items = emptyList(),
                photoTiles = emptyList(),
                videoTiles = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
                following = false,
                followPending = false,
                followError = null,
                tabs = listOf(
                    FapFolderProfileTab.TIMELINE,
                    FapFolderProfileTab.PHOTOS,
                    FapFolderProfileTab.VIDEOS,
                ),
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
                        loadTabItems(tab)
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
                photoTiles = emptyList(),
                videoTiles = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
            )
        }
        seenKeys.clear()
        seenPhotoIds.clear()
        seenPhotoUrls.clear()
        seenVideoPostIds.clear()
        seenVideoUrls.clear()
        loadTabItems(normalised)
    }

    fun refresh() {
        val username = _uiState.value.username
        if (username.isBlank()) return
        val currentTab = _uiState.value.activeTab
        headerJob?.cancel()
        itemsJob?.cancel()
        followJob?.cancel()
        seenKeys.clear()
        seenPhotoIds.clear()
        seenPhotoUrls.clear()
        seenVideoPostIds.clear()
        seenVideoUrls.clear()
        _uiState.update { it.copy(profile = null, isLoading = true, headerError = null) }
        load(username, currentTab.param)
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
                        when (state.activeTab) {
                            FapFolderProfileTab.TIMELINE -> appendPostItems(page.info)
                            FapFolderProfileTab.PHOTOS -> appendPhotoItems(page.info)
                            FapFolderProfileTab.VIDEOS -> appendVideoItems(page.info)
                        }
                        _uiState.update {
                            it.copy(
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
        val uid = profile.followUid ?: profile.uid ?: return
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

    private fun loadTabItems(tab: FapFolderProfileTab) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val username = state.username
        if (username.isBlank()) return

        val base = FapFolderNetwork.baseUrl.trimEnd('/')
        val isGroupProfile = !profile.groupSlug.isNullOrBlank()
        val tabUrl = profile.tabUrls[tab.param] ?: when (tab) {
            FapFolderProfileTab.PHOTOS -> if (isGroupProfile) {
                "$base/groups/${profile.groupSlug}/photos"
            } else "$base/$username/photos"
            FapFolderProfileTab.VIDEOS -> if (isGroupProfile) {
                "$base/groups/${profile.groupSlug}/videos"
            } else "$base/$username/videos"
            FapFolderProfileTab.TIMELINE -> if (isGroupProfile) {
                "$base/groups/${profile.groupSlug}"
            } else "$base/$username"
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
                            hasMore = false,
                        )
                    }
                    return@launch
                }

                when (tab) {
                    FapFolderProfileTab.PHOTOS -> applyPhotoPage(rawBody, tabUrl)
                    FapFolderProfileTab.VIDEOS -> applyVideoPage(rawBody, tabUrl)
                    FapFolderProfileTab.TIMELINE -> applyTimelinePage(rawBody, tabUrl, profile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadTabItems($tab) failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        itemsError = e.message ?: "Failed to load.",
                    )
                }
            }
        }
    }

    private fun applyTimelinePage(
        html: String,
        tabUrl: String,
        profile: FapFolderUserProfile,
    ) {
        val snapshot = FapFolderNetworkRepo.parseFirstPageFromHtml(
            html = html,
            pageUrl = tabUrl,
            filter = null,
            fallbackId = profile.uid?.toString(),
        )
        val items = snapshot.items.ifEmpty {
            fallbackPostsFromTimeline(html, tabUrl, profile)
        }

        seenKeys.clear()
        items.forEach { seenKeys.add(it.stableKey) }

        _uiState.update {
            it.copy(
                items = items,
                isLoading = false,
                hasMore = snapshot.hasMore && items.isNotEmpty(),
                currentOffset = 2,
                streamGet = snapshot.get
                    ?: FapFolderProfileParser.FALLBACK_GET_POSTS_GROUP,
                streamFilter = snapshot.filter ?: FapFolderConstants.FILTER_ALL,
                streamId = snapshot.id ?: profile.uid?.toString(),
                streamType = snapshot.type,
                streamUid = snapshot.uid,
                streamSearch = snapshot.search,
                streamHashtag = snapshot.hashtag,
                itemsError = null,
            )
        }
    }

    private fun applyPhotoPage(html: String, tabUrl: String) {
        val snapshot = FapFolderProfileParser.parseGalleryPage(html, tabUrl)
            ?: FapFolderProfileParser.GallerySnapshot(
                photos = emptyList(),
                hasMore = false,
                streamGet = FapFolderProfileParser.FALLBACK_GET_PHOTOS,
                streamId = null,
                streamType = "group",
            )

        val fresh = snapshot.photos.filter { tile ->
            val id = tile.photoId
            val url = tile.fullUrl
            val idTaken = id != null && seenPhotoIds.contains(id)
            val urlTaken = seenPhotoUrls.contains(url)
            if (!idTaken && !urlTaken) {
                seenPhotoIds.add(id)
                seenPhotoUrls.add(url)
                true
            } else false
        }

        _uiState.update {
            it.copy(
                photoTiles = fresh,
                isLoading = false,
                hasMore = snapshot.hasMore && fresh.isNotEmpty(),
                currentOffset = 2,
                streamGet = snapshot.streamGet
                    ?: FapFolderProfileParser.FALLBACK_GET_PHOTOS,
                streamId = snapshot.streamId ?: _uiState.value.profile?.uid?.toString(),
                streamType = snapshot.streamType ?: "group",
                streamFilter = null,
                itemsError = null,
            )
        }
    }

    private fun applyVideoPage(html: String, tabUrl: String) {
        val snapshot = FapFolderProfileParser.parseVideosPage(html, tabUrl)
            ?: FapFolderProfileParser.VideosSnapshot(
                videos = emptyList(),
                hasMore = false,
                streamGet = FapFolderProfileParser.FALLBACK_GET_VIDEOS,
                streamId = null,
                streamType = "group",
            )

        val fresh = snapshot.videos.filter { tile ->
            val id = tile.postId
            val url = tile.postUrl
            val idTaken = id != null && seenVideoPostIds.contains(id)
            val urlTaken = seenVideoUrls.contains(url)
            if (!idTaken && !urlTaken) {
                seenVideoPostIds.add(id)
                seenVideoUrls.add(url)
                true
            } else false
        }

        _uiState.update {
            it.copy(
                videoTiles = fresh,
                isLoading = false,
                hasMore = snapshot.hasMore && fresh.isNotEmpty(),
                currentOffset = 2,
                streamGet = snapshot.streamGet
                    ?: FapFolderProfileParser.FALLBACK_GET_VIDEOS,
                streamId = snapshot.streamId ?: _uiState.value.profile?.uid?.toString(),
                streamType = snapshot.streamType ?: "group",
                streamFilter = null,
                itemsError = null,
            )
        }
    }

    private fun appendPostItems(newItems: List<FapFolderFeedItem>) {
        val fresh = newItems.filter { seenKeys.add(it.stableKey) }
        _uiState.update { it.copy(items = it.items + fresh) }
    }

    private fun appendPhotoItems(newItems: List<FapFolderFeedItem>) {
        val tiles = newItems.mapNotNull { item ->
            (item as? FapFolderFeedItem.Post)?.post?.primaryImage
        }.map { image ->
            FapFolderProfileParser.PhotoTile(
                photoId = image.photoId,
                fullUrl = image.fullUrl,
                previewUrl = image.previewUrl,
                pageUrl = image.photoId?.let {
                    "${FapFolderNetwork.baseUrl.trimEnd('/')}/photos/$it"
                },
                alt = image.alt,
            )
        }
        val fresh = tiles.filter { tile ->
            val id = tile.photoId
            val url = tile.fullUrl
            val idTaken = id != null && seenPhotoIds.contains(id)
            val urlTaken = seenPhotoUrls.contains(url)
            if (!idTaken && !urlTaken) {
                seenPhotoIds.add(id)
                seenPhotoUrls.add(url)
                true
            } else false
        }
        _uiState.update { it.copy(photoTiles = it.photoTiles + fresh) }
    }

    private fun appendVideoItems(newItems: List<FapFolderFeedItem>) {
        val tiles = newItems.filterIsInstance<FapFolderFeedItem.Post>().map { item ->
            val post = item.post
            FapFolderProfileParser.VideoTile(
                postId = post.postId,
                postUrl = post.permalink ?: "${FapFolderNetwork.baseUrl.trimEnd('/')}/posts/${post.postId}",
                thumbnailUrl = post.primaryVideo?.posterUrl
                    ?: post.primaryImage?.fullUrl,
                title = post.bodyPlain.take(80).ifBlank { null },
            )
        }
        val fresh = tiles.filter { tile ->
            val id = tile.postId
            val url = tile.postUrl
            val idTaken = id != null && seenVideoPostIds.contains(id)
            val urlTaken = seenVideoUrls.contains(url)
            if (!idTaken && !urlTaken) {
                seenVideoPostIds.add(id)
                seenVideoUrls.add(url)
                true
            } else false
        }
        _uiState.update { it.copy(videoTiles = it.videoTiles + fresh) }
    }

    private fun fallbackPostsFromTimeline(
        html: String,
        tabUrl: String,
        profile: FapFolderUserProfile,
    ): List<FapFolderFeedItem> {
        val doc = runCatching { Jsoup.parse(html, tabUrl) }.getOrNull()
            ?: return emptyList()
        val streamRoot = doc.selectFirst("div.js_posts_stream") ?: doc.body()

        val snapshot = FapFolderNetworkRepo.parseFirstPageFromHtml(
            html = html,
            pageUrl = tabUrl,
            filter = null,
            fallbackId = profile.uid?.toString(),
        )
        if (snapshot.items.isNotEmpty()) return snapshot.items

        return FapFolderPostParser
            .parsePostsFromList(streamRoot, tabUrl, null, includeComments = false)
            .map { FapFolderFeedItem.Post(it) }
    }

    private fun buildStreamRequest(
        state: FapFolderProfileUiState,
        offset: Int,
    ): FapFolderLoadMoreRequest {
        val id = state.streamId
            ?: state.profile?.uid?.toString()
            ?: state.profile?.groupSlug

        return when (state.activeTab) {
            FapFolderProfileTab.TIMELINE -> FapFolderFeedPaging.fromStream(
                get = state.streamGet
                    ?: FapFolderProfileParser.FALLBACK_GET_POSTS_GROUP,
                filter = state.streamFilter ?: FapFolderConstants.FILTER_ALL,
                id = id,
                type = state.streamType,
                uid = state.streamUid,
                search = state.streamSearch,
                hashtag = state.streamHashtag,
                offset = offset,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = FapFolderConstants.TIME_NO_FILTER,
            )
            FapFolderProfileTab.PHOTOS -> FapFolderLoadMoreRequest(
                get = state.streamGet
                    ?: FapFolderProfileParser.FALLBACK_GET_PHOTOS,
                offset = offset,
                filter = null,
                id = id,
                type = state.streamType ?: "group",
                uid = null,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = FapFolderConstants.TIME_NO_FILTER,
                search = null,
                hashtag = null,
            )
            FapFolderProfileTab.VIDEOS -> FapFolderLoadMoreRequest(
                get = state.streamGet
                    ?: FapFolderProfileParser.FALLBACK_GET_VIDEOS,
                offset = offset,
                filter = null,
                id = id,
                type = state.streamType ?: "group",
                uid = null,
                premfeed = FapFolderConstants.PREMFEED_OFF,
                time = FapFolderConstants.TIME_NO_FILTER,
                search = null,
                hashtag = null,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        headerJob?.cancel()
        itemsJob?.cancel()
        followJob?.cancel()
    }
}
