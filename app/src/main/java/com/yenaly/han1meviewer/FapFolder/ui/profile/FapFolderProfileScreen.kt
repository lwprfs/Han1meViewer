package com.yenaly.han1meviewer.FapFolder.ui.profile

import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderImageLightboxDialog
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderInteractivePostCard
import com.yenaly.han1meviewer.FapFolder.ui.video.FapFolderVideoPlayerScreen
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderProfileScreen(
    username: String,
    initialTab: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenHashtag: (String) -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    onOpenVideoBySlug: (String) -> Unit = onOpenPost,
    viewModel: FapFolderProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    var lightboxPayload by remember {
        mutableStateOf<Pair<List<FapFolderMedia.Image>, Int>?>(null)
    }
    var videoToPlay by remember { mutableStateOf<FapFolderMedia.Video?>(null) }
    var videoPost by remember { mutableStateOf<FapFolderPost?>(null) }

    LaunchedEffect(username, initialTab) {
        viewModel.load(username, initialTab)
    }

    LaunchedEffect(uiState.activeTab) {
        runCatching { listState.scrollToItem(0) }
        runCatching { gridState.scrollToItem(0) }
    }

    LaunchedEffect(
        listState,
        gridState,
        uiState.activeTab,
        uiState.hasMore,
        uiState.isLoadingMore,
        uiState.isLoading,
        uiState.items.size,
        uiState.photoTiles.size,
        uiState.videoTiles.size,
    ) {
        snapshotFlow {
            val isGrid = uiState.activeTab == FapFolderProfileTab.PHOTOS ||
                    uiState.activeTab == FapFolderProfileTab.VIDEOS
            val (total, last) = if (isGrid) {
                val info = gridState.layoutInfo
                info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
            } else {
                val info = listState.layoutInfo
                info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
            }
            Triple(total, last, isGrid)
        }
            .filter { it.first > 0 && it.second >= 0 }
            .conflate()
            .collect { (total, last, isGrid) ->
                val threshold = if (isGrid) 6 else 4
                if (total > threshold &&
                    last >= total - threshold &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.isLoading
                ) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    Text(
                        text = uiState.profile?.displayName ?: "@$username",
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
                uiState.isLoading && uiState.profile == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                uiState.headerError != null && uiState.profile == null -> ProfileError(
                    title = "Failed to load profile",
                    message = uiState.headerError ?: "",
                    onRetry = { viewModel.refresh() },
                )

                else -> {
                    val activeTab = uiState.activeTab
                    if (activeTab == FapFolderProfileTab.PHOTOS ||
                        activeTab == FapFolderProfileTab.VIDEOS
                    ) {
                        ProfileGridTab(
                            uiState = uiState,
                            gridState = gridState,
                            onSwitchTab = { viewModel.switchTab(it.param) },
                            onOpenImage = { images, idx -> lightboxPayload = images to idx },
                            onOpenVideo = { video ->
                                videoPost = null
                                videoToPlay = video
                            },
                            onOpenVideoBySlug = onOpenVideoBySlug,
                            onRetry = { viewModel.refresh() },
                            onToggleFollow = { viewModel.toggleFollow() },
                        )
                    } else {
                        ProfileTimelineTab(
                            uiState = uiState,
                            listState = listState,
                            onSwitchTab = { viewModel.switchTab(it.param) },
                            onOpenPost = onOpenPost,
                            onOpenHashtag = onOpenHashtag,
                            onOpenGroup = onOpenGroup,
                            onOpenAlbum = onOpenAlbum,
                            onOpenProfile = onOpenProfile,
                            onOpenVideo = { post, video ->
                                videoPost = post
                                videoToPlay = video
                            },
                            onOpenImage = { images, idx -> lightboxPayload = images to idx },
                            onRetry = { viewModel.refresh() },
                            onToggleFollow = { viewModel.toggleFollow() },
                        )
                    }
                }
            }
        }
    }

    lightboxPayload?.let { (images, startIndex) ->
        FapFolderImageLightboxDialog(
            images = images,
            initialIndex = startIndex,
            onDismiss = { lightboxPayload = null },
        )
    }

    videoToPlay?.let { video ->
        FapFolderVideoPlayerScreen(
            video = video,
            title = videoPost?.bodyPlain?.take(60)
                ?: uiState.profile?.displayName.orEmpty(),
            onDismiss = {
                videoToPlay = null
                videoPost = null
            },
            postId = videoPost?.postId,
            coverUrl = video.posterUrl,
            postPermalinkForReferer = videoPost?.permalink
                ?: uiState.profile?.tabUrls?.get("posts"),
            onExpired = {
                videoToPlay = null
                videoPost = null
            },
        )
    }
}

@Composable
private fun ProfileTimelineTab(
    uiState: FapFolderProfileUiState,
    listState: LazyListState,
    onSwitchTab: (FapFolderProfileTab) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenVideo: (FapFolderPost, FapFolderMedia.Video) -> Unit,
    onOpenImage: (List<FapFolderMedia.Image>, Int) -> Unit,
    onRetry: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        uiState.profile?.let { profile ->
            item(key = "profile_header") {
                ProfileHeader(
                    profile = profile,
                    following = uiState.following,
                    followPending = uiState.followPending,
                    followError = uiState.followError,
                    onToggleFollow = onToggleFollow,
                    onOpenTab = onSwitchTab,
                )
            }
            item(key = "profile_tabs") {
                ProfileTabRow(
                    activeTab = uiState.activeTab,
                    onSelect = onSwitchTab,
                )
            }
        }

        when {
            uiState.isLoading && uiState.items.isEmpty() -> item("items_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            uiState.itemsError != null && uiState.items.isEmpty() -> item("items_error") {
                ProfileError(
                    title = "Failed to load",
                    message = uiState.itemsError ,
                    onRetry = onRetry,
                    compact = true,
                )
            }

            uiState.items.isEmpty() -> item("items_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No posts yet.",
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
                                    val slug = post.slug ?: post.postId.toString()
                                    onOpenPost(slug)
                                },
                                onClickHashtag = onOpenHashtag,
                                onClickAuthor = { author ->
                                    val groupSlug = author.groupSlug
                                    val user = author.username
                                    when {
                                        !groupSlug.isNullOrBlank() -> onOpenGroup(groupSlug)
                                        !user.isNullOrBlank() -> onOpenProfile(user)
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
                        is FapFolderFeedItem.User -> {
                            com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderUserRowCard(
                                user = item.user,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                onClick = {
                                    if (item.user.username.isNotBlank()) {
                                        onOpenProfile(item.user.username)
                                    }
                                },
                            )
                        }
                        is FapFolderFeedItem.Community -> {
                            com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderGroupRowCard(
                                group = item.group,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                onClick = {
                                    if (item.group.slug.isNotBlank()) {
                                        onOpenGroup(item.group.slug)
                                    }
                                },
                            )
                        }
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
private fun ProfileGridTab(
    uiState: FapFolderProfileUiState,
    gridState: LazyGridState,
    onSwitchTab: (FapFolderProfileTab) -> Unit,
    onOpenImage: (List<FapFolderMedia.Image>, Int) -> Unit,
    onOpenVideo: (FapFolderMedia.Video) -> Unit,
    onOpenVideoBySlug: (String) -> Unit,
    onRetry: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val gridItems: List<GridTile> = when (uiState.activeTab) {
        FapFolderProfileTab.PHOTOS -> buildPhotoTiles(uiState)
        FapFolderProfileTab.VIDEOS -> buildVideoTiles(uiState)
        else -> emptyList()
    }

    val columns = if (uiState.activeTab == FapFolderProfileTab.VIDEOS) 2 else 3

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
        uiState.profile?.let { profile ->
            item(
                key = "profile_header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column {
                    ProfileHeader(
                        profile = profile,
                        following = uiState.following,
                        followPending = uiState.followPending,
                        followError = uiState.followError,
                        onToggleFollow = onToggleFollow,
                        onOpenTab = onSwitchTab,
                    )
                    ProfileTabRow(
                        activeTab = uiState.activeTab,
                        onSelect = onSwitchTab,
                    )
                }
            }
        }

        when {
            uiState.isLoading && gridItems.isEmpty() -> item(
                key = "grid_loading",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            uiState.itemsError != null && gridItems.isEmpty() -> item(
                key = "grid_error",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                ProfileError(
                    title = "Failed to load",
                    message = uiState.itemsError ,
                    onRetry = onRetry,
                    compact = true,
                )
            }

            gridItems.isEmpty() -> item(
                key = "grid_empty",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (uiState.activeTab) {
                            FapFolderProfileTab.PHOTOS -> "No photos yet."
                            FapFolderProfileTab.VIDEOS -> "No videos yet."
                            else -> "Nothing here yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                items(gridItems, key = { it.key }) { tile ->
                    when (tile) {
                        is GridTile.Photo -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onOpenImage(tile.all, tile.index)
                                    },
                            ) {
                                RetryableImage(
                                    model = tile.photo.previewUrl ?: tile.photo.fullUrl,
                                    contentDescription = tile.photo.alt,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholder = painterResource(
                                        R.drawable.h_chan_loading_small
                                    ),
                                    error = painterResource(
                                        R.drawable.h_chan_load_failed_small
                                    ),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        is GridTile.Video -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpenVideo(tile.video) },
                            ) {
                                val poster = tile.video.posterUrl
                                if (!poster.isNullOrBlank()) {
                                    RetryableImage(
                                        model = poster,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholder = painterResource(
                                            R.drawable.h_chan_loading_small
                                        ),
                                        error = painterResource(
                                            R.drawable.h_chan_load_failed_small
                                        ),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.scrim
                                                .copy(alpha = 0.55f)
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                                tile.video.durationSeconds?.let { secs ->
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.scrim
                                            .copy(alpha = 0.65f),
                                    ) {
                                        Text(
                                            text = formatDuration(secs),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        is GridTile.VideoTile -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val slugOrId = tile.postId?.toString()
                                            ?: tile.postUrl.trimEnd('/')
                                                .substringAfterLast('/')
                                        onOpenVideoBySlug(slugOrId)
                                    },
                            ) {
                                val poster = tile.thumbnailUrl
                                if (!poster.isNullOrBlank()) {
                                    RetryableImage(
                                        model = poster,
                                        contentDescription = tile.title,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholder = painterResource(
                                            R.drawable.h_chan_loading_small
                                        ),
                                        error = painterResource(
                                            R.drawable.h_chan_load_failed_small
                                        ),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.scrim
                                                .copy(alpha = 0.55f)
                                        ),
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
                    }
                }

                if (uiState.isLoadingMore) {
                    item(
                        key = "grid_more_loading",
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

private sealed interface GridTile {
    val key: String

    data class Photo(
        val photo: FapFolderMedia.Image,
        val all: List<FapFolderMedia.Image>,
        val index: Int,
    ) : GridTile {
        override val key: String =
            "photo_${photo.photoId ?: photo.fullUrl.hashCode()}"
    }

    data class Video(val video: FapFolderMedia.Video) : GridTile {
        override val key: String =
            "video_${video.videoId ?: video.playUrl.hashCode()}"
    }

    data class VideoTile(
        val postId: Long?,
        val postUrl: String,
        val thumbnailUrl: String?,
        val title: String?,
    ) : GridTile {
        override val key: String = "videotile_${postId ?: postUrl.hashCode()}"
    }
}

private fun buildPhotoTiles(uiState: FapFolderProfileUiState): List<GridTile> {
    val images = uiState.mediaImages
    return images.mapIndexed { index, photo ->
        GridTile.Photo(photo = photo, all = images, index = index)
    }
}

private fun buildVideoTiles(uiState: FapFolderProfileUiState): List<GridTile> =
    uiState.videoTiles.map { tile ->
        GridTile.VideoTile(
            postId = tile.postId,
            postUrl = tile.postUrl,
            thumbnailUrl = tile.thumbnailUrl,
            title = tile.title,
        )
    }

@Composable
private fun ProfileTabRow(
    activeTab: FapFolderProfileTab,
    onSelect: (FapFolderProfileTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FapFolderProfileTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(tab) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: FapFolderUserProfile,
    following: Boolean,
    followPending: Boolean,
    followError: String?,
    onToggleFollow: () -> Unit,
    onOpenTab: (FapFolderProfileTab) -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        )
                    ),
            ) {
                profile.coverUrl?.let { cover ->
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to surface.copy(alpha = 0.85f),
                            )
                        ),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .offset(y = 40.dp)
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(surface)
                    .padding(4.dp)
                    .clip(CircleShape),
            ) {
                val avatar = profile.avatarUrl
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = profile.displayName,
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
                                text = profile.displayName.firstOrNull()?.uppercase()
                                    ?: "?",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!profile.isSelf) {
                if (following) {
                    OutlinedButton(
                        onClick = onToggleFollow,
                        enabled = !followPending,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        if (followPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Following", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Button(
                        onClick = onToggleFollow,
                        enabled = !followPending,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        if (followPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Follow", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (!profile.bio.isNullOrBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!profile.joinedLabel.isNullOrBlank()) {
            Text(
                text = "Joined ${profile.joinedLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        if (!followError.isNullOrBlank()) {
            Text(
                text = followError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        ProfileCountsRow(
            profile = profile,
            onOpenTab = onOpenTab,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ProfileCountsRow(
    profile: FapFolderUserProfile,
    onOpenTab: (FapFolderProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = profile.counts()
    if (counts.isEmpty) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileCountItem(
            label = "Followers",
            value = counts.followers,
            modifier = Modifier.weight(1f),
            onClick = null,
        )
        ProfileCountItem(
            label = "Posts",
            value = counts.posts,
            modifier = Modifier.weight(1f),
            onClick = { onOpenTab(FapFolderProfileTab.TIMELINE) },
        )
        ProfileCountItem(
            label = "Photos",
            value = counts.photos,
            modifier = Modifier.weight(1f),
            onClick = { onOpenTab(FapFolderProfileTab.PHOTOS) },
        )
        ProfileCountItem(
            label = "Videos",
            value = counts.videos,
            modifier = Modifier.weight(1f),
            onClick = { onOpenTab(FapFolderProfileTab.VIDEOS) },
        )
    }
}

@Composable
private fun ProfileCountItem(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    val displayValue = value?.let(::formatCount) ?: "—"
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatCount(value: Int): String =
    when {
        value >= 1_000_000 -> {
            val v = value / 1_000_000f
            val s = String.format("%.1f", v)
            (if (s.endsWith(".0")) s.dropLast(2) else s) + "M"
        }
        value >= 1_000 -> {
            val v = value / 1_000f
            val s = String.format("%.1f", v)
            (if (s.endsWith(".0")) s.dropLast(2) else s) + "K"
        }
        else -> value.toString()
    }

@Composable
private fun ProfileError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    compact: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(if (compact) 12.dp else 16.dp)
                        .size(if (compact) 24.dp else 32.dp),
                )
            }
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}


