package com.yenaly.han1meviewer.FapFolder.ui.video

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.abs

private data class DoubleTapSeek(val deltaMs: Long, val direction: Int)

private const val SEEK_STEP_MS = 10_000L
private const val MAX_RELOAD_ATTEMPTS = 3
private const val TAP_SLOP_PX = 24f
private const val DOUBLE_TAP_TIMEOUT_MS = 260L
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun isTrustedMediaHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val h = host.lowercase()
    return h == "fapfolder.club" ||
            h.endsWith(".fapfolder.club") ||
            h == "fap.onl" ||
            h.endsWith(".fap.onl")
}

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

    val initialAspect = (video.aspectRatio ?: (16.0 / 9.0))
        .toFloat().takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val videoIsLandscape = initialAspect >= 1f

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val surfaceRef = remember { mutableStateOf<SurfaceView?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isReady by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    var positionMs by remember(playerKey) {
        mutableLongStateOf(stored?.first ?: 0L)
    }
    var durationMs by remember(playerKey) {
        mutableLongStateOf(stored?.second ?: 0L)
    }

    var showControls by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var controlsInteractionTick by remember { mutableLongStateOf(0L) }
    var isFullscreen by remember { mutableStateOf(false) }
    var fullscreenLandscape by remember { mutableStateOf(videoIsLandscape) }
    var reloadToken by remember { mutableStateOf(0) }
    var reloadAttempts by remember { mutableStateOf(0) }
    var resumeDialogForPostId by remember { mutableStateOf<Long?>(null) }
    var pendingResumeMs by remember { mutableLongStateOf(0L) }
    var completionReported by remember { mutableStateOf(false) }
    var expiryHandled by remember { mutableStateOf(false) }

    var muted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    var doubleTapSeek by remember { mutableStateOf<DoubleTapSeek?>(null) }
    var doubleTapJob by remember { mutableStateOf<Job?>(null) }

    var brightnessOverlay by remember { mutableStateOf(-1f) }
    var volumeOverlay by remember { mutableStateOf(-1f) }
    var overlayJob by remember { mutableStateOf<Job?>(null) }

    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var resolvingError by remember { mutableStateOf<String?>(null) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
                is WebsiteState.Success -> resolvedUrl = state.info
                is WebsiteState.Error -> lastError = state.throwable.message
            }
        }
        if (resolvedUrl == null) {
            resolvingError = lastError ?: "Could not resolve video URL"
            playbackError = resolvingError
            isBuffering = false
        }
    }

    LaunchedEffect(playerKey, postId) {
        if (postId == null || postId <= 0L) return@LaunchedEffect
        if (resumeDialogForPostId != null) return@LaunchedEffect

        val shouldOffer = runCatching {
            FapFolderHistoryRepo.shouldOfferResume(postId)
        }.getOrDefault(false)
        val existing = runCatching {
            FapFolderHistoryRepo.getByPostId(postId)
        }.getOrNull()

        val resumeMs: Long = if (shouldOffer) {
            val raw = existing?.lastPosition ?: 0L
            if (raw > 0L) raw else 0L
        } else {
            0L
        }

        if (resumeMs > 0L) {
            pendingResumeMs = resumeMs
            resumeDialogForPostId = postId
            exoPlayer?.pause()
        }
    }

    DisposableEffect(resolvedUrl, reloadToken) {
        val playableUrl: String? = resolvedUrl
        if (playableUrl.isNullOrBlank()) {
            return@DisposableEffect onDispose { }
        }

        val playHost = runCatching { playableUrl.toHttpUrlOrNull()?.host }.getOrNull()
        val referer = postPermalinkForReferer ?: FapFolderConstants.HEADER_REFERER

        val requestHeaders = buildMap {
            put("referer", referer)
            put("origin", FapFolderConstants.HEADER_ORIGIN)
            put("user-agent", FapFolderConstants.USER_AGENT_FAPFOLDER)
            put("accept", "*/*")
            put("accept-language", FapFolderConstants.ACCEPT_LANGUAGE)
            put("accept-encoding", "identity")
            put("range", "bytes=0-")
            if (isTrustedMediaHost(playHost)) {
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

        val mimeType = video.fileExtension
            ?.takeIf { it.isNotBlank() }
            ?.let { MimeTypes.getMimeTypeFromExtension(it) }
            ?: MimeTypes.VIDEO_MP4

        val mediaItem = MediaItem.Builder()
            .setUri(playableUrl)
            .setMimeType(mimeType)
            .build()
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        val player = ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = false
            setPlaybackSpeed(playbackSpeed)
            volume = if (muted) 0f else 1f
        }
        exoPlayer = player
        isBuffering = true
        isReady = false
        playbackError = null

        surfaceRef.value?.let { player.setVideoSurfaceView(it) }

        if (resumeDialogForPostId == null && pendingResumeMs > 0L) {
            player.seekTo(pendingResumeMs)
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    val pos = safePosition(player)
                    val dur = safeDuration(player)
                    positionMs = pos
                    if (dur > 0L) durationMs = dur
                    FapFolderProgressStore.put(playerKey, pos, dur)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    isReady = true
                    val d = safeDuration(player)
                    if (d > 0L) durationMs = d
                    surfaceRef.value?.let { player.setVideoSurfaceView(it) }
                }
                if (state == Player.STATE_ENDED && !completionReported) {
                    completionReported = true
                    reportCompletion(postId, player)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val badStatus =
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                val expired = video.isExpired() || badStatus

                if (expired && onExpired != null && !expiryHandled) {
                    expiryHandled = true
                    playbackError = "Video link expired. Refreshing…"
                    isBuffering = false
                    isReady = false
                    onExpired()
                    return
                }

                if (badStatus && reloadAttempts < MAX_RELOAD_ATTEMPTS) {
                    reloadAttempts++
                    playbackError =
                        "Stream error. Retrying ($reloadAttempts/$MAX_RELOAD_ATTEMPTS)…"
                    isBuffering = false
                    isReady = false
                    reloadToken++
                    return
                }

                playbackError = if (badStatus) {
                    "Stream error. Please try again later."
                } else {
                    error.message ?: "Playback error"
                }
                isBuffering = false
                isReady = false
            }
        })

        onDispose {
            doubleTapJob?.cancel()
            val currentPosition = safePosition(player)
            val currentDuration = safeDuration(player)
            if (currentPosition > 0L) {
                FapFolderProgressStore.put(playerKey, currentPosition, currentDuration)
            }
            runCatching { player.release() }
            exoPlayer = null
            isPlaying = false
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, playerKey, postId) {
        val player = exoPlayer ?: return@LaunchedEffect
        var lastSavedAt = 0L
        var lastReportedPosition = -1L
        while (isActive) {
            delay(500)
            if (player.isPlaying) {
                val currentPos = safePosition(player)
                val currentDur = safeDuration(player)
                positionMs = currentPos
                if (currentDur > 0L) durationMs = currentDur
                FapFolderProgressStore.put(playerKey, currentPos, currentDur)

                val now = System.currentTimeMillis()
                val progressedEnough = currentPos - lastReportedPosition >= 1000L ||
                        lastReportedPosition < 0L
                if (postId != null && postId > 0L &&
                    now - lastSavedAt >= 1000L && progressedEnough
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

    LaunchedEffect(showControls, isPlaying, controlsLocked, controlsInteractionTick) {
        if (showControls && isPlaying && !controlsLocked) {
            delay(3500)
            showControls = false
        }
    }

    fun revealControls() {
        if (!showControls) showControls = true
        controlsInteractionTick = System.currentTimeMillis()
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
            revealControls()
        }
    }

    fun seekTo(ms: Long) {
        val safe = if (ms < 0L) 0L else ms
        exoPlayer?.seekTo(safe)
        positionMs = safe
    }

    fun seekBy(deltaMs: Long) {
        val player = exoPlayer ?: return
        val dur = safeDuration(player)
        val current = safePosition(player)
        val upper = if (dur > 0L) dur else Long.MAX_VALUE
        var target = current + deltaMs
        if (target < 0L) target = 0L
        if (target > upper) target = upper
        player.seekTo(target)
        positionMs = target
        revealControls()
    }

    fun toggleMute() {
        val player = exoPlayer ?: return
        muted = !muted
        player.volume = if (muted) 0f else 1f
        revealControls()
    }

    fun flashDoubleTapSeek(direction: Int) {
        doubleTapSeek = DoubleTapSeek(SEEK_STEP_MS, direction)
        doubleTapJob?.cancel()
        doubleTapJob = scope.launch {
            delay(700)
            doubleTapSeek = null
        }
    }

    fun flashOverlay(value: Float, isVolume: Boolean) {
        if (isVolume) volumeOverlay = value else brightnessOverlay = value
        overlayJob?.cancel()
        overlayJob = scope.launch {
            delay(800)
            if (isVolume) volumeOverlay = -1f else brightnessOverlay = -1f
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

    fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.show(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    fun enterFullscreen() {
        if (isFullscreen) return
        applyFullscreenOrientation(videoIsLandscape, force = false)
        hideSystemBars()
        isFullscreen = true
        revealControls()
        exoPlayer?.let { p -> surfaceRef.value?.let { p.setVideoSurfaceView(it) } }
    }

    fun exitFullscreen() {
        if (!isFullscreen) return
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemBars()
        isFullscreen = false
        fullscreenLandscape = videoIsLandscape
        exoPlayer?.let { p -> surfaceRef.value?.let { p.setVideoSurfaceView(it) } }
    }

    fun toggleFullscreenOrientation(landscape: Boolean) {
        applyFullscreenOrientation(landscape, force = true)
        if (!isFullscreen) {
            hideSystemBars()
            isFullscreen = true
        }
        revealControls()
        exoPlayer?.let { p -> surfaceRef.value?.let { p.setVideoSurfaceView(it) } }
    }

    BackHandler(enabled = isFullscreen) { exitFullscreen() }

    DisposableEffect(Unit) {
        onDispose {
            if (isFullscreen) {
                activity?.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val playerContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (!isReady || (playbackError == null && isBuffering)) {
                val poster: String? = video.posterUrl ?: coverUrl
                if (!poster.isNullOrBlank()) {
                    RetryableImage(
                        model = poster,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        surfaceRef.value = this
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) {
                                exoPlayer?.setVideoSurfaceView(this@apply)
                            }

                            override fun surfaceChanged(
                                h: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) {
                                exoPlayer?.setVideoSurfaceView(this@apply)
                            }

                            override fun surfaceDestroyed(h: SurfaceHolder) {
                                exoPlayer?.clearVideoSurfaceView(this@apply)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    val sv = view as? SurfaceView ?: return@AndroidView
                    surfaceRef.value = sv
                    exoPlayer?.setVideoSurfaceView(sv)
                },
                onRelease = { view ->
                    val sv = view as? SurfaceView
                    if (sv != null) {
                        exoPlayer?.clearVideoSurfaceView(sv)
                    }
                    if (surfaceRef.value === sv) surfaceRef.value = null
                    (view as? ViewGroup)?.removeAllChildren()
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(controlsLocked) {
                        if (controlsLocked) return@pointerInput

                        var lastTapTime = 0L
                        var dragMode = 0
                        var dragIsVolume = false
                        var dragged = false
                        var downTime = 0L
                        var downPos = Offset.Zero
                        var pendingTapJob: Job? = null

                        detectDragGestures(
                            onDragStart = { offset ->
                                dragged = false
                                dragMode = 0
                                dragIsVolume = offset.x >= size.width / 2f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (!dragged) {
                                    if (abs(dragAmount.x) < TAP_SLOP_PX &&
                                        abs(dragAmount.y) < TAP_SLOP_PX
                                    ) {
                                        return@detectDragGestures
                                    }
                                    dragged = true
                                    revealControls()
                                }
                                if (dragMode == 0) {
                                    dragMode =
                                        if (abs(dragAmount.x) > abs(dragAmount.y)) 2 else 1
                                }
                                if (dragMode == 1) {
                                    if (dragIsVolume) {
                                        val cur = audioManager
                                            ?.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            ?: 0
                                        val delta = (-dragAmount.y /
                                                size.height * maxVolume * 2f).toInt()
                                        var newVol = cur + delta
                                        if (newVol < 0) newVol = 0
                                        if (newVol > maxVolume) newVol = maxVolume
                                        audioManager?.setStreamVolume(
                                            AudioManager.STREAM_MUSIC, newVol, 0
                                        )
                                        val fraction = if (maxVolume > 0)
                                            newVol.toFloat() / maxVolume.toFloat() else 0f
                                        flashOverlay(fraction, true)
                                    } else {
                                        val window = activity?.window
                                        if (window != null) {
                                            val attrs = window.attributes
                                            val cur = if (attrs.screenBrightness < 0f)
                                                0.5f else attrs.screenBrightness
                                            var newB = cur - dragAmount.y / size.height
                                            if (newB < 0.05f) newB = 0.05f
                                            if (newB > 1f) newB = 1f
                                            attrs.screenBrightness = newB
                                            window.attributes = attrs
                                            flashOverlay(newB, false)
                                        }
                                    }
                                } else if (dragMode == 2) {
                                    val dur = safeDuration(exoPlayer)
                                    if (dur > 0L) {
                                        val delta =
                                            (dragAmount.x / size.width *
                                                    dur.toFloat()).toLong()
                                        seekBy(delta)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragged) revealControls()
                                dragMode = 0
                                dragged = false
                            },
                            onDragCancel = {
                                dragMode = 0
                                dragged = false
                            },
                        )
                    }
                    .pointerInput(controlsLocked) {
                        if (controlsLocked) return@pointerInput
                        detectTapGestures(
                            onTap = { offset ->
                                if (playbackError != null) return@detectTapGestures
                                if (showControls) {
                                    showControls = false
                                } else {
                                    revealControls()
                                }
                            },
                            onDoubleTap = { offset ->
                                if (playbackError != null) return@detectTapGestures
                                val forward = offset.x >= size.width / 2f
                                seekBy(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
                                flashDoubleTapSeek(if (forward) 1 else -1)
                                revealControls()
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
                            imageVector = if (seek.direction > 0)
                                Icons.Filled.FastForward
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

            if (brightnessOverlay >= 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Brightness ${(brightnessOverlay * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (volumeOverlay >= 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (volumeOverlay == 0f)
                                Icons.Filled.VolumeOff
                            else Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${(volumeOverlay * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
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
                        text = playbackError ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        playbackError = null
                        expiryHandled = false
                        reloadAttempts = 0
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

            if (controlsLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(8.dp),
                ) {
                    IconButton(onClick = {
                        controlsLocked = false
                        revealControls()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Unlock controls",
                            tint = Color.White,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showControls && playbackError == null && !controlsLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            if (isFullscreen) exitFullscreen()
                            onDismiss()
                        }) {
                            Icon(
                                imageVector =
                                    Icons.AutoMirrored.Filled.ArrowBack,
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
                        IconButton(onClick = { toggleMute() }) {
                            Icon(
                                imageVector = if (muted) Icons.Filled.VolumeOff
                                else Icons.Filled.VolumeUp,
                                contentDescription =
                                    if (muted) "Unmute" else "Mute",
                                tint = Color.White,
                            )
                        }
                        Box {
                            IconButton(onClick = {
                                speedMenuOpen = true
                                revealControls()
                            }) {
                                Text(
                                    text = formatSpeed(playbackSpeed),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            DropdownMenu(
                                expanded = speedMenuOpen,
                                onDismissRequest = { speedMenuOpen = false },
                            ) {
                                PLAYBACK_SPEEDS.forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = formatSpeed(speed) +
                                                        if (speed == playbackSpeed)
                                                            " ✓" else ""
                                            )
                                        },
                                        onClick = {
                                            playbackSpeed = speed
                                            exoPlayer?.setPlaybackSpeed(speed)
                                            speedMenuOpen = false
                                            revealControls()
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            controlsLocked = true
                            revealControls()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Lock controls",
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = {
                            toggleFullscreenOrientation(!fullscreenLandscape)
                        }) {
                            Icon(
                                imageVector = if (fullscreenLandscape)
                                    Icons.Filled.ScreenLockPortrait
                                else Icons.Filled.ScreenLockLandscape,
                                contentDescription = if (fullscreenLandscape)
                                    "Rotate to portrait" else "Rotate to landscape",
                                tint = Color.White,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        IconButton(
                            onClick = { seekBy(-SEEK_STEP_MS) },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    CircleShape,
                                ),
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
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    CircleShape,
                                ),
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
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    CircleShape,
                                ),
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
                                if (isFullscreen) Modifier.navigationBarsPadding()
                                else Modifier
                            )
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val safeDur = if (durationMs > 0L) durationMs else 0L
                            val safePos = if (positionMs > 0L) positionMs else 0L

                            val displayPosition = if (sliderDragging) {
                                val ratio = sliderValue.toDouble()
                                (ratio * safeDur.toDouble()).toLong()
                            } else {
                                safePos
                            }

                            Text(
                                text = formatTime(displayPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(52.dp),
                            )

                            val sliderFraction = when {
                                sliderDragging -> sliderValue
                                safeDur > 0L -> {
                                    val ratio = safePos.toFloat() /
                                            safeDur.toFloat()
                                    if (ratio < 0f) 0f
                                    else if (ratio > 1f) 1f
                                    else ratio
                                }
                                else -> 0f
                            }

                            Slider(
                                value = sliderFraction,
                                onValueChange = {
                                    sliderDragging = true
                                    sliderValue = it
                                    revealControls()
                                },
                                onValueChangeFinished = {
                                    val ratio = sliderValue.toDouble()
                                    val target =
                                        (ratio * safeDur.toDouble()).toLong()
                                    seekTo(target)
                                    sliderDragging = false
                                    revealControls()
                                },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor =
                                        MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor =
                                        Color.White.copy(alpha = 0.3f),
                                ),
                            )

                            Text(
                                text = formatTime(safeDur),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(52.dp),
                            )

                            IconButton(onClick = {
                                if (isFullscreen) exitFullscreen()
                                else enterFullscreen()
                            }) {
                                Icon(
                                    imageVector = if (isFullscreen)
                                        Icons.Filled.FullscreenExit
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
    }

    Surface(color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isFullscreen) {
                Box(modifier = Modifier.fillMaxSize()) {
                    playerContent()
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    TitleBlock(
                        title = title,
                        video = video,
                        playbackSpeed = playbackSpeed,
                        muted = muted,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(initialAspect),
                    ) {
                        playerContent()
                    }

                    StreamUrlRow(
                        url = resolvedUrl ?: video.playUrl,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                        ),
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (resumeDialogForPostId != null) {
        AlertDialog(
            onDismissRequest = {
                resumeDialogForPostId = null
                pendingResumeMs = 0L
                runCatching { exoPlayer?.seekTo(0L) }
                runCatching { exoPlayer?.play() }
            },
            title = { Text("Resume playback?") },
            text = {
                Text(
                    "Resume from ${formatTime(pendingResumeMs)} " +
                            "or start from the beginning?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = pendingResumeMs
                    resumeDialogForPostId = null
                    pendingResumeMs = 0L
                    runCatching {
                        exoPlayer?.seekTo(target)
                        exoPlayer?.play()
                    }
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    resumeDialogForPostId = null
                    pendingResumeMs = 0L
                    runCatching {
                        exoPlayer?.seekTo(0L)
                        exoPlayer?.play()
                    }
                }) { Text("Start over") }
            },
        )
    }
}

@Composable
private fun TitleBlock(
    title: String,
    video: FapFolderMedia.Video,
    playbackSpeed: Float,
    muted: Boolean,
) {
    if (title.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = buildList {
            val secs = video.durationSeconds
            if (secs != null) add(formatTime(secs.toLong() * 1000L))
            video.fileExtension?.let { ext -> add(ext.uppercase()) }
            if (playbackSpeed != 1f) add(formatSpeed(playbackSpeed))
            if (muted) add("Muted")
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamUrlRow(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("stream_url", url))
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Stream URL copied", Toast.LENGTH_SHORT)
                        .show()
                    copied = true
                },
            ),
        color = Color.White.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = url,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (copied) "Copied" else "Hold to copy",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun reportCompletion(postId: Long?, player: ExoPlayer) {
    if (postId == null || postId <= 0L) return
    val pos = safePosition(player)
    val dur = safeDuration(player)
    ioScope.launch {
        runCatching {
            FapFolderHistoryRepo.savePlaybackProgress(
                postId = postId,
                positionMs = pos,
                durationMs = dur,
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

private fun safeDuration(player: ExoPlayer?): Long {
    if (player == null) return 0L
    return try {
        val d = player.duration
        if (d > 0L) d else 0L
    } catch (_: Throwable) {
        0L
    }
}

private fun safePosition(player: ExoPlayer?): Long {
    if (player == null) return 0L
    return try {
        val p = player.currentPosition
        if (p > 0L) p else 0L
    } catch (_: Throwable) {
        0L
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x"
    else "${speed}x"
