package com.yenaly.han1meviewer.FapFolder.ui.group

import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.han1meviewer.R
import androidx.annotation.StringRes
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.model.GroupMembership
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderProfileParser
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderFeedPaging
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FapFolderGroupTab(@StringRes val labelRes: Int, val param: String) {
    POSTS(R.string.fap_tab_posts, "posts"),
    PHOTOS(R.string.fap_tab_photos, "photos"),
    VIDEOS(R.string.fap_tab_videos, "videos");

    val label: String get() = applicationContext.getString(labelRes)

    companion object {
        fun fromParam(value: String?): FapFolderGroupTab =
            entries.firstOrNull { it.param.equals(value, ignoreCase = true) } ?: POSTS
    }
}

data class FapFolderGroupUiState(
    val slug: String = "",
    val activeTab: FapFolderGroupTab = FapFolderGroupTab.POSTS,
    val group: FapFolderGroup? = null,
    val isLoading: Boolean = true,
    val headerError: String? = null,
    val sessionExpired: Boolean = false,
    val items: List<FapFolderFeedItem> = emptyList(),
    val photoTiles: List<FapFolderProfileParser.PhotoTile> = emptyList(),
    val videoTiles: List<FapFolderProfileParser.VideoTile> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val itemsError: String? = null,
    val members: List<FapFolderUserRow> = emptyList(),
    val membersHasMore: Boolean = false,
    val membersLoading: Boolean = false,
    val membersError: String? = null,
    val membersNextPage: Int = 1,
    val joining: Boolean = false,
    val joinError: String? = null,
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

    val mediaImages: List<FapFolderMedia.Image>
        get() = FapFolderProfileParser.toMediaImages(photoTiles)
}

class FapFolderGroupViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderGroupVM"
        private const val MAX_PAGES = 50
    }

    private val _uiState = MutableStateFlow(FapFolderGroupUiState())
    val uiState = _uiState.asStateFlow()

    private var headerJob: Job? = null
    private var itemsJob: Job? = null
    private var membersJob: Job? = null
    private var joinJob: Job? = null
    private var loadedRouteTab: FapFolderGroupTab? = null

    private val seenPostIds = mutableSetOf<Long>()
    private val seenPhotoIds = mutableSetOf<Long>()
    private val seenPhotoUrls = mutableSetOf<String>()
    private val seenVideoPostIds = mutableSetOf<Long>()
    private val seenVideoUrls = mutableSetOf<String>()

    fun load(slug: String, initialTab: String?) {
        val cleanSlug = slug.trim('/')
        if (cleanSlug.isBlank()) return
        val tab = FapFolderGroupTab.fromParam(initialTab)

        val sameLoad = _uiState.value.slug == cleanSlug &&
                (_uiState.value.activeTab == tab || loadedRouteTab == tab) &&
                _uiState.value.group != null
        if (sameLoad) return
        loadedRouteTab = tab

        headerJob?.cancel()
        itemsJob?.cancel()
        membersJob?.cancel()
        clearSeen()

        _uiState.update {
            it.copy(
                slug = cleanSlug,
                activeTab = tab,
                group = null,
                isLoading = true,
                headerError = null,
                sessionExpired = false,
                items = emptyList(),
                photoTiles = emptyList(),
                videoTiles = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
                members = emptyList(),
                membersError = null,
                membersHasMore = false,
                membersNextPage = 1,
                streamGet = null,
                streamFilter = null,
                streamId = null,
                streamType = null,
                streamUid = null,
                streamSearch = null,
                streamHashtag = null,
            )
        }

        headerJob = viewModelScope.launch {
            val headerLoaded = runHeaderRequest(cleanSlug)
            if (headerLoaded) runInitialTabRequest(cleanSlug, tab)
        }
    }

    private suspend fun runHeaderRequest(cleanSlug: String): Boolean {
        val groupState = runCatching {
            FapFolderNetworkRepo.getGroupPage(cleanSlug).firstTerminalGroupState()
        }.getOrNull()

        return when (groupState) {
            null -> {
                _uiState.update {
                    it.copy(isLoading = false, headerError = applicationContext.getString(R.string.fap_group_load_failed))
                }
                false
            }
            is VideoLoadingState.Success -> {
                _uiState.update {
                    it.copy(group = groupState.info, headerError = null)
                }
                true
            }
            is VideoLoadingState.Error -> {
                val msg = groupState.throwable.message
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        headerError = msg ?: applicationContext.getString(R.string.fap_group_load_failed),
                        sessionExpired = msg?.contains("session", true) == true,
                    )
                }
                false
            }
            is VideoLoadingState.NoContent -> {
                _uiState.update {
                    it.copy(isLoading = false, headerError = applicationContext.getString(R.string.fap_group_not_found))
                }
                false
            }
            is VideoLoadingState.Loading -> false
        }
    }

    private suspend fun runInitialTabRequest(
        cleanSlug: String,
        tab: FapFolderGroupTab,
    ) {
        when (tab) {
            FapFolderGroupTab.PHOTOS, FapFolderGroupTab.VIDEOS ->
                loadFirstMediaPage(cleanSlug, tab)
            FapFolderGroupTab.POSTS -> loadFirstPostPage(cleanSlug)
        }
    }

    private suspend fun loadFirstPostPage(cleanSlug: String) {
        val groupUrl = FapFolderNetwork.baseUrl.trimEnd('/') + "/groups/${cleanSlug.trim('/')}"
        try {
            val response = FapFolderNetwork.service.getGroupUrl(groupUrl)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        itemsError = applicationContext.getString(R.string.fap_http_error, response.code()),
                    )
                }
                return
            }
            val snapshot = FapFolderNetworkRepo.parseFirstPageFromHtml(
                html = rawBody,
                pageUrl = groupUrl,
                filter = null,
                fallbackId = _uiState.value.group?.groupId?.toString(),
            )
            snapshot.items.forEach { item ->
                if (item is FapFolderFeedItem.Post) {
                    seenPostIds.add(item.post.postId)
                }
            }
            _uiState.update {
                it.copy(
                    items = snapshot.items,
                    isLoading = false,
                    hasMore = snapshot.hasMore || snapshot.items.isNotEmpty(),
                    currentOffset = snapshot.nextOffset,
                    streamGet = snapshot.get,
                    streamFilter = snapshot.filter,
                    streamId = snapshot.id ?: it.group?.groupId?.toString(),
                    streamType = snapshot.type,
                    streamUid = snapshot.uid,
                    streamSearch = snapshot.search,
                    streamHashtag = snapshot.hashtag,
                    itemsError = null,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFirstPostPage failed", e)
            _uiState.update {
                it.copy(isLoading = false, itemsError = e.message ?: applicationContext.getString(R.string.fap_load_failed))
            }
        }
    }

    private suspend fun loadFirstMediaPage(cleanSlug: String, tab: FapFolderGroupTab) {
        val tabUrl = FapFolderNetwork.baseUrl.trimEnd('/') +
                "/groups/${cleanSlug.trim('/')}/${tab.param}"
        try {
            val response = FapFolderNetwork.service.getByUrl(tabUrl)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        itemsError = applicationContext.getString(R.string.fap_http_error, response.code()),
                    )
                }
                return
            }
            when (tab) {
                FapFolderGroupTab.PHOTOS -> applyPhotoFirstPage(rawBody, tabUrl)
                FapFolderGroupTab.VIDEOS -> applyVideoFirstPage(rawBody, tabUrl)
                else -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFirstMediaPage($tab) failed", e)
            _uiState.update {
                it.copy(isLoading = false, itemsError = e.message ?: applicationContext.getString(R.string.fap_load_failed))
            }
        }
    }

    fun switchTab(tab: FapFolderGroupTab) {
        if (_uiState.value.activeTab == tab) return
        _uiState.update {
            it.copy(
                activeTab = tab,
                items = emptyList(),
                photoTiles = emptyList(),
                videoTiles = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
                members = emptyList(),
                membersError = null,
                membersHasMore = false,
                membersNextPage = 1,
                streamGet = null,
                streamFilter = null,
                streamId = null,
                streamType = null,
                streamUid = null,
                streamSearch = null,
                streamHashtag = null,
            )
        }
        clearSeen()
        run {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                when (tab) {
                    FapFolderGroupTab.PHOTOS, FapFolderGroupTab.VIDEOS ->
                        loadFirstMediaPage(_uiState.value.slug, tab)
                    FapFolderGroupTab.POSTS ->
                        loadFirstPostPage(_uiState.value.slug)
                                    }
            }
        }
    }

    fun refresh() {
        val slug = _uiState.value.slug
        if (slug.isBlank()) return
        val tab = _uiState.value.activeTab
        _uiState.update { it.copy(slug = "") }
        val routeTab = loadedRouteTab
        load(slug, tab.param)
        loadedRouteTab = routeTab
    }

    fun loadMoreItems() = loadMoreItemsInternal(forceAfterError = false)

    fun retryLoadMoreItems() = loadMoreItemsInternal(forceAfterError = true)

    private fun loadMoreItemsInternal(forceAfterError: Boolean) {
        val state = _uiState.value

        val canRetryAfterError = forceAfterError && state.itemsError != null
        if (state.isLoadingMore || state.isLoading ||
            (!state.hasMore && !canRetryAfterError)) return
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
                    is PageLoadingState.Success ->
                        handleLoadMoreSuccess(state.activeTab, page.info)
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

    fun joinGroup() {
        val group = _uiState.value.group ?: return
        val groupId = group.groupId ?: return
        if (_uiState.value.joining) return

        joinJob?.cancel()
        joinJob = viewModelScope.launch {
            _uiState.update { it.copy(joining = true, joinError = null) }

            val flow = when (group.membership) {
                GroupMembership.NOT_MEMBER -> FapFolderNetworkRepo.joinGroup(groupId)
                GroupMembership.MEMBER, GroupMembership.ADMIN, GroupMembership.OWNER ->
                    FapFolderNetworkRepo.leaveGroup(groupId)
                GroupMembership.PENDING -> FapFolderNetworkRepo.leaveGroup(groupId)
                GroupMembership.UNKNOWN -> FapFolderNetworkRepo.joinGroup(groupId)
            }

            flow.collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> {
                        val newMembership = when (group.membership) {
                            GroupMembership.NOT_MEMBER, GroupMembership.UNKNOWN ->
                                GroupMembership.MEMBER
                            GroupMembership.PENDING -> GroupMembership.NOT_MEMBER
                            GroupMembership.MEMBER -> GroupMembership.NOT_MEMBER
                            GroupMembership.ADMIN, GroupMembership.OWNER -> group.membership
                        }
                        _uiState.update {
                            it.copy(
                                joining = false,
                                group = it.group?.copy(membership = newMembership),
                            )
                        }
                    }
                    is WebsiteState.Error -> _uiState.update {
                        it.copy(
                            joining = false,
                            joinError = result.throwable.message
                                ?: applicationContext.getString(R.string.fap_membership_update_failed),
                        )
                    }
                }
            }
        }
    }

    private fun clearSeen() {
        seenPostIds.clear()
        seenPhotoIds.clear()
        seenPhotoUrls.clear()
        seenVideoPostIds.clear()
        seenVideoUrls.clear()
    }

    private fun applyPhotoFirstPage(html: String, tabUrl: String) {
        val snapshot = FapFolderProfileParser.parseGalleryPage(html, tabUrl)
        val tiles = snapshot?.photos.orEmpty().filter { tile ->
            val id = tile.photoId
            val url = tile.fullUrl
            val idTaken = id != null && seenPhotoIds.contains(id)
            val urlTaken = seenPhotoUrls.contains(url)
            if (!idTaken && !urlTaken) {
                id?.let { seenPhotoIds.add(it) }
                seenPhotoUrls.add(url)
                true
            } else false
        }
        _uiState.update {
            it.copy(
                photoTiles = tiles,
                items = emptyList(),
                isLoading = false,
                hasMore = tiles.isNotEmpty(),
                currentOffset = snapshot?.nextOffset ?: 1,
                streamGet = snapshot?.streamGet
                    ?: FapFolderProfileParser.FALLBACK_GET_PHOTOS,
                streamFilter = null,
                streamId = snapshot?.streamId ?: it.group?.groupId?.toString(),
                streamType = snapshot?.streamType ?: "group",
                itemsError = null,
            )
        }
    }

    private fun applyVideoFirstPage(html: String, tabUrl: String) {
        val snapshot = FapFolderProfileParser.parseVideosPage(html, tabUrl)
        val tiles = snapshot?.videos.orEmpty().filter { tile ->
            val id = tile.postId
            val url = tile.postUrl
            val idTaken = id != null && seenVideoPostIds.contains(id)
            val urlTaken = seenVideoUrls.contains(url)
            if (!idTaken && !urlTaken) {
                id?.let { seenVideoPostIds.add(it) }
                seenVideoUrls.add(url)
                true
            } else false
        }
        _uiState.update {
            it.copy(
                videoTiles = tiles,
                items = emptyList(),
                isLoading = false,
                hasMore = tiles.isNotEmpty(),
                currentOffset = snapshot?.nextOffset ?: 1,
                streamGet = snapshot?.streamGet
                    ?: FapFolderProfileParser.FALLBACK_GET_VIDEOS,
                streamFilter = null,
                streamId = snapshot?.streamId ?: it.group?.groupId?.toString(),
                streamType = snapshot?.streamType ?: "group",
                itemsError = null,
            )
        }
    }

    private fun handleLoadMoreSuccess(
        tab: FapFolderGroupTab,
        incoming: List<FapFolderFeedItem>,
    ) {
        when (tab) {
            FapFolderGroupTab.PHOTOS -> {
                val newTiles = incoming.mapNotNull { item ->
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
                }.filter { tile ->
                    val id = tile.photoId
                    val url = tile.fullUrl
                    val idTaken = id != null && seenPhotoIds.contains(id)
                    val urlTaken = seenPhotoUrls.contains(url)
                    if (!idTaken && !urlTaken) {
                        id?.let { seenPhotoIds.add(it) }
                        seenPhotoUrls.add(url)
                        true
                    } else false
                }
                _uiState.update {
                    it.copy(
                        photoTiles = it.photoTiles + newTiles,
                        isLoadingMore = false,
                        hasMore = incoming.isNotEmpty() && newTiles.isNotEmpty(),
                        itemsError = null,
                        currentOffset = it.currentOffset + 1,
                    )
                }
            }

            FapFolderGroupTab.VIDEOS -> {
                val newTiles = incoming
                    .filterIsInstance<FapFolderFeedItem.Post>()
                    .map { item ->
                        val post = item.post
                        FapFolderProfileParser.VideoTile(
                            postId = post.postId,
                            postUrl = post.permalink
                                ?: "${FapFolderNetwork.baseUrl.trimEnd('/')}/posts/${post.postId}",
                            thumbnailUrl = post.primaryVideo?.posterUrl
                                ?: post.primaryImage?.fullUrl,
                            title = post.bodyPlain.take(80).ifBlank { null },
                        )
                    }
                    .filter { tile ->
                        val id = tile.postId
                        val url = tile.postUrl
                        val idTaken = id != null && seenVideoPostIds.contains(id)
                        val urlTaken = seenVideoUrls.contains(url)
                        if (!idTaken && !urlTaken) {
                            id?.let { seenVideoPostIds.add(it) }
                            seenVideoUrls.add(url)
                            true
                        } else false
                    }
                _uiState.update {
                    it.copy(
                        videoTiles = it.videoTiles + newTiles,
                        isLoadingMore = false,
                        hasMore = incoming.isNotEmpty() && newTiles.isNotEmpty(),
                        itemsError = null,
                        currentOffset = it.currentOffset + 1,
                    )
                }
            }

            else -> {
                val fresh = incoming.filter { item ->
                    when (item) {
                        is FapFolderFeedItem.Post -> seenPostIds.add(item.post.postId)
                        else -> true
                    }
                }
                _uiState.update {
                    it.copy(
                        items = it.items + fresh,
                        isLoadingMore = false,
                        hasMore = incoming.isNotEmpty() && fresh.isNotEmpty(),
                        itemsError = null,
                        currentOffset = it.currentOffset + 1,
                    )
                }
            }
        }
    }

    private fun buildStreamRequest(
        state: FapFolderGroupUiState,
        offset: Int,
    ): FapFolderLoadMoreRequest {
        val get: String
        val filter: String?
        val type: String?
        when (state.activeTab) {
            FapFolderGroupTab.PHOTOS -> {
                get = state.streamGet ?: FapFolderProfileParser.FALLBACK_GET_PHOTOS
                filter = null
                type = state.streamType ?: "group"
            }
            FapFolderGroupTab.VIDEOS -> {
                get = state.streamGet ?: FapFolderProfileParser.FALLBACK_GET_VIDEOS
                filter = null
                type = state.streamType ?: "group"
            }
            else -> {
                get = state.streamGet ?: FapFolderConstants.FEED_GET_POSTS_GROUP
                filter = state.streamFilter ?: FapFolderConstants.FILTER_ALL
                type = state.streamType
            }
        }
        return FapFolderFeedPaging.fromStream(
            get = get,
            filter = filter,
            id = state.streamId ?: state.group?.groupId?.toString(),
            type = type,
            uid = state.streamUid,
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
        membersJob?.cancel()
        joinJob?.cancel()
    }
}

private suspend fun <T> Flow<VideoLoadingState<T>>.firstTerminalGroupState()
        : VideoLoadingState<T>? =
    firstOrNull { it !is VideoLoadingState.Loading }
