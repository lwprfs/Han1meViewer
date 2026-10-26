package com.yenaly.han1meviewer.OnAir.ui.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirCookieManager
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun OnAirVideoPlayer(
    state: OnAirPlayerState,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPositionUpdate: (Long, Long) -> Unit,
    onQualityChange: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onRetryQuality: (String) -> Unit = { onQualityChange(it) },
    onPlayerReady: (ExoPlayer) -> Unit = {},
    onTokenExpired: (() -> Unit)? = null,
    onStreamUnavailable: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var rebuffering by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val isExoReady = playbackState == Player.STATE_READY
    val url = state.activeUrl
    val isBuffering = url != null && (
            (!hasRenderedFirstFrame && !isExoReady) ||
                    rebuffering ||
                    playbackState == Player.STATE_BUFFERING
            )
    val activeStream = state.activeStream
    val streamError = activeStream?.state == StreamState.Error
    val noStream = url.isNullOrBlank() && !state.isSwitchLoading

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect

        val base = OnAirNetwork.baseUrl
        val cookieHeader = OnAirCookieManager.cookiesRaw
        val mediaHeaders = mutableMapOf(
            "Referer" to base,
            "Origin" to base.trimEnd('/'),
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        )
        if (cookieHeader.isNotBlank()) {
            mediaHeaders["Cookie"] = cookieHeader
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mediaHeaders)
        val dsFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaItem = androidx.media3.common.MediaItem.Builder().setUri(url).build()
        val isHls = state.activeStream?.isHls == true || url.contains(".m3u8")
        val mediaSource = if (isHls) {
            HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
        }

        val existing = exoPlayer
        if (existing == null) {
            val player = ExoPlayer.Builder(context).build()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.setPlaybackSpeed(state.speed)
            player.playWhenReady = false
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(newState: Int) {
                    playbackState = newState
                    rebuffering = newState == Player.STATE_BUFFERING && hasRenderedFirstFrame
                    onPositionUpdate(player.currentPosition, player.duration)
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                    rebuffering = false
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    showControls = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    val code = error.errorCode
                    val msg = error.cause?.message.orEmpty()
                    val whole = "${error.errorCodeName} $msg".lowercase()

                    val isForbidden = whole.contains("403") ||
                            whole.contains("401") ||
                            whole.contains("unauthorized") ||
                            whole.contains("forbidden")

                    val isUnsupported =
                        code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                                code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                                code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
                                whole.contains("extractor") ||
                                whole.contains("could read the stream") ||
                                whole.contains("unrecognized")

                    when {
                        isForbidden -> onTokenExpired?.invoke()
                        isUnsupported -> onStreamUnavailable?.invoke(state.currentQuality)
                        else -> onStreamUnavailable?.invoke(state.currentQuality)
                    }
                }
            })
            exoPlayer = player
            currentUrl = url
            onPlayerReady(player)
        } else if (currentUrl != url) {
            val wasPlaying = existing.playWhenReady
            val pos = existing.currentPosition
            existing.stop()
            existing.setMediaSource(mediaSource)
            existing.prepare()
            if (pos > 0) existing.seekTo(pos)
            existing.playWhenReady = wasPlaying
            currentUrl = url
            hasRenderedFirstFrame = false
            rebuffering = false
        }
    }

    LaunchedEffect(state.playbackIntent, state.activeUrl, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        val shouldPlay = state.playbackIntent == PlaybackIntent.Playing &&
                !state.isSwitchLoading &&
                state.activeUrl != null
        if (player.playWhenReady != shouldPlay) {
            player.playWhenReady = shouldPlay
        }
    }

    LaunchedEffect(state.speed, exoPlayer) {
        exoPlayer?.setPlaybackSpeed(state.speed)
    }

    LaunchedEffect(exoPlayer, isExoReady, state.playbackIntent) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (state.playbackIntent != PlaybackIntent.Playing) {
            onPositionUpdate(player.currentPosition, player.duration)
            return@LaunchedEffect
        }
        while (isActive) {
            delay(500)
            val pos = player.currentPosition
            val dur = player.duration
            onPositionUpdate(pos, if (dur > 0) dur else 0L)
        }
    }

    LaunchedEffect(showControls, state.playbackIntent) {
        if (showControls && state.playbackIntent == PlaybackIntent.Playing) {
            delay(3000)
            showControls = false
        }
    }

    val containerModifier = if (isFullscreen) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    }

    Box(
        modifier = containerModifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        if (exoPlayer != null && !url.isNullOrBlank()) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    val player = exoPlayer
                    if (player != null && view.childCount == 0) {
                        val surfaceView = android.view.SurfaceView(view.context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                        view.addView(surfaceView)
                        player.setVideoSurfaceView(surfaceView)
                    }
                },
                onRelease = { view ->
                    exoPlayer?.setVideoSurfaceView(null)
                    view.removeAllViews()
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showControls = !showControls })
                    },
            )

            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            if (state.isSwitchLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = "Loading ${state.pendingQuality ?: ""} stream…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (state.playbackIntent == PlaybackIntent.NotStarted && !state.isSwitchLoading && !streamError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Text(
                            text = "Play (${state.currentQuality})",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showControls && !isBuffering && !state.isSwitchLoading &&
                        !streamError &&
                        state.playbackIntent != PlaybackIntent.NotStarted,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(if (isFullscreen) 80.dp else 64.dp)
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(
                            imageVector = if (state.playbackIntent == PlaybackIntent.Playing) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 48.dp else 40.dp),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box {
                            IconButton(onClick = { showQualityMenu = true }) {
                                Text(
                                    text = state.currentQuality.ifBlank { "Quality" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            DropdownMenu(
                                expanded = showQualityMenu,
                                onDismissRequest = { showQualityMenu = false },
                            ) {
                                val ordered = QUALITY_ORDER.filter { it in state.streamsByQuality } +
                                        state.streamsByQuality.keys.filter { it !in QUALITY_ORDER }
                                ordered.forEach { q ->
                                    val stream = state.streamsByQuality[q]
                                    val isLoading = stream?.state == StreamState.Loading ||
                                            state.pendingQuality == q
                                    val isError = stream?.state == StreamState.Error
                                    val isCurrent = q == state.currentQuality
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                when {
                                                    isLoading -> CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                    )

                                                    isError -> Icon(
                                                        Icons.Filled.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp),
                                                    )

                                                    isCurrent -> Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )

                                                    else -> Spacer(Modifier.width(16.dp))
                                                }
                                                Text(qualityLabel(q))
                                            }
                                        },
                                        onClick = {
                                            showQualityMenu = false
                                            if (!isCurrent) onQualityChange(q)
                                        },
                                        enabled = !isLoading && !isCurrent,
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showSpeedMenu = true }) {
                                Text(
                                    text = "${state.speed}x",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                            ) {
                                state.availableSpeeds.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s}x") },
                                        onClick = {
                                            onSpeedChange(s)
                                            showSpeedMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = if (isFullscreen) {
                                    Icons.Filled.FullscreenExit
                                } else {
                                    Icons.Filled.Fullscreen
                                },
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = formatTime(state.position),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(52.dp),
                            )
                            Slider(
                                value = if (state.duration > 0) {
                                    state.position.toFloat() / state.duration
                                } else {
                                    0f
                                },
                                onValueChange = { v -> onSeek((v * state.duration).toLong()) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                            )
                            Text(
                                text = formatTime(state.duration),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(52.dp),
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                when {
                    streamError || state.hasError -> {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = activeStream?.errorMessage
                                ?: state.errorMessage
                                ?: "Failed to load video",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { onRetryQuality(state.currentQuality) }) {
                            Text("Retry")
                        }
                    }

                    state.isSwitchLoading -> {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = "Loading ${state.pendingQuality ?: ""} stream…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    noStream -> {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = "No stream for ${qualityLabel(state.currentQuality)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { onRetryQuality(state.currentQuality) }) {
                            Text("Retry")
                        }
                    }

                    else -> {
                        if (!coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = "Loading video…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private val QUALITY_ORDER = listOf("original", "720", "480", "360", "240", "144")

private fun qualityLabel(quality: String): String =
    if (quality == "original") "Original" else "${quality}p"

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun OnAirStreamUrlBar(
    url: String,
    quality: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(url) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(8.dp),
            )
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    clipboard.setText(AnnotatedString(url))
                    GlobalToasts.show(
                        "Stream URL copied",
                        level = GlobalToasts.ToastLevel.SUCCESS,
                    )
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = qualityLabel(quality),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (expanded) {
            SelectionContainer {
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(url))
                GlobalToasts.show(
                    "Stream URL copied",
                    level = GlobalToasts.ToastLevel.SUCCESS,
                )
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy stream URL",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
