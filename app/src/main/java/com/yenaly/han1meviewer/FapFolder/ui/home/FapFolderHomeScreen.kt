package com.yenaly.han1meviewer.FapFolder.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderPostCard
import com.yenaly.han1meviewer.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class HomeSortOption(val label: String, val value: String)

private val homeSortOptions = listOf(
    HomeSortOption("All", FapFolderConstants.FILTER_ALL),
    HomeSortOption("Photos", FapFolderConstants.FILTER_PHOTOS),
    HomeSortOption("Videos", FapFolderConstants.FILTER_VIDEOS),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderHomeScreen(
    onOpenPost: (postId: String) -> Unit,
    onOpenSearch: (query: String?) -> Unit,
    onOpenUser: (username: String) -> Unit,
    onOpenHashtag: (tag: String) -> Unit,
    onOpenGroup: (slug: String) -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchSite: () -> Unit,
    onSessionExpired: () -> Unit = {},
    onLoadFailure: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FapFolderHomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiState.items.isEmpty() && uiState.firstLoad) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let(onLoadFailure)
    }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onSessionExpired()
    }

    LaunchedEffect(listState, uiState.hasMore, uiState.isLoadingMore, uiState.firstLoad) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 &&
                    last >= total - 4 &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.firstLoad
                ) {
                    viewModel.loadMore()
                }
            }
    }

    LaunchedEffect(uiState.firstLoad, uiState.isLoadingMore) {
        if (!uiState.firstLoad && !uiState.isLoadingMore) {
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "FapFolder", fontWeight = FontWeight.Bold)
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { sortMenuOpen = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val current = homeSortOptions
                                    .firstOrNull { it.value == uiState.feedFilter }
                                    ?.label ?: "All"
                                Text(
                                    text = current,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false },
                            ) {
                                homeSortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortMenuOpen = false
                                            viewModel.changeFilter(option.value)
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.watch_history),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                    IconButton(onClick = onSwitchSite) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_switch_24),
                            contentDescription = stringResource(R.string.switch_site),
                        )
                    }
                    IconButton(onClick = { onOpenSearch(null) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { listState.scrollToItem(0) }
                viewModel.refresh()
            },
            state = refreshState,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.firstLoad && uiState.items.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                    uiState.errorMessage != null && uiState.items.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = uiState.errorMessage ?: "Failed to load",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .clickable { viewModel.refresh() }
                                        .padding(8.dp),
                                ) {
                                    Text(
                                        text = "Retry",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    uiState.items.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No posts yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        FeedList(
                            items = uiState.items,
                            listState = listState,
                            isLoadingMore = uiState.isLoadingMore,
                            trending = uiState.trending,
                            suggested = uiState.suggested,
                            onOpenPost = onOpenPost,
                            onOpenUser = onOpenUser,
                            onOpenHashtag = onOpenHashtag,
                            onOpenGroup = onOpenGroup,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedList(
    items: List<FapFolderFeedItem>,
    listState: LazyListState,
    isLoadingMore: Boolean,
    trending: List<com.yenaly.han1meviewer.FapFolder.data.model.FapFolderTrendingTag>,
    suggested: List<com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSuggestedCommunity>,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.stableKey }) { item ->
            when (item) {
                is FapFolderFeedItem.Post -> {
                    val post = item.post
                    FapFolderPostCard(
                        post = post,
                        onClickPost = {
                            val slug = post.slug ?: post.postId.toString()
                            onOpenPost(slug)
                        },
                        onClickHashtag = onOpenHashtag,
                        onClickAuthor = { author ->
                            val group = author.groupSlug
                            val user = author.username
                            when {
                                !group.isNullOrBlank() -> onOpenGroup(group)
                                !user.isNullOrBlank() -> onOpenUser(user)
                            }
                        },
                        onClickVideo = { },
                        onClickImage = { image, all ->
                            val albumId = all.firstOrNull()?.photoId?.toString()
                            if (albumId != null) onOpenAlbum(albumId)
                        },
                        onClickPoll = { },
                        onClickLink = { },
                        onLongClickPost = { },
                    )
                }
                is FapFolderFeedItem.User -> {
                    com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderUserRowCard(
                        user = item.user,
                        onClick = {
                            val username = item.user.username
                            if (username.isNotBlank()) onOpenUser(username)
                        },
                    )
                }
                is FapFolderFeedItem.Community -> {
                    com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderGroupRowCard(
                        group = item.group,
                        onClick = {
                            val slug = item.group.slug
                            if (slug.isNotBlank()) onOpenGroup(slug)
                        },
                    )
                }
            }
        }

        if (items.size >= 5) {
            if (trending.isNotEmpty()) {
                item(key = "__trending__") {
                    FapFolderTrendingCard(
                        tags = trending,
                        onTagClick = onOpenHashtag,
                    )
                }
            }
            if (suggested.isNotEmpty()) {
                item(key = "__suggested__") {
                    FapFolderSuggestedCommunitiesCard(
                        communities = suggested,
                        onCommunityClick = onOpenGroup,
                    )
                }
            }
        }

        if (isLoadingMore) {
            item(key = "__loading_more__") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}