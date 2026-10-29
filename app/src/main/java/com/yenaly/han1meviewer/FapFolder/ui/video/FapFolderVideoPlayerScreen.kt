package com.yenaly.han1meviewer.FapFolder.ui.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryRepo
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private data class DoubleTapSeek(val deltaMs: Long, val direction: Int)

private const val SEEK_STEP_MS = 10_000L

@Composable
fun FapFolderVideoPlayerScreen(
    video: FapFolderMedia.Video,
    title: String,
    onDismiss: () -> Unit,
    postId: Long? = null,
    coverUrl: String? = null,
    postPermalinkForReferer: String? = null,
    onExpired: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val playerKey = video.videoId?.toString() ?: video.playUrl
    val stored = remember(playerKey) { FapFolderProgressStore.get(playerKey) }

    val initialAspect = (video.aspectRatio ?: (16.0 / 9.0)).toFloat()
        .takeIf { it > 0f } ?: (16f / 9f)
    val videoIsLandscape = initialAspect >= 1f

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(stored?.first ?: 0L) }
    var duration by remember { mutableLongStateOf(stored?.second ?: 0L) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var fullscreenLandscape by remember { mutableStateOf(videoIsLandscape) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var resumePrompted by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var pendingResumeMs by remember { mutableLongStateOf(0L) }
    var completionReported by remember { mutableStateOf(false) }
    var expiryHandled by remember { mutableStateOf(false) }

    var doubleTapSeek by remember { mutableStateOf<DoubleTapSeek?>(null) }
    var doubleTapJob by remember { mutableStateOf<Job?>(null) }

    var resolvedUrl by remember(reloadToken) { mutableStateOf<String?>(null) }
    var resolvingError by remember(reloadToken) { mutableStateOf<String?>(null) }

    LaunchedEffect(video.playUrl, video.expirationEpoch) {
        if (expiryHandled) return@LaunchedEffect
        if (video.isExpired() && onExpired != null) {
            expiryHandled = true
            playbackError = "Video link expired. Refreshing…"
            onExpired()
        }
    }

    LaunchedEffect(video.playUrl, reloadToken) {
        if (video.playUrl.isBlank()) {
            resolvingError = "Empty video URL"
            return@LaunchedEffect
        }
        resolvedUrl = null
        resolvingError = null
        isBuffering = true

        val looksLikePlayApi = video.playUrl.contains("/video/play")
        if (!looksLikePlayApi) {
            resolvedUrl = video.playUrl.replace("&amp;", "&")
            return@LaunchedEffect
        }

        var lastError: String? = null
        FapFolderNetworkRepo.resolveVideoPlayUrl(video.playUrl).collect { state ->
            when (state) {
                is WebsiteState.Loading -> Unit
                is WebsiteState.Success -> {
                    resolvedUrl = state.info
                }
                is WebsiteState.Error -> {
                    lastError = state.throwable.message
                }
            }
        }
        if (resolvedUrl == null) {
            resolvingError = lastError ?: "Could not resolve video URL"
            playbackError = resolvingError
            isBuffering = false
        }
    }

    LaunchedEffect(playerKey, postId) {
        if (resumePrompted) return@LaunchedEffect
        resumePrompted = true
        if (postId == null || postId <= 0L) return@LaunchedEffect

        val shouldOffer = runCatching {
            FapFolderHistoryRepo.shouldOfferResume(postId)
        }.getOrDefault(false)
        val existing = runCatching {
            FapFolderHistoryRepo.getByPostId(postId)
        }.getOrNull()

        val resumeMs = when {
            shouldOffer -> existing?.lastPosition ?: 0L
            else -> 0L
        }

        if (resumeMs > 0L) {
            pendingResumeMs = resumeMs
            showResumeDialog = true
        }
    }

    DisposableEffect(resolvedUrl, reloadToken) {
        val playableUrl = resolvedUrl ?: return@DisposableEffect onDispose { }
        val player = ExoPlayer.Builder(context).build().apply {

            val playHost = runCatching { playableUrl.toHttpUrlOrNull()?.host }
                .getOrNull().orEmpty()
            val isR2 = playHost.contains("cloudflarestorage.com", ignoreCase = true)

            val referer = postPermalinkForReferer ?: FapFolderConstants.HEADER_REFERER

            val requestHeaders = buildMap {
                put("referer", referer)
                put("origin", FapFolderConstants.HEADER_ORIGIN)
                put("user-agent", FapFolderConstants.USER_AGENT_FAPFOLDER)
                put("accept", "*/*")
                put("accept-language", FapFolderConstants.ACCEPT_LANGUAGE)
                put("accept-encoding", "identity")
                put("range", "bytes=0-")
                if (!isR2) {
                    val cookieHeader = runCatching { FapFolderNetwork.snapshotCookies() }
                        .getOrDefault("")
                    if (cookieHeader.isNotBlank()) put("cookie", cookieHeader)
                }
            }

            val httpFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(requestHeaders)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(FapFolderConstants.USER_AGENT_FAPFOLDER)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            val mediaItem = MediaItem.Builder()
                .setUri(playableUrl)
                .setMimeType("video/mp4")
                .build()
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
        exoPlayer = player
        isBuffering = true
        playbackError = null

        val resumeAt = if (showResumeDialog) 0L else pendingResumeMs
        if (resumeAt > 0L) {
            player.seekTo(resumeAt)
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    val d = player.duration
                    if (d > 0) duration = d
                }
                if (state == Player.STATE_ENDED && !completionReported) {
                    completionReported = true
                    reportCompletion(postId, player, scope)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val expired = video.isExpired() ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                if (expired && onExpired != null && !expiryHandled) {
                    expiryHandled = true
                    playbackError = "Video link expired. Refreshing…"
                    isBuffering = false
                    onExpired()
                } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    playbackError = "Video link expired. Refreshing…"
                    isBuffering = false
                    if (onExpired != null && !expiryHandled) {
                        expiryHandled = true
                        onExpired()
                    } else {
                        reloadToken++
                    }
                } else {
                    playbackError = error.message ?: "Playback error"
                    isBuffering = false
                }
            }
        })

        onDispose {
            doubleTapJob?.cancel()
            val currentPosition = runCatching { player.currentPosition }.getOrDefault(0L)
            val currentDuration = runCatching { player.duration }.getOrDefault(0L)
            if (currentPosition > 0L) {
                FapFolderProgressStore.put(playerKey, currentPosition, currentDuration)
            }
            runCatching { player.release() }
            exoPlayer = null
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, playerKey, postId) {
        val player = exoPlayer ?: return@LaunchedEffect
        var lastSavedAt = 0L
        var lastReportedPosition = -1L
        while (isActive) {
            delay(500)
            if (player.isPlaying) {
                val currentPos = runCatching { player.currentPosition }.getOrDefault(0L)
                val currentDur = runCatching { player.duration }.getOrDefault(0L)
                position = currentPos
                if (currentDur > 0) duration = currentDur
                FapFolderProgressStore.put(playerKey, currentPos, currentDur)

                val now = System.currentTimeMillis()
                val progressedEnough =
                    currentPos - lastReportedPosition >= 1000L ||
                            lastReportedPosition < 0L
                if (postId != null && postId > 0L &&
                    now - lastSavedAt >= 1000L &&
                    progressedEnough
                ) {
                    lastSavedAt = now
                    lastReportedPosition = currentPos
                    runCatching {
                        FapFolderHistoryRepo.savePlaybackProgress(
                            postId = postId,
                            positionMs = currentPos,
                            durationMs = currentDur,
                            isPlaying = player.isPlaying,
                            completed = false,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
            showControls = true
        }
    }

    fun seekTo(ms: Long) {
        exoPlayer?.seekTo(ms)
        position = ms
    }

    fun seekBy(deltaMs: Long) {
        val player = exoPlayer ?: return
        val dur = runCatching { player.duration }.getOrDefault(0L)
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)
        player.seekTo(target)
        position = target
        showControls = true
    }

    fun flashDoubleTapSeek(direction: Int) {
        doubleTapSeek = DoubleTapSeek(SEEK_STEP_MS, direction)
        doubleTapJob?.cancel()
        doubleTapJob = scope.launch {
            delay(700)
            doubleTapSeek = null
        }
    }

    fun applyFullscreenOrientation(landscape: Boolean, force: Boolean) {
        val requested = when {
            force && landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            force && !landscape -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        activity?.requestedOrientation = requested
        fullscreenLandscape = landscape
    }

    fun enterFullscreen() {
        if (isFullscreen) return
        applyFullscreenOrientation(videoIsLandscape, force = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        isFullscreen = true
        showControls = true
    }

    fun exitFullscreen() {
        if (!isFullscreen) return
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.show(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        isFullscreen = false
    }

    fun toggleFullscreenOrientation(landscape: Boolean) {
        if (!isFullscreen) {
            applyFullscreenOrientation(landscape, force = true)
            enterFullscreen()
        } else {
            applyFullscreenOrientation(landscape, force = true)
        }
        showControls = true
    }

    BackHandler(enabled = isFullscreen) { exitFullscreen() }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val playerContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        val surfaceView = android.view.SurfaceView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                        addView(surfaceView)
                        exoPlayer?.setVideoSurfaceView(surfaceView)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    val existingSurface = (view as? ViewGroup)
                        ?.getChildAt(0) as? android.view.SurfaceView
                    if (existingSurface != null) {
                        exoPlayer?.setVideoSurfaceView(existingSurface)
                    }
                },
                onRelease = { view ->
                    exoPlayer?.setVideoSurfaceView(null)
                    (view as? ViewGroup)?.removeAllViews()
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (playbackError == null) {
                                    showControls = !showControls
                                }
                            },
                            onDoubleTap = { offset ->
                                if (playbackError != null) return@detectTapGestures
                                val forward = offset.x >= size.width / 2f
                                seekBy(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
                                flashDoubleTapSeek(if (forward) 1 else -1)
                            },
                        )
                    },
            )

            doubleTapSeek?.let { seek ->
                val alignment = if (seek.direction > 0) Alignment.CenterEnd
                else Alignment.CenterStart
                Box(
                    modifier = Modifier
                        .align(alignment)
                        .padding(horizontal = 32.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (seek.direction > 0) Icons.Filled.FastForward
                            else Icons.Filled.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${if (seek.direction > 0) "+" else "-"}10s",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (playbackError != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = playbackError!!,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        playbackError = null
                        expiryHandled = false
                        reloadToken++
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            } else if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            if (showControls && playbackError == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(8.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        exitFullscreen()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    IconButton(onClick = { toggleFullscreenOrientation(false) }) {
                        Icon(
                            imageVector = Icons.Filled.ScreenRotation,
                            contentDescription = "Force portrait",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { toggleFullscreenOrientation(true) }) {
                        Icon(
                            imageVector = Icons.Filled.ScreenRotation,
                            contentDescription = "Force landscape",
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    IconButton(
                        onClick = { seekBy(-SEEK_STEP_MS) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    IconButton(
                        onClick = { togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp),
                        )
                    }

                    IconButton(
                        onClick = { seekBy(SEEK_STEP_MS) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .then(
                            if (isFullscreen) Modifier.navigationBarsPadding() else Modifier
                        )
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime(position),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(52.dp),
                        )
                        Slider(
                            value = if (duration > 0) position.toFloat() / duration else 0f,
                            onValueChange = { v -> seekTo((v * duration).toLong()) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(52.dp),
                        )
                        IconButton(onClick = {
                            if (isFullscreen) exitFullscreen() else enterFullscreen()
                        }) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit
                                else Icons.Filled.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { exitFullscreen() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            playerContent()
        }
    } else {
        Surface(color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            (video.aspectRatio ?: (16.0 / 9.0)).toFloat()
                                .takeIf { it > 0f } ?: (16f / 9f)
                        ),
                ) {
                    playerContent()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                    val meta = buildList {
                        video.durationSeconds?.let { add(formatTime(it * 1000L)) }
                        video.fileExtension?.let { add(it.uppercase()) }
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = resolvedUrl ?: video.playUrl,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = {
                showResumeDialog = false
                pendingResumeMs = 0L
            },
            title = { Text("Resume playback?") },
            text = {
                Text("Resume from ${formatTime(pendingResumeMs)} or start from the beginning?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    val target = pendingResumeMs
                    pendingResumeMs = 0L
                    runCatching { exoPlayer?.seekTo(target) }
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    pendingResumeMs = 0L
                    runCatching { exoPlayer?.seekTo(0L) }
                }) { Text("Start over") }
            },
        )
    }
}

private fun reportCompletion(
    postId: Long?,
    player: ExoPlayer,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (postId == null || postId <= 0L) return
    val position = runCatching { player.currentPosition }.getOrDefault(0L)
    val duration = runCatching { player.duration }.getOrDefault(0L)
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            FapFolderHistoryRepo.savePlaybackProgress(
                postId = postId,
                positionMs = position,
                durationMs = duration,
                isPlaying = false,
                completed = true,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
