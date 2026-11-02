package com.yenaly.han1meviewer.OnAir.ui.video

import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.yenaly.han1meviewer.OnAir.common.OnAirUrlUtils
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoDetail
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.component.SiteVideoActionBar
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoTag
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
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

    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    DisposableEffect(playerState.isFullscreen) {
        val activity = context.findActivity()
        if (playerState.isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (playerState.isFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (viewModel.playerState.value.playbackIntent == PlaybackIntent.Playing) {
                    viewModel.pausePlayback()
                }
                viewModel.persistProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistProgress()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setSpeed(
            com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings
                .config(com.yenaly.han1meviewer.SiteType.ONAIR).defaultSpeed
        )
    }

    val onAirConfig = com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.ONAIR)
    var autoKicked by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId, playerState.activeUrl, playerState.playbackIntent, state) {
        if (!onAirConfig.autoPlay || playerState.playbackIntent != PlaybackIntent.NotStarted) return@LaunchedEffect
        if (state !is VideoLoadingState.Success) return@LaunchedEffect
        if (showResumeBanner && onAirConfig.resumePlayback) return@LaunchedEffect
        if (!autoKicked || !playerState.activeUrl.isNullOrBlank()) {
            autoKicked = true
            delay(400)
            viewModel.startPlayback()
        }
    }

    LaunchedEffect(videoId) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    LaunchedEffect(videoId, preferredQuality) {
        viewModel.loadVideo(videoId, preferredQuality)
    }

    LaunchedEffect(playerState.playbackIntent) {
        if (playerState.playbackIntent != PlaybackIntent.NotStarted) {
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
            val view = LocalView.current
            DisposableEffect(view) {
                val window = (view.parent as? DialogWindowProvider)?.window
                if (window != null) {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val controller = WindowInsetsControllerCompat(window, view)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }
            val info = (state as? VideoLoadingState.Success)?.info
            OnAirVideoPlayer(
                state = playerState,
                isFullscreen = true,
                player = exoPlayer,
                title = info?.title.orEmpty(),
                onPlayPause = playPauseToggle,
                onSeek = viewModel::seekTo,
                onSeekBy = viewModel::seekBy,
                onSeekConsumed = viewModel::consumeSeek,
                onPositionUpdate = viewModel::onPositionUpdate,
                onQualityChange = viewModel::selectQuality,
                onSpeedChange = viewModel::setSpeed,
                onToggleFullscreen = { viewModel.setFullscreen(false) },
                onToggleMute = viewModel::toggleMute,
                onToggleLoop = viewModel::toggleLoop,
                onToggleLock = viewModel::toggleLock,
                onEnded = viewModel::onPlaybackEnded,
                onRetryQuality = { viewModel.selectQuality(it) },
                onStreamUnavailable = viewModel::markStreamUnavailable,
                onTokenExpired = {
                    viewModel.markStreamUnavailable(
                        playerState.currentQuality,
                        "Session expired — sign in again to refresh stream URLs.",
                    )
                },
                coverUrl = info?.recommendedVideos?.firstOrNull()?.thumbUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
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
            is VideoLoadingState.Loading -> SiteLoadingContent(
                modifier = Modifier.padding(paddingValues),
                message = rememberRandomLoadingHint(),
            )

            is VideoLoadingState.Error -> SiteErrorContent(
                message = s.throwable.message ?: "Failed to load",
                onRetry = { viewModel.loadVideo(videoId, preferredQuality) },
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.NoContent -> SiteErrorContent(
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
                            player = exoPlayer,
                            title = detail.title,
                            onBack = onBack,
                            onPlayPause = playPauseToggle,
                            onSeek = viewModel::seekTo,
                            onSeekBy = viewModel::seekBy,
                            onSeekConsumed = viewModel::consumeSeek,
                            onPositionUpdate = viewModel::onPositionUpdate,
                            onQualityChange = viewModel::selectQuality,
                            onSpeedChange = viewModel::setSpeed,
                            onToggleFullscreen = { viewModel.setFullscreen(true) },
                            onToggleMute = viewModel::toggleMute,
                            onToggleLoop = viewModel::toggleLoop,
                            onToggleLock = viewModel::toggleLock,
                            onEnded = viewModel::onPlaybackEnded,
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

                    if (showResumeBanner && com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.ONAIR).resumePlayback) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = "Resume from ${formatMillis(resumePosition)}?",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = {
                                    viewModel.startFromBeginning()
                                    viewModel.startPlayback()
                                }) { Text("Restart") }
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.applyResume()
                                        viewModel.startPlayback()
                                    },
                                    shape = RoundedCornerShape(50),
                                ) { Text("Resume") }
                            }
                        }
                    }

                    SiteVideoActionBar(
                        site = SiteType.ONAIR,
                        videoCode = videoId,
                        title = detail.title,
                        coverUrl = detail.recommendedVideos.firstOrNull()?.thumbUrl,
                        webUrl = OnAirUrlUtils.normalizeVideoUrl(videoId),
                        downloadUrl = playerState.activeUrl,
                        downloadQuality = playerState.currentQuality,
                        resolveDownloadUrl = {

                            val quality = playerState.currentQuality.ifBlank { "original" }
                            com.yenaly.han1meviewer.OnAir.data.remote.OnAirStreamFetcher
                                .fetch(videoId, quality)?.url
                        },
                    )

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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        }

        item(key = "uploader") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = detail.uploaderName != null) {
                            val path = detail.uploaderUrl?.trimEnd('/')?.substringAfterLast('/')
                            if (!path.isNullOrBlank()) onOpenUser(path)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = detail.uploaderName?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        detail.uploaderName?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                        FilledTonalButton(
                            onClick = {},
                            shape = RoundedCornerShape(50),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 14.dp,
                                vertical = 6.dp,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("$it", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (detail.tags.isNotEmpty()) {
            item(key = "tags") {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.tags.forEach { tag: OnAirVideoTag ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = { onOpenTag(tag.name) },
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    end = 6.dp,
                                    top = 5.dp,
                                    bottom = 5.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                        .copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        text = "${tag.score}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(
                                            horizontal = 7.dp,
                                            vertical = 1.dp,
                                        ),
                                    )
                                }
                            }
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
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                )
            }
            items(detail.recommendedVideos.size, key = { i -> "rec_$i" }) { idx ->
                val rec = detail.recommendedVideos[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenVideo(rec.videoId) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 140.dp, height = 80.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = rec.thumbUrl,
                            contentDescription = rec.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        rec.duration?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rec.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        rec.uploaderName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatMillis(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
