package com.yenaly.han1meviewer.OnAir.ui.video

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import com.yenaly.han1meviewer.ui.component.VideoFitState
import com.yenaly.han1meviewer.ui.component.fitVideoSurface
import com.yenaly.han1meviewer.ui.component.installVideoFit
import android.view.ViewGroup
import kotlinx.coroutines.launch
import android.content.ClipData
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AspectRatio
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
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
    player: ExoPlayer,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekConsumed: () -> Unit,
    onPositionUpdate: (Long, Long) -> Unit,
    onQualityChange: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLoop: () -> Unit,
    onToggleLock: () -> Unit,
    onEnded: () -> Unit,
    onRetryQuality: (String) -> Unit = { onQualityChange(it) },
    onTokenExpired: (() -> Unit)? = null,
    onStreamUnavailable: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    title: String = "",
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var hasRenderedFirstFrame by remember(player) {
        mutableStateOf(player.playbackState == Player.STATE_READY)
    }
    var rebuffering by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf(0f) }
    var seekHint by remember { mutableStateOf<String?>(null) }

    val currentState by rememberUpdatedState(state)
    val currentOnPositionUpdate by rememberUpdatedState(onPositionUpdate)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnTokenExpired by rememberUpdatedState(onTokenExpired)
    val currentOnStreamUnavailable by rememberUpdatedState(onStreamUnavailable)

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
    val menuOpen = showQualityMenu || showSpeedMenu

    fun bump() { interaction++ }

    // Player listener (the ExoPlayer itself is hoisted, so it survives fullscreen toggles).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(newState: Int) {
                playbackState = newState
                rebuffering = newState == Player.STATE_BUFFERING && hasRenderedFirstFrame
                currentOnPositionUpdate(player.currentPosition, player.duration)
                if (newState == Player.STATE_ENDED) currentOnEnded()
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                rebuffering = false
            }

            override fun onPlayerError(error: PlaybackException) {
                val code = error.errorCode
                val msg = error.cause?.message.orEmpty()
                val whole = "${error.errorCodeName} $msg".lowercase()
                val isForbidden = whole.contains("403") ||
                        whole.contains("401") ||
                        whole.contains("unauthorized") ||
                        whole.contains("forbidden")
                val quality = currentState.currentQuality
                if (isForbidden) currentOnTokenExpired?.invoke()
                else currentOnStreamUnavailable?.invoke(quality)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Load / switch media.
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val loaded = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (loaded == url) {
            // Same URL after a player error (e.g. Retry): the player is stuck, re-prepare it.
            if (player.playerError != null) {
                player.prepare()
                player.playWhenReady = state.playbackIntent == PlaybackIntent.Playing
            }
            return@LaunchedEffect
        }

        val base = OnAirNetwork.baseUrl
        val cookieHeader = OnAirCookieManager.cookiesRaw
        val mediaHeaders = mutableMapOf(
            "Referer" to base,
            "Origin" to base.trimEnd('/'),
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        )
        if (cookieHeader.isNotBlank()) mediaHeaders["Cookie"] = cookieHeader

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

        val startPos = if (loaded != null) player.currentPosition else state.position
        hasRenderedFirstFrame = false
        rebuffering = false
        player.setMediaSource(mediaSource, startPos.coerceAtLeast(0L))
        player.prepare()
        player.setPlaybackSpeed(state.speed)
        player.playWhenReady = state.playbackIntent == PlaybackIntent.Playing
    }

    LaunchedEffect(state.playbackIntent, state.activeUrl, state.isSwitchLoading) {
        val shouldPlay = state.playbackIntent == PlaybackIntent.Playing &&
                !state.isSwitchLoading &&
                state.activeUrl != null
        if (player.playWhenReady != shouldPlay) player.playWhenReady = shouldPlay
    }

    LaunchedEffect(state.speed) { player.setPlaybackSpeed(state.speed) }
    LaunchedEffect(state.isMuted) { player.volume = if (state.isMuted) 0f else 1f }
    LaunchedEffect(state.isLooping) {
        player.repeatMode = if (state.isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Real seeking (previously the slider only updated the stored position).
    LaunchedEffect(state.pendingSeekMs) {
        val target = state.pendingSeekMs
        if (target >= 0) {
            player.seekTo(target)
            onSeekConsumed()
        }
    }

    LaunchedEffect(player, isExoReady, state.playbackIntent) {
        if (state.playbackIntent != PlaybackIntent.Playing) {
            onPositionUpdate(player.currentPosition, player.duration)
            return@LaunchedEffect
        }
        while (isActive) {
            delay(500)
            if (!scrubbing) {
                val dur = player.duration
                onPositionUpdate(player.currentPosition, if (dur > 0) dur else 0L)
            }
        }
    }

    // Auto-hide controls; any interaction restarts the timer.
    LaunchedEffect(showControls, state.playbackIntent, interaction, menuOpen, scrubbing) {
        if (showControls && state.playbackIntent == PlaybackIntent.Playing && !menuOpen && !scrubbing) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(700)
            seekHint = null
        }
    }

    val fitState = remember { VideoFitState() }
    var stretched by rememberSaveable { mutableStateOf(false) }
    fitState.stretched = stretched && isFullscreen
    var fitFrame by remember { mutableStateOf<FrameLayout?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                fitState.updateFrom(videoSize)
                fitFrame?.let { frame ->
                    (frame.getChildAt(0))?.let { fitVideoSurface(frame, it, fitState) }
                }
            }
        }
        fitState.updateFrom(player.videoSize)
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val handlePlayPause: () -> Unit = {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
        bump()
        onPlayPause()
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
        if (!url.isNullOrBlank()) {
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
                    if (view.childCount == 0) {
                        val surfaceView = android.view.SurfaceView(view.context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                        view.addView(surfaceView)
                        player.setVideoSurfaceView(surfaceView)
                        fitFrame = view
                        installVideoFit(view, surfaceView, fitState)
                    }
                    fitState.stretched = stretched && isFullscreen
                    view.getChildAt(0)?.let { fitVideoSurface(view, it, fitState) }
                },
                onRelease = { view ->
                    (view.getChildAt(0) as? android.view.SurfaceView)?.let {
                        player.clearVideoSurfaceView(it)
                    }
                    view.removeAllViews()
                },
            )

            // Tap = toggle controls, double-tap left/right = seek -/+10s.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.isLocked) {
                        detectTapGestures(
                            onTap = { showControls = !showControls; bump() },
                            onDoubleTap = { offset ->
                                if (!state.isLocked && state.playbackIntent != PlaybackIntent.NotStarted) {
                                    if (offset.x < size.width / 2f) {
                                        onSeekBy(-SKIP_MS)
                                        seekHint = "« 10s"
                                    } else {
                                        onSeekBy(SKIP_MS)
                                        seekHint = "10s »"
                                    }
                                }
                            },
                        )
                    },
            )

            seekHint?.let { hint ->
                Text(
                    text = hint,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (isBuffering && !state.isSwitchLoading) {
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

            // Locked: only an unlock button is shown.
            AnimatedVisibility(
                visible = state.isLocked && showControls &&
                        state.playbackIntent != PlaybackIntent.NotStarted,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                IconButton(
                    onClick = { onToggleLock(); bump() },
                    modifier = Modifier
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Unlock controls", tint = Color.White)
                }
            }

            AnimatedVisibility(
                visible = showControls && !state.isLocked && !state.isSwitchLoading &&
                        !streamError &&
                        state.playbackIntent != PlaybackIntent.NotStarted,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Center: -10s / play-pause / +10s
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 32.dp else 20.dp),
                    ) {
                        IconButton(
                            onClick = { bump(); onSeekBy(-SKIP_MS) },
                            modifier = Modifier
                                .size(if (isFullscreen) 56.dp else 44.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        ) {
                            Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White)
                        }
                        IconButton(
                            onClick = handlePlayPause,
                            modifier = Modifier
                                .size(if (isFullscreen) 80.dp else 56.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                        ) {
                            Icon(
                                imageVector = if (state.playbackIntent == PlaybackIntent.Playing) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = "Play / pause",
                                tint = Color.White,
                                modifier = Modifier.size(if (isFullscreen) 48.dp else 36.dp),
                            )
                        }
                        IconButton(
                            onClick = { bump(); onSeekBy(SKIP_MS) },
                            modifier = Modifier
                                .size(if (isFullscreen) 56.dp else 44.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        ) {
                            Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White)
                        }
                    }

                    // Top bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isFullscreen) {
                            IconButton(onClick = onToggleFullscreen) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit fullscreen",
                                    tint = Color.White,
                                )
                            }
                        }
                        Text(
                            text = if (isFullscreen) title else "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isFullscreen) Spacer(Modifier.weight(1f))

                        Box {
                            IconButton(onClick = { showQualityMenu = true; bump() }) {
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
                            IconButton(onClick = { showSpeedMenu = true; bump() }) {
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
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (s == state.speed) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                } else {
                                                    Spacer(Modifier.width(16.dp))
                                                }
                                                Text("${s}x")
                                            }
                                        },
                                        onClick = {
                                            onSpeedChange(s)
                                            showSpeedMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onToggleMute(); bump() }) {
                            Icon(
                                imageVector = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (state.isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = { onToggleLoop(); bump() }) {
                            Icon(
                                imageVector = if (state.isLooping) Icons.Filled.RepeatOne
                                else Icons.Filled.Repeat,
                                contentDescription = "Loop",
                                tint = if (state.isLooping) MaterialTheme.colorScheme.primary
                                else Color.White,
                            )
                        }
                        if (isFullscreen) {
                            IconButton(onClick = { stretched = !stretched; bump() }) {
                                Icon(
                                    imageVector = Icons.Filled.AspectRatio,
                                    contentDescription = stringResource(R.string.stretch_to_fill),
                                    tint = if (stretched) MaterialTheme.colorScheme.primary
                                    else Color.White,
                                )
                            }
                            IconButton(onClick = { onToggleLock(); bump() }) {
                                Icon(
                                    Icons.Filled.LockOpen,
                                    contentDescription = "Lock controls",
                                    tint = Color.White,
                                )
                            }
                        } else {
                            IconButton(onClick = onToggleFullscreen) {
                                Icon(
                                    Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    // Bottom bar: time + seek slider
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val shownPosition = if (scrubbing && state.duration > 0) {
                                (scrubFraction * state.duration).toLong()
                            } else state.position
                            Text(
                                text = formatTime(shownPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(52.dp),
                            )
                            Slider(
                                value = if (scrubbing) scrubFraction
                                else if (state.duration > 0) {
                                    (state.position.toFloat() / state.duration).coerceIn(0f, 1f)
                                } else 0f,
                                onValueChange = { v ->
                                    scrubbing = true
                                    scrubFraction = v
                                },
                                onValueChangeFinished = {
                                    if (state.duration > 0) onSeek((scrubFraction * state.duration).toLong())
                                    scrubbing = false
                                    bump()
                                },
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

private const val SKIP_MS = 10_000L

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
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
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
                    clipboardScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("", url))
                )
            }
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
                clipboardScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("", url))
                )
            }
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
