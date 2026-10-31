package com.yenaly.han1meviewer.FapFolder.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.model.GroupMembership
import com.yenaly.han1meviewer.FapFolder.data.model.GroupVisibility
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderProfileParser
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderImageLightboxDialog
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderInteractivePostCard
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderUserRowCard
import com.yenaly.han1meviewer.FapFolder.ui.video.FapFolderVideoPlayerScreen
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderGroupScreen(
    slug: String,
    initialTab: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenHashtag: (String) -> Unit = {},
    viewModel: FapFolderGroupViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var videoToPlay by remember { mutableStateOf<FapFolderMedia.Video?>(null) }
    var videoPost by remember { mutableStateOf<FapFolderPost?>(null) }
    var lightboxPayload by remember {
        mutableStateOf<Pair<List<FapFolderMedia.Image>, Int>?>(null)
    }

    LaunchedEffect(slug, initialTab) {
        viewModel.load(slug, initialTab)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.group?.displayName ?: slug,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading && uiState.group == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                uiState.headerError != null && uiState.group == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Failed to load group",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = uiState.headerError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
                        }
                    }
                }

                else -> {
                    when (uiState.activeTab) {
                        FapFolderGroupTab.PHOTOS,
                        FapFolderGroupTab.VIDEOS,
                        -> GroupMediaTab(
                            uiState = uiState,
                            onSwitchTab = { viewModel.switchTab(it) },
                            onToggleMembership = { viewModel.joinGroup() },
                            onOpenImage = { images, idx -> lightboxPayload = images to idx },
                            onOpenVideoBySlug = onOpenPost,
                            onLoadMore = { viewModel.loadMoreItems() },
                            onRetry = { viewModel.loadMoreItems() },
                        )

                        FapFolderGroupTab.MEMBERS -> GroupMembersTab(
                            uiState = uiState,
                            onSwitchTab = { viewModel.switchTab(it) },
                            onToggleMembership = { viewModel.joinGroup() },
                            onOpenProfile = onOpenProfile,
                            onLoadMore = { viewModel.loadMoreMembers() },
                            onRetry = { viewModel.loadMoreMembers() },
                        )

                        FapFolderGroupTab.POSTS -> GroupPostsTab(
                            uiState = uiState,
                            onSwitchTab = { viewModel.switchTab(it) },
                            onToggleMembership = { viewModel.joinGroup() },
                            onOpenPost = onOpenPost,
                            onOpenHashtag = onOpenHashtag,
                            onOpenProfile = onOpenProfile,
                            onOpenVideo = { post, video ->
                                videoPost = post
                                videoToPlay = video
                            },
                            onOpenImage = { images, idx -> lightboxPayload = images to idx },
                            onLoadMore = { viewModel.loadMoreItems() },
                            onRetry = { viewModel.loadMoreItems() },
                        )
                    }
                }
            }
        }
    }

    videoToPlay?.let { video ->
        FapFolderVideoPlayerScreen(
            video = video,
            title = videoPost?.bodyPlain?.take(60).orEmpty(),
            onDismiss = {
                videoToPlay = null
                videoPost = null
            },
            postId = videoPost?.postId,
            coverUrl = video.posterUrl,
            postPermalinkForReferer = videoPost?.permalink
                ?: "https://fapfolder.club/groups/${uiState.slug}",
            onExpired = {
                val post = videoPost
                videoToPlay = null
                videoPost = null
                if (post != null) {
                    val slugOrId = post.slug ?: post.postId.toString()
                    onOpenPost(slugOrId)
                }
            },
        )
    }

    lightboxPayload?.let { (images, startIndex) ->
        FapFolderImageLightboxDialog(
            images = images,
            initialIndex = startIndex,
            onDismiss = { lightboxPayload = null },
        )
    }
}

@Composable
private fun GroupHeaderBlock(
    group: com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup,
    joining: Boolean,
    joinError: String?,
    onToggleMembership: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            group.bannerUrl?.let { banner ->
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            ) {
                val avatar = group.avatarUrl
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = group.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = group.displayName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (group.visibility != GroupVisibility.PUBLIC &&
                        group.visibility != GroupVisibility.UNKNOWN
                    ) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = if (group.visibility == GroupVisibility.PRIVATE) "Private"
                                else "Secret",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                val meta = buildList {
                    group.memberCount?.let { add("$it members") }
                    group.postCount?.let { add("$it posts") }
                    group.photoCount?.let { add("$it photos") }
                    group.videoCount?.let { add("$it videos") }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MembershipButton(
                membership = group.membership,
                loading = joining,
                onClick = onToggleMembership,
            )
        }

        if (!group.bio.isNullOrBlank()) {
            Text(
                text = group.bio ,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!joinError.isNullOrBlank()) {
            Text(
                text = joinError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupTabRow(
    activeTab: FapFolderGroupTab,
    onSelect: (FapFolderGroupTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FapFolderGroupTab.entries.forEach { tab ->
            FilterChip(
                selected = activeTab == tab,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

private fun LazyListScope.groupHeaderItems(
    uiState: FapFolderGroupUiState,
    onSwitchTab: (FapFolderGroupTab) -> Unit,
    onToggleMembership: () -> Unit,
) {
    uiState.group?.let { group ->
        item(key = "group_header") {
            GroupHeaderBlock(
                group = group,
                joining = uiState.joining,
                joinError = uiState.joinError,
                onToggleMembership = onToggleMembership,
            )
        }
        item(key = "group_tabs") {
            GroupTabRow(
                activeTab = uiState.activeTab,
                onSelect = onSwitchTab,
            )
        }
    }
}

private fun LazyGridScope.groupHeaderItems(
    uiState: FapFolderGroupUiState,
    onSwitchTab: (FapFolderGroupTab) -> Unit,
    onToggleMembership: () -> Unit,
) {
    uiState.group?.let { group ->
        item(
            key = "group_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            GroupHeaderBlock(
                group = group,
                joining = uiState.joining,
                joinError = uiState.joinError,
                onToggleMembership = onToggleMembership,
            )
        }
        item(
            key = "group_tabs",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            GroupTabRow(
                activeTab = uiState.activeTab,
                onSelect = onSwitchTab,
            )
        }
    }
}

@Composable
private fun GroupPostsTab(
    uiState: FapFolderGroupUiState,
    onSwitchTab: (FapFolderGroupTab) -> Unit,
    onToggleMembership: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenVideo: (FapFolderPost, FapFolderMedia.Video) -> Unit,
    onOpenImage: (List<FapFolderMedia.Image>, Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState: LazyListState = rememberLazyListState()

    LaunchedEffect(listState, uiState.hasMore, uiState.isLoadingMore, uiState.isLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 &&
                    last >= total - 4 &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.isLoading
                ) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        groupHeaderItems(
            uiState = uiState,
            onSwitchTab = onSwitchTab,
            onToggleMembership = onToggleMembership,
        )

        when {
            uiState.isLoading && uiState.items.isEmpty() -> item(key = "items_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            uiState.itemsError != null && uiState.items.isEmpty() -> item(key = "items_error") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Failed to load posts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = uiState.itemsError ,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }

            uiState.items.isEmpty() -> item(key = "items_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                items(uiState.items, key = { it.stableKey }) { item ->
                    when (item) {
                        is FapFolderFeedItem.Post -> {
                            val post = item.post
                            FapFolderInteractivePostCard(
                                post = post,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                onClickPost = {
                                    val slugOrId = post.slug ?: post.postId.toString()
                                    onOpenPost(slugOrId)
                                },
                                onClickHashtag = onOpenHashtag,
                                onClickAuthor = { author ->
                                    val groupSlug = author.groupSlug
                                    val username = author.username
                                    when {
                                        !groupSlug.isNullOrBlank() -> onOpenProfile(groupSlug)
                                        !username.isNullOrBlank() -> onOpenProfile(username)
                                    }
                                },
                                onClickVideo = { video -> onOpenVideo(post, video) },
                                onClickImage = { img, all ->
                                    val idx = all.indexOfFirst {
                                        it.photoId == img.photoId
                                    }.coerceAtLeast(0)
                                    onOpenImage(all, idx)
                                },
                                onClickPoll = { },
                                onClickLink = { },
                                onLongClickPost = { },
                            )
                        }
                        is FapFolderFeedItem.User -> FapFolderUserRowCard(
                            user = item.user,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            onClick = {
                                if (item.user.username.isNotBlank()) {
                                    onOpenProfile(item.user.username)
                                }
                            },
                        )
                        is FapFolderFeedItem.Community -> Unit
                    }
                }
                if (uiState.isLoadingMore) {
                    item(key = "items_more_loading") {
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
    }
}

@Composable
private fun GroupMembersTab(
    uiState: FapFolderGroupUiState,
    onSwitchTab: (FapFolderGroupTab) -> Unit,
    onToggleMembership: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState: LazyListState = rememberLazyListState()

    LaunchedEffect(listState, uiState.membersHasMore, uiState.membersLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 &&
                    last >= total - 4 &&
                    uiState.membersHasMore &&
                    !uiState.membersLoading
                ) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        groupHeaderItems(
            uiState = uiState,
            onSwitchTab = onSwitchTab,
            onToggleMembership = onToggleMembership,
        )

        when {
            uiState.membersLoading && uiState.members.isEmpty() ->
                item(key = "members_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

            uiState.membersError != null && uiState.members.isEmpty() ->
                item(key = "members_error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Failed to load members",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }
                }

            uiState.members.isEmpty() -> item(key = "members_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No members to show.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                items(uiState.members, key = { memberKey(it) }) { member ->
                    FapFolderUserRowCard(
                        user = member,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onClick = {
                            val username = member.username
                            if (username.isNotBlank()) onOpenProfile(username)
                        },
                    )
                }
                if (uiState.membersLoading) {
                    item(key = "members_more_loading") {
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
    }
}

@Composable
private fun GroupMediaTab(
    uiState: FapFolderGroupUiState,
    onSwitchTab: (FapFolderGroupTab) -> Unit,
    onToggleMembership: () -> Unit,
    onOpenImage: (List<FapFolderMedia.Image>, Int) -> Unit,
    onOpenVideoBySlug: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val isPhotos = uiState.activeTab == FapFolderGroupTab.PHOTOS
    val gridState: LazyGridState = rememberLazyGridState()
    val columns = if (isPhotos) 3 else 2

    LaunchedEffect(
        gridState,
        uiState.hasMore,
        uiState.isLoadingMore,
        uiState.isLoading,
        uiState.photoTiles.size,
        uiState.videoTiles.size,
    ) {
        snapshotFlow {
            val info = gridState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }
            .distinctUntilChanged()
            .conflate()
            .collect { (total, last) ->
                if (total > 6 &&
                    last >= total - 6 &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.isLoading
                ) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 6.dp,
            end = 6.dp,
            top = 6.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groupHeaderItems(
            uiState = uiState,
            onSwitchTab = onSwitchTab,
            onToggleMembership = onToggleMembership,
        )

        if (isPhotos) {
            when {
                uiState.isLoading && uiState.photoTiles.isEmpty() ->
                    item(
                        key = "photos_loading",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                uiState.itemsError != null && uiState.photoTiles.isEmpty() ->
                    item(
                        key = "photos_error",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Failed to load photos",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = uiState.itemsError ,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = onRetry) { Text("Retry") }
                            }
                        }
                    }

                uiState.photoTiles.isEmpty() ->
                    item(
                        key = "photos_empty",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No photos yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                else -> {
                    val images = uiState.mediaImages
                    gridItems(
                        items = uiState.photoTiles,
                        key = { "p_${it.photoId ?: it.fullUrl.hashCode()}" },
                    ) { tile ->
                        val idx = uiState.photoTiles.indexOf(tile).coerceAtLeast(0)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenImage(images, idx) },
                        ) {
                            RetryableImage(
                                model = tile.previewUrl ?: tile.fullUrl,
                                contentDescription = tile.alt,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = painterResource(R.drawable.h_chan_loading_small),
                                error = painterResource(R.drawable.h_chan_load_failed_small),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    if (uiState.isLoadingMore) {
                        item(
                            key = "photos_more_loading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
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
        } else {
            when {
                uiState.isLoading && uiState.videoTiles.isEmpty() ->
                    item(
                        key = "videos_loading",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                uiState.itemsError != null && uiState.videoTiles.isEmpty() ->
                    item(
                        key = "videos_error",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Failed to load videos",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = uiState.itemsError ,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = onRetry) { Text("Retry") }
                            }
                        }
                    }

                uiState.videoTiles.isEmpty() ->
                    item(
                        key = "videos_empty",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No videos yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                else -> {
                    gridItems(
                        items = uiState.videoTiles,
                        key = { "v_${it.postId ?: it.postUrl.hashCode()}" },
                    ) { tile ->
                        VideoTileBox(
                            tile = tile,
                            onClick = {
                                val slugOrId = tile.postId?.toString()
                                    ?: tile.postUrl.trimEnd('/').substringAfterLast('/')
                                onOpenVideoBySlug(slugOrId)
                            },
                        )
                    }
                    if (uiState.isLoadingMore) {
                        item(
                            key = "videos_more_loading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
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
        }
    }
}

@Composable
private fun VideoTileBox(
    tile: FapFolderProfileParser.VideoTile,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        val poster = tile.thumbnailUrl
        if (!poster.isNullOrBlank()) {
            RetryableImage(
                model = poster,
                contentDescription = tile.title,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.h_chan_loading_small),
                error = painterResource(R.drawable.h_chan_load_failed_small),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun MembershipButton(
    membership: GroupMembership,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val label = when (membership) {
        GroupMembership.NOT_MEMBER, GroupMembership.UNKNOWN -> "Join"
        GroupMembership.PENDING -> "Pending"
        GroupMembership.MEMBER -> "Following"
        GroupMembership.ADMIN -> "Admin"
        GroupMembership.OWNER -> "Owner"
    }
    val enabled = !loading && membership != GroupMembership.ADMIN &&
            membership != GroupMembership.OWNER

    if (membership == GroupMembership.MEMBER || membership == GroupMembership.PENDING) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(label)
            }
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(label)
            }
        }
    }
}

private fun memberKey(user: FapFolderUserRow): String =
    "member_${user.uid ?: user.username}"
