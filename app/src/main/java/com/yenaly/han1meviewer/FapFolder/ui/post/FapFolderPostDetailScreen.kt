package com.yenaly.han1meviewer.FapFolder.ui.post

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderImageLightboxDialog
import com.yenaly.han1meviewer.ui.component.CollapsingPlayerLayout
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.util.DownloadLayout
import com.yenaly.han1meviewer.util.FapOwnerKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderPostDetailScreen(
    slugOrId: String,
    autoPlay: Boolean,
    onBack: () -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenGroupPhotos: (String) -> Unit = onOpenGroup,
    onOpenProfilePhotos: (String) -> Unit = onOpenProfile,
    viewModel: FapFolderPostDetailViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var playing by remember(slugOrId) { mutableStateOf(false) }
    var imageViewerPayload by remember {
        mutableStateOf<Pair<List<FapFolderMedia.Image>, Int>?>(null)
    }

    LaunchedEffect(slugOrId) { viewModel.load(slugOrId) }

    var autoPlayConsumed by remember(slugOrId) { mutableStateOf(false) }
    LaunchedEffect(autoPlay, uiState.post) {
        if (!autoPlay || autoPlayConsumed) return@LaunchedEffect
        if (uiState.post?.primaryVideo == null) return@LaunchedEffect
        autoPlayConsumed = true
        playing = true
    }

    LaunchedEffect(uiState.refreshToken, uiState.post) {
        val pendingId = viewModel.pendingAutoPlayVideoId() ?: return@LaunchedEffect
        val post = uiState.post ?: return@LaunchedEffect
        post.media.filterIsInstance<FapFolderMedia.Video>()
            .firstOrNull { it.videoId == pendingId || it.onPlayId == pendingId }
            ?: return@LaunchedEffect
        viewModel.clearPendingAutoPlay()
        playing = true
    }

    LaunchedEffect(listState, uiState.commentsHasMore, uiState.commentsLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .map { (total, last) -> total > 4 && last >= total - 4 }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && uiState.commentsHasMore && !uiState.commentsLoading) {
                    viewModel.loadMoreComments()
                }
            }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    Text(
                        text = if (uiState.post?.primaryVideo != null) stringResource(R.string.video) else stringResource(R.string.fap_post),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
            val post = uiState.post
            when {
                uiState.postLoading && post == null -> LoadingBlock(stringResource(R.string.fap_loading_post))

                post == null && uiState.postError != null -> PostErrorBlock(
                    title = stringResource(R.string.fap_load_post_failed),
                    message = uiState.postError ?: stringResource(R.string.site_unknown_error),
                    onRetry = { viewModel.refresh() },
                )

                post == null -> EmptyBlock(stringResource(R.string.fap_post_not_found))

                else -> {
                    val video = post.primaryVideo
                    if (video != null) {
                        CollapsingPlayerLayout(
                            modifier = Modifier.fillMaxSize(),
                            player = {
                                InlineVideoSlot(
                                    post = post,
                                    video = video,
                                    playing = playing,
                                    onPlayingChange = { playing = it },
                                    onExpired = {
                                        val pid = video.videoId ?: video.onPlayId
                                        playing = false
                                        if (pid != null) viewModel.requestAutoPlayAfterRefresh(pid)
                                        viewModel.refresh()
                                    },
                                )
                            },
                            content = {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 8.dp),
                                ) {
                                    fapFolderVideoDetailItems(
                                        post = post,
                                        video = video,
                                        slugOrId = slugOrId,
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onOpenHashtag = onOpenHashtag,
                                        onOpenGroup = onOpenGroup,
                                        onOpenProfile = onOpenProfile,
                                    )
                                }
                            },
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            fapFolderImagePostItems(
                                post = post,
                                slugOrId = slugOrId,
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenImages = { images, idx ->
                                    imageViewerPayload = images to idx
                                },
                                onOpenHashtag = onOpenHashtag,
                                onOpenGroup = onOpenGroup,
                                onOpenProfile = onOpenProfile,
                                onOpenAlbum = onOpenAlbum,
                                onOpenGroupPhotos = onOpenGroupPhotos,
                                onOpenProfilePhotos = onOpenProfilePhotos,
                                context = context,
                            )
                        }
                    }
                }
            }
        }
    }

    imageViewerPayload?.let { (images, initialIndex) ->
        val post = uiState.post
        val kind = if (post?.author?.isGroupAuthor == true) FapOwnerKind.GROUP else FapOwnerKind.PROFILE
        val owner = post?.author?.displayName
        FapFolderImageLightboxDialog(
            images = images,
            initialIndex = initialIndex,
            onDismiss = { imageViewerPayload = null },
            saveSegments = DownloadLayout.fapFolderPhotoSegments(
                kind, owner, post?.let { "Post ${it.postId}" },
            ),
        )
    }
}

@Composable
private fun LoadingBlock(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(18.dp)
                        .size(32.dp),
                    strokeWidth = 3.dp,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PostErrorBlock(title: String, message: String, onRetry: () -> Unit) {
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
                        .padding(16.dp)
                        .size(32.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun EmptyBlock(hint: String) {
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
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
