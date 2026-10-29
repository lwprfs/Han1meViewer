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
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderProfileTabStream
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.model.PostVariant
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderHtmlUtils
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
import org.jsoup.Jsoup

enum class FapFolderProfileTab(val label: String, val param: String) {
    TIMELINE("Timeline", "posts"),
    PHOTOS("Photos", "photos"),
    VIDEOS("Videos", "videos");

    companion object {
        fun fromParam(value: String?): FapFolderProfileTab {
            val v = value?.lowercase().orEmpty()
            return when {
                v.contains("photo") -> PHOTOS
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
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
            )
        }
        seenKeys.clear()
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
        val tabUrl = profile.tabUrls[tab.param]
            ?: if (isGroupProfile) {
                val slug = profile.groupSlug
                when (tab) {
                    FapFolderProfileTab.PHOTOS -> "$base/groups/$slug/photos"
                    FapFolderProfileTab.VIDEOS -> "$base/groups/$slug/videos"
                    FapFolderProfileTab.TIMELINE -> "$base/groups/$slug"
                }
            } else {
                when (tab) {
                    FapFolderProfileTab.PHOTOS -> "$base/$username/photos"
                    FapFolderProfileTab.VIDEOS -> "$base/$username/videos"
                    FapFolderProfileTab.TIMELINE -> "$base/$username"
                }
            }

        val stream = profile.tabStreams[tab.param]
        if (stream != null && tab == FapFolderProfileTab.TIMELINE) {
            applyStreamState(stream)
            loadFirstPageFromUrl(tabUrl, tab, null, profile.uid?.toString())
            return
        }

        val fallbackFilter = when (tab) {
            FapFolderProfileTab.PHOTOS -> "photos"
            FapFolderProfileTab.VIDEOS -> "videos"
            FapFolderProfileTab.TIMELINE -> null
        }

        loadFirstPageFromUrl(
            tabUrl = tabUrl,
            tab = tab,
            fallbackFilter = fallbackFilter,
            fallbackId = profile.uid?.toString() ?: profile.groupSlug,
        )
    }

    private fun loadFirstPageFromUrl(
        tabUrl: String,
        tab: FapFolderProfileTab,
        fallbackFilter: String?,
        fallbackId: String?,
    ) {
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

                val snapshot = FapFolderNetworkRepo.parseFirstPageFromHtml(
                    html = rawBody,
                    pageUrl = tabUrl,
                    filter = fallbackFilter,
                    fallbackId = fallbackId,
                )

                val items: List<FapFolderFeedItem> = snapshot.items.ifEmpty {
                    fallbackItemsFromTabPage(rawBody, tabUrl, tab)
                }

                seenKeys.clear()
                items.forEach { seenKeys.add(it.stableKey) }

                _uiState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        hasMore = snapshot.hasMore && items.isNotEmpty(),
                        currentOffset = 2,
                        streamGet = snapshot.get ?: defaultGetForTab(tab),
                        streamFilter = snapshot.filter ?: fallbackFilter,
                        streamId = snapshot.id ?: fallbackId,
                        streamType = snapshot.type,
                        streamUid = snapshot.uid,
                        streamSearch = snapshot.search,
                        streamHashtag = snapshot.hashtag,
                        itemsError = null,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadFirstPageFromUrl($tab) failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        itemsError = e.message ?: "Failed to load.",
                    )
                }
            }
        }
    }

    private fun defaultGetForTab(tab: FapFolderProfileTab): String = when (tab) {
        FapFolderProfileTab.TIMELINE -> FapFolderProfileParser.FALLBACK_GET_POSTS
        FapFolderProfileTab.PHOTOS -> FapFolderProfileParser.FALLBACK_GET_POSTS
        FapFolderProfileTab.VIDEOS -> FapFolderProfileParser.FALLBACK_GET_POSTS
    }

    private fun fallbackItemsFromTabPage(
        html: String,
        tabUrl: String,
        tab: FapFolderProfileTab,
    ): List<FapFolderFeedItem> {
        val doc = runCatching { Jsoup.parse(html, tabUrl) }.getOrNull()
            ?: return emptyList()

        val groupSlug = FapFolderUrlUtils.extractGroupSlug(tabUrl)
        val displayName = groupSlug
            ?: FapFolderUrlUtils.extractUsername(tabUrl)
            ?: "FapFolder"
        val author = FapFolderAuthor(
            uid = null,
            username = FapFolderUrlUtils.extractUsername(tabUrl),
            groupSlug = groupSlug,
            displayName = displayName,
            profileUrl = null,
            avatarUrl = null,
            isGold = false,
            isAnonymous = false,
        )

        if (tab == FapFolderProfileTab.PHOTOS) {
            val anchors = doc.select("a.pg_photo[href], a.js_lightbox[href]")
            val images = anchors.mapNotNull { anchor ->
                val fullUrl = (anchor.attr("data-image").takeIf { it.isNotBlank() }
                    ?: FapFolderHtmlUtils.extractBackgroundImageUrl(anchor.attr("style"))
                    ?: anchor.selectFirst("img[data-src]")?.attr("data-src")
                    ?: anchor.selectFirst("img[src]")?.absUrl("src"))
                    ?.let { FapFolderUrlUtils.normalizeMediaUrl(it) }
                    ?: return@mapNotNull null
                FapFolderMedia.Image(
                    photoId = anchor.attr("data-id").toLongOrNull()
                        ?: FapFolderUrlUtils.extractPhotoId(anchor.attr("href")),
                    fullUrl = fullUrl,
                    previewUrl = anchor.selectFirst("img[data-src]")?.attr("data-src")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { FapFolderUrlUtils.normalizeMediaUrl(it) },
                    context = anchor.attr("data-context").takeIf { it.isNotBlank() },
                    alt = anchor.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() },
                )
            }.distinctBy { it.photoId ?: it.fullUrl }

            if (images.isEmpty()) return emptyList()
            val synthetic = FapFolderPost(
                postId = images.first().photoId ?: tabUrl.hashCode().toLong(),
                slug = null,
                permalink = tabUrl,
                author = author,
                uploaderUsername = null,
                uploaderUrl = null,
                uploaderIsGold = false,
                action = null,
                postedAtIso = "",
                bodyHtml = "",
                bodyPlain = "",
                hashtags = emptyList(),
                media = listOf(
                    FapFolderMedia.Gallery(
                        photos = images,
                        totalCount = images.size,
                        albumId = images.first().photoId?.toString(),
                    )
                ),
                variants = setOf(PostVariant.GALLERY),
                isPremium = false,
                reactions = ReactionCount.EMPTY,
                commentCount = 0,
                shareCount = 0,
                viewCount = null,
            )
            return listOf(FapFolderFeedItem.Post(synthetic))
        }

        if (tab == FapFolderProfileTab.VIDEOS) {
            val blocks = doc.select("div.vid-placeholder[data-src]")
            if (blocks.isEmpty()) return emptyList()
            val media = blocks.mapNotNull { el ->
                val src = el.attr("data-src").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val poster = el.attr("data-poster").takeIf { it.isNotBlank() }
                    ?: FapFolderHtmlUtils.extractBackgroundImageUrl(el.attr("style"))
                        ?.let { FapFolderUrlUtils.normalizeMediaUrl(it) }
                FapFolderMedia.Video(
                    videoId = el.attr("data-id").removePrefix("video-").toLongOrNull(),
                    onPlayId = el.attr("data-onplay-id").toLongOrNull(),
                    playUrl = src,
                    posterUrl = poster,
                    durationSeconds = el.attr("data-length").toIntOrNull(),
                    aspectRatio = el.attr("data-aspect-ratio").toDoubleOrNull(),
                    adEligible = el.attr("data-ad-eligible") == "1",
                    fileExtension = "mp4",
                    expirationEpoch = FapFolderUrlUtils.videoExpirationEpoch(src),
                    signature = FapFolderUrlUtils.videoSignature(src),
                )
            }
            if (media.isEmpty()) return emptyList()
            val synthetic = FapFolderPost(
                postId = tabUrl.hashCode().toLong(),
                slug = null,
                permalink = tabUrl,
                author = author,
                uploaderUsername = null,
                uploaderUrl = null,
                uploaderIsGold = false,
                action = null,
                postedAtIso = "",
                bodyHtml = "",
                bodyPlain = "",
                hashtags = emptyList(),
                media = media,
                variants = setOf(PostVariant.VIDEO),
                isPremium = false,
                reactions = ReactionCount.EMPTY,
                commentCount = 0,
                shareCount = 0,
                viewCount = null,
            )
            return listOf(FapFolderFeedItem.Post(synthetic))
        }

        return emptyList()
    }

    private fun applyStreamState(stream: FapFolderProfileTabStream) {
        _uiState.update {
            it.copy(
                streamGet = stream.get,
                streamFilter = stream.filter,
                streamId = stream.id,
                streamType = stream.type,
                streamUid = stream.uid,
                streamSearch = stream.search,
                streamHashtag = stream.hashtag,
                currentOffset = stream.offset.coerceAtLeast(2),
                hasMore = !stream.done,
            )
        }
    }

    private fun buildStreamRequest(
        state: FapFolderProfileUiState,
        offset: Int,
    ): FapFolderLoadMoreRequest {
        val fallbackFilter = when (state.activeTab) {
            FapFolderProfileTab.PHOTOS -> "photos"
            FapFolderProfileTab.VIDEOS -> "videos"
            FapFolderProfileTab.TIMELINE -> FapFolderConstants.FILTER_ALL
        }
        val fallbackGet = FapFolderProfileParser.FALLBACK_GET_POSTS
        val id = state.streamId
            ?: state.profile?.uid?.toString()
            ?: state.profile?.groupSlug
        val uid = state.streamUid ?: id

        return FapFolderFeedPaging.fromStream(
            get = state.streamGet ?: fallbackGet,
            filter = state.streamFilter ?: fallbackFilter,
            id = id,
            type = state.streamType,
            uid = uid,
            search = state.streamSearch,
            hashtag = state.streamHashtag,
            offset = offset,
            premfeed = FapFolderConstants.PREMFEED_OFF,
            time = FapFolderConstants.TIME_NO_FILTER,
        )
    }

    override fun onCleared() {
        super.onCleared()
        headerJob?.cancel()
        itemsJob?.cancel()
        followJob?.cancel()
    }
}
