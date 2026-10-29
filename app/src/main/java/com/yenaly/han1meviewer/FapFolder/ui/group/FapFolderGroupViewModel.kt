package com.yenaly.han1meviewer.FapFolder.ui.group

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.model.GroupMembership
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderFeedPaging
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

enum class FapFolderGroupTab(val label: String, val param: String) {
    POSTS("Posts", "posts"),
    PHOTOS("Photos", "photos"),
    VIDEOS("Videos", "videos"),
    MEMBERS("Members", "members");

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

    private val seenPostIds = mutableSetOf<Long>()

    fun load(slug: String, initialTab: String?) {
        val cleanSlug = slug.trim('/')
        if (cleanSlug.isBlank()) return
        val tab = FapFolderGroupTab.fromParam(initialTab)
        val sameLoad = _uiState.value.slug == cleanSlug &&
                _uiState.value.activeTab == tab &&
                _uiState.value.group != null
        if (sameLoad) return

        headerJob?.cancel()
        itemsJob?.cancel()
        membersJob?.cancel()
        seenPostIds.clear()

        _uiState.update {
            it.copy(
                slug = cleanSlug,
                activeTab = tab,
                group = null,
                isLoading = true,
                headerError = null,
                sessionExpired = false,
                items = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
                members = emptyList(),
                membersError = null,
                membersHasMore = false,
                membersNextPage = 1,
            )
        }

        headerJob = viewModelScope.launch {
            FapFolderNetworkRepo.getGroupPage(cleanSlug).collect { state ->
                when (state) {
                    is VideoLoadingState.Loading -> Unit
                    is VideoLoadingState.Success -> {
                        _uiState.update {
                            it.copy(
                                group = state.info,
                                isLoading = false,
                                headerError = null,
                            )
                        }
                        if (tab == FapFolderGroupTab.MEMBERS) {
                            loadMembers(reset = true)
                        } else {
                            loadFirstItems(tab)
                        }
                    }
                    is VideoLoadingState.Error -> {
                        val msg = state.throwable.message
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                headerError = msg ?: "Failed to load group.",
                                sessionExpired = msg?.contains("session", true) == true,
                            )
                        }
                    }
                    is VideoLoadingState.NoContent -> _uiState.update {
                        it.copy(isLoading = false, headerError = "Group not found.")
                    }
                }
            }
        }
    }

    fun switchTab(tab: FapFolderGroupTab) {
        if (_uiState.value.activeTab == tab) return
        _uiState.update {
            it.copy(
                activeTab = tab,
                items = emptyList(),
                itemsError = null,
                hasMore = false,
                currentOffset = 1,
                members = emptyList(),
                membersError = null,
                membersHasMore = false,
                membersNextPage = 1,
            )
        }
        seenPostIds.clear()
        if (tab == FapFolderGroupTab.MEMBERS) {
            loadMembers(reset = true)
        } else {
            loadFirstItems(tab)
        }
    }

    fun refresh() {
        val slug = _uiState.value.slug
        if (slug.isBlank()) return
        _uiState.update { it.copy(slug = "") }
        load(slug, _uiState.value.activeTab.param)
    }

    fun loadMoreItems() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore) return
        if (state.activeTab == FapFolderGroupTab.MEMBERS) {
            loadMembers(reset = false)
            return
        }
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
                            when (item) {
                                is FapFolderFeedItem.Post -> seenPostIds.add(item.post.postId)
                                else -> true
                            }
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

    fun loadMoreMembers() {
        if (_uiState.value.membersLoading || !_uiState.value.membersHasMore) return
        loadMembers(reset = false)
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
                                ?: "Failed to update membership.",
                        )
                    }
                }
            }
        }
    }

    private fun loadFirstItems(tab: FapFolderGroupTab) {
        val state = _uiState.value
        val group = state.group ?: return
        val groupId = group.groupId

        if (tab == FapFolderGroupTab.PHOTOS || tab == FapFolderGroupTab.VIDEOS) {
            val tabUrl = FapFolderNetwork.baseUrl.trimEnd('/') +
                    "/groups/${state.slug.trim('/')}/${tab.param}"
            itemsJob?.cancel()
            itemsJob = viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, itemsError = null) }
                try {
                    val response = FapFolderNetwork.service.getByUrl(tabUrl)
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
                    FapFolderNetworkRepo.parseFirstPageFromHtml(
                        html = rawBody,
                        pageUrl = tabUrl,
                        filter = tab.param,
                        fallbackId = groupId?.toString(),
                    ).let { snapshot ->
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
                        snapshot.items.forEach { item ->
                            if (item is FapFolderFeedItem.Post) {
                                seenPostIds.add(item.post.postId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "loadFirstItems($tab) failed", e)
                    _uiState.update {
                        it.copy(isLoading = false, itemsError = e.message ?: "Load failed.")
                    }
                }
            }
            return
        }

        val groupUrl = FapFolderNetwork.baseUrl.trimEnd('/') + "/groups/${state.slug.trim('/')}"
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, itemsError = null) }
            try {
                val response = FapFolderNetwork.service.getGroupUrl(groupUrl)
                val rawBody = response.body()?.string().orEmpty()
                if (!response.isSuccessful || rawBody.isBlank()) {
                    _uiState.update {
                        it.copy(isLoading = false, itemsError = "HTTP ${response.code()}")
                    }
                    return@launch
                }
                FapFolderNetworkRepo.parseFirstPageFromHtml(
                    html = rawBody,
                    pageUrl = groupUrl,
                    filter = null,
                    fallbackId = groupId?.toString(),
                ).let { snapshot ->
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
                    snapshot.items.forEach { item ->
                        if (item is FapFolderFeedItem.Post) {
                            seenPostIds.add(item.post.postId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadFirstItems($tab) failed", e)
                _uiState.update {
                    it.copy(isLoading = false, itemsError = e.message ?: "Load failed.")
                }
            }
        }
    }

    private fun loadMembers(reset: Boolean) {
        val state = _uiState.value
        val group = state.group ?: return
        val page = if (reset) 1 else state.membersNextPage

        membersJob?.cancel()
        membersJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    membersLoading = true,
                    membersError = null,
                    membersHasMore = if (reset) false else it.membersHasMore,
                )
            }

            FapFolderNetworkRepo.getGroupMembers(
                groupId = group.groupId,
                slug = state.slug,
                page = page,
            ).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> _uiState.update {
                        val existing = if (reset) emptyList() else it.members
                        val merged = (existing + pageState.info.members)
                            .distinctBy { m -> m.uid ?: m.username }
                        it.copy(
                            members = merged,
                            membersLoading = false,
                            membersHasMore = pageState.info.hasMore,
                            membersNextPage = page + 1,
                            membersError = null,
                        )
                    }
                    is PageLoadingState.NoMoreData -> _uiState.update {
                        it.copy(membersLoading = false, membersHasMore = false)
                    }
                    is PageLoadingState.Error -> {
                        val msg = pageState.throwable.message
                        _uiState.update {
                            it.copy(
                                membersLoading = false,
                                membersError = msg,
                                sessionExpired = msg?.contains("session", true) == true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildStreamRequest(
        state: FapFolderGroupUiState,
        offset: Int,
    ): FapFolderLoadMoreRequest {
        val get = state.streamGet
            ?: when (state.activeTab) {
                FapFolderGroupTab.PHOTOS -> FapFolderConstants.FEED_GET_POSTS_GROUP
                FapFolderGroupTab.VIDEOS -> FapFolderConstants.FEED_GET_POSTS_GROUP
                else -> FapFolderConstants.FEED_GET_POSTS_GROUP
            }
        val filter = state.streamFilter
            ?: when (state.activeTab) {
                FapFolderGroupTab.PHOTOS -> "photos"
                FapFolderGroupTab.VIDEOS -> "videos"
                else -> FapFolderConstants.FILTER_ALL
            }
        val id = state.streamId ?: state.group?.groupId?.toString()
        return FapFolderFeedPaging.fromStream(
            get = get,
            filter = filter,
            id = id,
            type = state.streamType,
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