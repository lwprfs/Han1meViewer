package com.yenaly.han1meviewer.FapFolder.ui.group

import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.platform.LocalUriHandler
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
import com.yenaly.han1meviewer.util.DownloadLayout
import com.yenaly.han1meviewer.util.FapOwnerKind
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
    onOpenVideoPost: (String) -> Unit = onOpenPost,
    viewModel: FapFolderGroupViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var photoLightboxIndex by remember { mutableStateOf<Int?>(null) }
    var lightboxPayload by remember {
        mutableStateOf<Pair<List<FapFolderMedia.Image>, Int>?>(null)
    }

    LaunchedEffect(slug, initialTab) {
        viewModel.load(slug, initialTab)
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    Text(
                        text = uiState.group?.displayName ?: slug,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { SiteBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.site_refresh),
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
                                text = stringResource(R.string.fap_failed_to_load_group),
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
                            TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.retry)) }
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
                            onOpenImage = { _, idx ->
                                if (uiState.activeTab == FapFolderGroupTab.PHOTOS) {
                                    photoLightboxIndex = idx
                                }
                            },
                            onOpenVideoBySlug = onOpenVideoPost,
                            onLoadMore = { viewModel.loadMoreItems() },
                            onRetry = { viewModel.retryLoadMoreItems() },
                        )

                        FapFolderGroupTab.POSTS -> GroupPostsTab(
                            uiState = uiState,
                            onSwitchTab = { viewModel.switchTab(it) },
                            onToggleMembership = { viewModel.joinGroup() },
                            onOpenPost = onOpenPost,
                            onOpenHashtag = onOpenHashtag,
                            onOpenProfile = onOpenProfile,
                            onOpenVideo = { post, _ ->

                                onOpenVideoPost(post.slug ?: post.postId.toString())
                            },
                            onOpenImage = { images, idx -> lightboxPayload = images to idx },
                            onLoadMore = { viewModel.loadMoreItems() },
                            onRetry = { viewModel.retryLoadMoreItems() },
                        )
                    }
                }
            }
        }
    }

    photoLightboxIndex?.let { startIndex ->
        FapFolderImageLightboxDialog(
            images = uiState.mediaImages,
            initialIndex = startIndex,
            onDismiss = { photoLightboxIndex = null },
            hasMore = uiState.hasMore,
            isLoadingMore = uiState.isLoadingMore,
            onLoadMore = { viewModel.loadMoreItems() },
            saveSegments = DownloadLayout.fapFolderPhotoSegments(
                FapOwnerKind.GROUP, uiState.group?.displayName ?: slug, null,
            ),
        )
    }

    lightboxPayload?.let { (images, startIndex) ->
        FapFolderImageLightboxDialog(
            images = images,
            initialIndex = startIndex,
            onDismiss = { lightboxPayload = null },
            saveSegments = DownloadLayout.fapFolderPhotoSegments(
                FapOwnerKind.GROUP, uiState.group?.displayName ?: slug, null,
            ),
        )
    }
}

@Composable
private fun GroupHeaderBlock(
    group: com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup,
    joining: Boolean,
    joinError: String?,
    onToggleMembership: () -> Unit,
    onSelectTab: (FapFolderGroupTab) -> Unit = {},
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
                                text = if (group.visibility == GroupVisibility.PRIVATE) stringResource(R.string.fap_private)
                                else stringResource(R.string.fap_secret),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                val followers = group.followerCount ?: group.memberCount
                if (followers != null) {
                    Text(
                        text = stringResource(R.string.fap_followers, formatCount(followers)),
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

        GroupStatsRow(group = group, onSelectTab = onSelectTab)

        GroupInfoChips(group = group)

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

private fun formatCount(value: Int): String =
    java.text.NumberFormat.getIntegerInstance().format(value)

@Composable
private fun GroupStatsRow(
    group: com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup,
    onSelectTab: (FapFolderGroupTab) -> Unit,
) {
    val followers = group.followerCount ?: group.memberCount
    val stats = listOf(
        Triple(stringResource(R.string.site_followers), followers, null as FapFolderGroupTab?),
        Triple(stringResource(R.string.fap_tab_posts), group.postCount, FapFolderGroupTab.POSTS),
        Triple(stringResource(R.string.fap_tab_photos), group.photoCount, FapFolderGroupTab.PHOTOS),
        Triple(stringResource(R.string.fap_tab_videos), group.videoCount, FapFolderGroupTab.VIDEOS),
    ).filter { it.second != null }
    if (stats.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            stats.forEach { (label, value, tab) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (tab != null) Modifier.clickable { onSelectTab(tab) }
                            else Modifier
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatCount(value ?: 0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupInfoChips(
    group: com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup,
) {
    val uriHandler = LocalUriHandler.current
    val categoryName = group.category?.name?.takeIf { it.isNotBlank() }
    if (categoryName == null && group.externalLinks.isEmpty()) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categoryName?.let { name ->
            AssistChip(onClick = {}, label = { Text(name) })
        }
        group.externalLinks.forEach { link ->
            AssistChip(
                onClick = { runCatching { uriHandler.openUri(link.url) } },
                label = { Text(link.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                onSelectTab = onSwitchTab,
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
                onSelectTab = onSwitchTab,
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
                            text = stringResource(R.string.fap_failed_to_load_posts),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = uiState.itemsError ,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
                        text = stringResource(R.string.fap_nothing_here_yet),
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
                                    text = stringResource(R.string.fap_failed_to_load_photos),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = uiState.itemsError ,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
                                text = stringResource(R.string.fap_no_photos_yet),
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
                                    text = stringResource(R.string.fap_failed_to_load_videos),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = uiState.itemsError ,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
                                text = stringResource(R.string.fap_no_videos_yet),
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
                contentDescription = stringResource(R.string.site_play),
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
        GroupMembership.NOT_MEMBER, GroupMembership.UNKNOWN -> stringResource(R.string.fap_join)
        GroupMembership.PENDING -> stringResource(R.string.creator_status_pending)
        GroupMembership.MEMBER -> stringResource(R.string.site_following)
        GroupMembership.ADMIN -> stringResource(R.string.fap_admin)
        GroupMembership.OWNER -> stringResource(R.string.fap_owner)
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
