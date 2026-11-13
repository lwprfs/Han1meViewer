package com.yenaly.han1meviewer.FapFolder.ui.video

import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.yenaly.han1meviewer.util.PlayerOrientation
import com.yenaly.han1meviewer.util.applicationContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.FrameLayout
import com.yenaly.han1meviewer.ui.component.VideoFitState
import com.yenaly.han1meviewer.ui.component.fitVideoSurface
import com.yenaly.han1meviewer.ui.component.installVideoFit
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import com.yenaly.han1meviewer.ui.component.player.StreamFallback
import com.yenaly.han1meviewer.ui.component.player.isDecoderProblem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
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
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun isTrustedMediaHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val h = host.lowercase()

    val site = FapFolderConstants.SITE_HOST
    return h == "fapfolder.club" || h.endsWith(".fapfolder.club") ||
        h == site || h.endsWith(".$site")
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

    inline: Boolean = false,

    autoStart: Boolean = false,
    minInlineAspect: Float = 0f,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
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
    var mpvUrl by remember(playerKey) { mutableStateOf<String?>(null) }
    val fitState = remember { VideoFitState().also { it.aspect = initialAspect } }
    var stretched by rememberSaveable { mutableStateOf(com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.FAPFOLDER).stretchFullscreen) }
    fitState.stretched = stretched && isFullscreen
    var fullscreenLandscape by remember { mutableStateOf(videoIsLandscape) }
    var reloadToken by remember { mutableStateOf(0) }
    var reloadAttempts by remember { mutableStateOf(0) }
    var resumeDialogForPostId by remember { mutableStateOf<Long?>(null) }
    var pendingResumeMs by remember { mutableLongStateOf(0L) }
    var completionReported by remember { mutableStateOf(false) }
    var expiryHandled by remember { mutableStateOf(false) }

    var muted by remember { mutableStateOf(false) }
    var playbackSpeed by remember {
        mutableStateOf(
            com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings
                .config(com.yenaly.han1meviewer.SiteType.FAPFOLDER).defaultSpeed
        )
    }
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
            playbackError = applicationContext.getString(R.string.fap_video_link_expired)
            onExpired()
        }
    }

    LaunchedEffect(video.playUrl, reloadToken) {
        if (video.playUrl.isBlank()) {
            resolvingError = applicationContext.getString(R.string.fap_empty_video_url)
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
            resolvingError = lastError ?: applicationContext.getString(R.string.fap_resolve_url_failed)
            playbackError = resolvingError
            isBuffering = false
        }
    }

    LaunchedEffect(playerKey, postId) {
        if (postId == null || postId <= 0L) return@LaunchedEffect
        if (resumeDialogForPostId != null) return@LaunchedEffect

        val shouldOffer = com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.FAPFOLDER).resumePlayback && runCatching {
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
        val isPlayApi = playableUrl.contains("/video/play")

        val requestHeaders = buildMap {
            put("accept", "*/*")
            put("accept-encoding", "identity")
            if (isPlayApi && isTrustedMediaHost(playHost)) {
                put("referer", referer)
                put("origin", FapFolderConstants.HEADER_ORIGIN)
                put("user-agent", FapFolderConstants.USER_AGENT_FAPFOLDER)
                put("accept-language", FapFolderConstants.ACCEPT_LANGUAGE)

            } else {

                put("referer", FapFolderConstants.HEADER_REFERER)
                put("user-agent", FapFolderConstants.USER_AGENT_FAPFOLDER)
            }
        }

        val httpFactory = OkHttpDataSource.Factory(FapFolderNetwork.playbackOkHttpClient)
            .setDefaultRequestProperties(requestHeaders)
            .setUserAgent(FapFolderConstants.USER_AGENT_FAPFOLDER)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(playableUrl)
            .build()
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        val player = com.yenaly.han1meviewer.ui.component.player.createSitePlayer(context).apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = autoStart && com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.FAPFOLDER).autoPlay &&
                    resumeDialogForPostId == null
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
                if (error.isDecoderProblem()) {

                    StreamFallback.markMpv(playableUrl)
                    player.pause()
                    isBuffering = false
                    mpvUrl = playableUrl
                    return
                }
                val badStatus =
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                val expired = video.isExpired() || badStatus

                if (expired && onExpired != null && !expiryHandled) {
                    expiryHandled = true
                    playbackError = applicationContext.getString(R.string.fap_video_link_expired)
                    isBuffering = false
                    isReady = false
                    onExpired()
                    return
                }

                if (badStatus && reloadAttempts < MAX_RELOAD_ATTEMPTS) {
                    reloadAttempts++
                    playbackError =
                        applicationContext.getString(R.string.fap_stream_error_retrying, reloadAttempts, MAX_RELOAD_ATTEMPTS)
                    isBuffering = false
                    isReady = false
                    reloadToken++
                    return
                }

                playbackError = if (badStatus) {
                    applicationContext.getString(R.string.fap_stream_error_later)
                } else {
                    error.message ?: applicationContext.getString(R.string.fap_playback_error)
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
        applyFullscreenOrientation(
            when (PlayerOrientation.mode) {
                PlayerOrientation.LANDSCAPE -> true
                PlayerOrientation.PORTRAIT -> false
                else -> videoIsLandscape
            },
            force = false,
        )
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

    BackHandler(enabled = !inline) {
        if (isFullscreen) exitFullscreen() else onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isFullscreen) {
                activity?.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                showSystemBars()
            }
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(exoPlayer) {
        val exo = exoPlayer
        val sizeListener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                fitState.updateFrom(videoSize)
                val view = surfaceRef.value
                val frame = view?.parent as? FrameLayout
                if (view != null && frame != null) fitVideoSurface(frame, view, fitState)
            }
        }
        exo?.addListener(sizeListener)
        onDispose { exo?.removeListener(sizeListener) }
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
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        val surface = SurfaceView(ctx).apply surfaceView@{
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(h: SurfaceHolder) {
                                    exoPlayer?.setVideoSurfaceView(this@surfaceView)
                                }

                                override fun surfaceChanged(
                                    h: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int,
                                ) {
                                    exoPlayer?.setVideoSurfaceView(this@surfaceView)
                                }

                                override fun surfaceDestroyed(h: SurfaceHolder) {
                                    exoPlayer?.clearVideoSurfaceView(this@surfaceView)
                                }
                            })
                        }
                        addView(surface)
                        surfaceRef.value = surface
                        installVideoFit(this, surface, fitState)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { frame ->
                    val view = frame.getChildAt(0) as? SurfaceView ?: return@AndroidView
                    surfaceRef.value = view
                    fitState.stretched = stretched && isFullscreen
                    fitVideoSurface(frame, view, fitState)
                    exoPlayer?.setVideoSurfaceView(view)
                },
                onRelease = { frame ->
                    val view = frame.getChildAt(0) as? SurfaceView
                    if (view != null) {
                        exoPlayer?.clearVideoSurfaceView(view)
                        if (surfaceRef.value === view) surfaceRef.value = null
                    }
                },
            )

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
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            if (playbackError == null) {
                com.yenaly.han1meviewer.ui.component.player.SitePlayerOverlay(
                    config = com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings
                        .config(com.yenaly.han1meviewer.SiteType.FAPFOLDER),
                    title = title,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    bufferedMs = exoPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L,
                    qualityLabel = "",
                    speed = playbackSpeed,
                    isFullscreen = isFullscreen,
                    onPlayPause = { togglePlayPause() },
                    onSeekTo = { seekTo(it) },
                    onSeekBy = { seekBy(it) },
                    onToggleFullscreen = { if (isFullscreen) exitFullscreen() else enterFullscreen() },
                    onBack = { if (isFullscreen) exitFullscreen() else onDismiss() },
                    speedOptions = PLAYBACK_SPEEDS,
                    onSpeedSelect = {
                        playbackSpeed = it
                        exoPlayer?.setPlaybackSpeed(it)
                    },
                    stretched = stretched,
                    onToggleStretch = { stretched = !stretched },
                    onTemporarySpeed = { exoPlayer?.setPlaybackSpeed(it ?: playbackSpeed) },
                    extraActions = {
                        if (onPrevious != null) {
                            IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.site_previous_video),
                                    tint = Color.White,
                                )
                            }
                        }
                        if (onNext != null) {
                            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.site_next_video),
                                    tint = Color.White,
                                )
                            }
                        }
                        IconButton(onClick = { toggleMute() }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff
                                else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (muted) stringResource(R.string.site_unmute) else stringResource(R.string.site_mute),
                                tint = Color.White,
                            )
                        }
                    },
                    onMediaPrevious = onPrevious,
                    onMediaNext = onNext,
                    onRotate = {
                        val nowLandscape = activity?.resources?.configuration?.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        toggleFullscreenOrientation(!nowLandscape)
                    },
                )
            }

            mpvUrl?.let { mpv ->
                com.yenaly.han1meviewer.ui.component.player.MpvInlinePlayer(
                    url = mpv,
                    modifier = Modifier.fillMaxSize(),
                    referer = FapFolderConstants.HEADER_REFERER,
                    startPositionMs = positionMs.coerceAtLeast(0L),
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { if (isFullscreen) exitFullscreen() else enterFullscreen() },
                    onPositionUpdate = { pos, dur ->
                        positionMs = pos
                        if (dur > 0L) durationMs = dur
                    },
                )
            }
        }
    }

    if (inline) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(maxOf(initialAspect, minInlineAspect))
                    .background(Color.Black),
            ) {
                if (!isFullscreen) playerContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            ) {
                StreamUrlRow(url = resolvedUrl ?: video.playUrl)
            }
        }
        if (isFullscreen) {
            Dialog(
                onDismissRequest = { exitFullscreen() },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                val dialogView = LocalView.current
                DisposableEffect(dialogView) {
                    val window = (dialogView.parent as? DialogWindowProvider)?.window
                    if (window != null) {
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        val controller = WindowCompat.getInsetsController(window, dialogView)
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose { }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    playerContent()
                }
            }
        }
    } else {
        Surface(color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isFullscreen) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        playerContent()
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.close),
                                    tint = Color.White,
                                )
                            }
                            if (title.isNotBlank()) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }

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
    }

    if (resumeDialogForPostId != null) {
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onDismissRequest = {
                resumeDialogForPostId = null
                pendingResumeMs = 0L
                runCatching { exoPlayer?.seekTo(0L) }
                runCatching { exoPlayer?.play() }
            },
            title = {
                Text(
                    stringResource(R.string.site_resume_playback),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.site_resume_or_start, formatTime(pendingResumeMs))
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
                }) { Text(stringResource(R.string.site_resume)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    resumeDialogForPostId = null
                    pendingResumeMs = 0L
                    runCatching {
                        exoPlayer?.seekTo(0L)
                        exoPlayer?.play()
                    }
                }) { Text(stringResource(R.string.site_start_over)) }
            },
        )
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
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("stream_url", url))
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, context.getString(R.string.site_stream_url_copied), Toast.LENGTH_SHORT)
                        .show()
                    copied = true
                },
            ),
        color = Color.White.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = url,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (copied) stringResource(R.string.site_copied) else stringResource(R.string.site_hold_to_copy),
                color = if (copied) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.5f),
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
