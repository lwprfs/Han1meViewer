package com.yenaly.han1meviewer.OnAir.ui.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoDetail
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoTag
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.rememberRandomLoadingHint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirVideoScreen(
    videoId: String,
    preferredQuality: String?,
    viewModel: OnAirVideoViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onLoadFailure: (String) -> Unit = {},
) {
    val state by viewModel.pageState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val showResumeBanner by viewModel.showResumeBanner.collectAsStateWithLifecycle()
    val resumePosition by viewModel.resumePosition.collectAsStateWithLifecycle()

    LaunchedEffect(videoId, preferredQuality) {
        viewModel.loadVideo(videoId, preferredQuality)
    }

    LaunchedEffect(playerState.playbackIntent) {
        if (playerState.playbackIntent == PlaybackIntent.Playing) {
            viewModel.persistProgress()
            viewModel.markCompletedIfNeeded()
        }
    }

    LaunchedEffect(playerState.playbackIntent) {
        if (playerState.playbackIntent != PlaybackIntent.Playing) return@LaunchedEffect
        while (isActive) {
            delay(10_000L)
            viewModel.persistProgress()
            viewModel.markCompletedIfNeeded()
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is VideoLoadingState.Error) {
            onLoadFailure(s.throwable.message ?: "Video failed")
        }
    }

    BackHandler(enabled = playerState.isFullscreen) {
        viewModel.setFullscreen(false)
    }

    val playPauseToggle: () -> Unit = {
        when (playerState.playbackIntent) {
            PlaybackIntent.NotStarted -> viewModel.startPlayback()
            PlaybackIntent.Playing -> viewModel.pausePlayback()
            PlaybackIntent.Paused -> viewModel.resumePlayback()
        }
    }

    val navigateToVideoAndCancel: (String) -> Unit = { id ->
        viewModel.cancelPendingExtraction()
        if (id != videoId) onOpenVideo(id)
    }

    if (playerState.isFullscreen) {
        Dialog(
            onDismissRequest = { viewModel.setFullscreen(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val cover = (state as? VideoLoadingState.Success)?.info
                ?.recommendedVideos?.firstOrNull()?.thumbUrl
            OnAirVideoPlayer(
                state = playerState,
                isFullscreen = true,
                onPlayPause = playPauseToggle,
                onSeek = { viewModel.onPositionUpdate(it, playerState.duration) },
                onPositionUpdate = viewModel::onPositionUpdate,
                onQualityChange = viewModel::selectQuality,
                onSpeedChange = viewModel::setSpeed,
                onToggleFullscreen = { viewModel.setFullscreen(false) },
                onRetryQuality = { viewModel.selectQuality(it) },
                onStreamUnavailable = viewModel::markStreamUnavailable,
                onTokenExpired = {
                    viewModel.markStreamUnavailable(
                        playerState.currentQuality,
                        "Session expired — sign in again to refresh stream URLs.",
                    )
                },
                coverUrl = cover,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? VideoLoadingState.Success)?.info?.title ?: "Video"
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelPendingExtraction()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val s = state) {
            is VideoLoadingState.Loading -> LoadingContent(
                modifier = Modifier.padding(paddingValues),
                message = rememberRandomLoadingHint(),
            )

            is VideoLoadingState.Error -> ErrorContent(
                message = s.throwable.message ?: "Failed to load",
                onRetry = { viewModel.loadVideo(videoId, preferredQuality) },
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.NoContent -> ErrorContent(
                message = "Video not found",
                onRetry = { viewModel.loadVideo(videoId, preferredQuality) },
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.Success -> {
                val detail = s.info
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    if (!playerState.isFullscreen) {
                        OnAirVideoPlayer(
                            state = playerState,
                            isFullscreen = false,
                            onPlayPause = playPauseToggle,
                            onSeek = { viewModel.onPositionUpdate(it, playerState.duration) },
                            onPositionUpdate = viewModel::onPositionUpdate,
                            onQualityChange = viewModel::selectQuality,
                            onSpeedChange = viewModel::setSpeed,
                            onToggleFullscreen = { viewModel.setFullscreen(true) },
                            onRetryQuality = { viewModel.selectQuality(it) },
                            onStreamUnavailable = viewModel::markStreamUnavailable,
                            onTokenExpired = {
                                viewModel.markStreamUnavailable(
                                    playerState.currentQuality,
                                    "Session expired — sign in again to refresh stream URLs.",
                                )
                            },
                            coverUrl = detail.recommendedVideos.firstOrNull()?.thumbUrl,
                        )

                        val activeUrl = playerState.activeUrl
                        if (!activeUrl.isNullOrBlank()) {
                            OnAirStreamUrlBar(
                                url = activeUrl,
                                quality = playerState.currentQuality,
                            )
                        }
                    }

                    if (showResumeBanner) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Resume from ${formatMillis(resumePosition)}?",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = {
                                    viewModel.applyResume()
                                    viewModel.startPlayback()
                                }) { Text("Resume") }
                                TextButton(onClick = {
                                    viewModel.startFromBeginning()
                                    viewModel.startPlayback()
                                }) { Text("Start Over") }
                            }
                        }
                    }

                    VideoDetailContent(
                        detail = detail,
                        onOpenUser = onOpenUser,
                        onOpenTag = onOpenTag,
                        onOpenVideo = navigateToVideoAndCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoDetailContent(
    detail: OnAirVideoDetail,
    onOpenUser: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item(key = "title") {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item(key = "uploader") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = detail.uploaderName?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    detail.uploaderName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.clickable {
                                val path = detail.uploaderUrl?.trimEnd('/')?.substringAfterLast('/')
                                if (!path.isNullOrBlank()) onOpenUser(path)
                            },
                        )
                    }
                    val meta = buildList {
                        detail.viewsRaw?.let { add(it) }
                        detail.duration?.let { add(it) }
                        detail.fileSize?.let { add(it) }
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                detail.favoriteCount?.let {
                    FilledTonalButton(onClick = {}) {
                        Text("☆ $it")
                    }
                }
            }
        }

        if (detail.tags.isNotEmpty()) {
            item(key = "tags") {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detail.tags.forEach { tag: OnAirVideoTag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = { onOpenTag(tag.name) },
                        ) {
                            Text(
                                text = "${tag.name} ${tag.score}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        if (detail.recommendedVideos.isNotEmpty()) {
            item(key = "recommended_header") {
                Text(
                    text = "More videos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(detail.recommendedVideos.size, key = { i -> "rec_$i" }) { idx ->
                val rec = detail.recommendedVideos[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenVideo(rec.videoId) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                    ) {
                        AsyncImage(
                            model = rec.thumbUrl,
                            contentDescription = rec.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rec.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        rec.uploaderName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    rec.duration?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
