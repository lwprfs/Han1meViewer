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

data class FapFolderProfileTab(
    val label: String,
    val param: String,
    val url: String,
    val kind: Kind,
) {
    enum class Kind { POSTS, PHOTOS, VIDEOS, GROUPS, FRIENDS, ABOUT, OTHER }

    companion object {
        fun fromParam(value: String?): String {
            val v = value?.lowercase().orEmpty()
            return when {
                v.contains("photo") -> "photos"
                v.contains("video") -> "videos"
                v.contains("group") || v.contains("communit") -> "groups"
                v.contains("friend") -> "friends"
                v.contains("about") -> "about"
                v.contains("post") -> "posts"
                else -> "posts"
            }
        }

        fun kindOf(param: String): Kind = when (param) {
            "photos" -> Kind.PHOTOS
            "videos" -> Kind.VIDEOS
            "groups" -> Kind.GROUPS
            "friends" -> Kind.FRIENDS
            "about" -> Kind.ABOUT
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
                tabs = defaultTabs(),
            )
        }

        headerJob = viewModelScope.launch {
            FapFolderNetworkRepo.getProfilePage(clean).collect { state ->
                when (state) {
                    is VideoLoadingState.Loading -> Unit
                    is VideoLoadingState.Success -> {
                        val profile = state.info
                        val resolvedTabs = resolveTabs(profile)
                        _uiState.update {
                            it.copy(
                                profile = profile,
                                isLoading = false,
                                headerError = null,
                                following = profile.isFollowed,
                                tabs = resolvedTabs,
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
        load(username, currentTab)
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

    private fun resolveTabs(profile: FapFolderUserProfile): List<FapFolderProfileTab> {
        if (profile.tabUrls.isEmpty()) {
            return defaultTabs().filter { tab ->
                when (tab.param) {
                    "photos" -> (profile.photoCount ?: 0) > 0
                    "videos" -> (profile.videoCount ?: 0) > 0
                    "groups" -> true
                    "friends" -> true
                    else -> true
                }
            }
        }
        return profile.tabUrls.map { (param, url) ->
            FapFolderProfileTab(
                label = labelForParam(param),
                param = param,
                url = url,
                kind = FapFolderProfileTab.kindOf(param),
            )
        }
    }

    private fun labelForParam(param: String): String = when (param) {
        "photos" -> "Photos"
        "videos" -> "Videos"
        "groups" -> "Groups"
        "friends" -> "Friends"
        "about" -> "About"
        else -> "Posts"
    }

    private fun loadTabItems(tab: String) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val username = state.username
        if (username.isBlank()) return

        val base = FapFolderNetwork.baseUrl.trimEnd('/')
        val isGroupProfile = !profile.groupSlug.isNullOrBlank()
        val tabUrl = profile.tabUrls[tab]
            ?: if (isGroupProfile) {
                val slug = profile.groupSlug
                when (tab) {
                    "photos" -> "$base/groups/$slug/photos"
                    "videos" -> "$base/groups/$slug/videos"
                    "groups" -> "$base/groups/$slug/groups"
                    "friends" -> "$base/groups/$slug/members"
                    "about" -> "$base/groups/$slug"
                    else -> "$base/groups/$slug"
                }
            } else {
                when (tab) {
                    "photos" -> "$base/$username/photos"
                    "videos" -> "$base/$username/videos"
                    "groups" -> "$base/$username/groups"
                    "friends" -> "$base/$username/friends"
                    "about" -> "$base/$username/about"
                    else -> "$base/$username"
                }
            }

        if (tab == "about") {
            _uiState.update { it.copy(items = emptyList(), isLoading = false, hasMore = false) }
            return
        }

        val stream = profile.tabStreams[tab]
        if (stream != null && tab == "posts") {
            applyStreamState(stream)
            loadFirstPageFromUrl(tabUrl, tab, null, profile.uid?.toString())
            return
        }

        loadFirstPageFromUrl(
            tabUrl = tabUrl,
            tab = tab,
            fallbackFilter = tab.takeIf { it == "photos" || it == "videos" },
            fallbackId = profile.uid?.toString() ?: profile.groupSlug,
        )
    }

    private fun loadFirstPageFromUrl(
        tabUrl: String,
        tab: String,
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
                        streamGet = snapshot.get,
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

    private fun fallbackItemsFromTabPage(
        html: String,
        tabUrl: String,
        tab: String,
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

        if (tab == "photos") {
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

        if (tab == "videos") {
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
            "photos" -> "photos"
            "videos" -> "videos"
            else -> FapFolderConstants.FILTER_ALL
        }
        val fallbackGet = when (state.activeTab) {
            "groups" -> FapFolderProfileParser.FALLBACK_GET_GROUPS
            "friends" -> FapFolderProfileParser.FALLBACK_GET_FRIENDS
            else -> FapFolderProfileParser.FALLBACK_GET_POSTS
        }
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
