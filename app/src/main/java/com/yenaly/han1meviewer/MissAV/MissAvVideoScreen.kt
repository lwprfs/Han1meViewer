package com.yenaly.han1meviewer.MissAV

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

internal const val QUALITY_UNAVAILABLE = "Unavailable"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MissAvVideoScreen(
    videoCode: String,
    path: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    viewModel: MissAvViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val videoState by viewModel.videoFlow.collectAsStateWithLifecycle()
    val historyViewModel: MissAvHistoryViewModel = viewModel()
    val normalizedPath = if (path.startsWith("/")) path else "/$path"

    var playerStarted by remember { mutableStateOf(false) }
    var capturedUrl by remember { mutableStateOf("") }
    var isExtractingUrl by remember { mutableStateOf(false) }
    var extractionFailed by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var selectedQuality by remember { mutableStateOf("720P") }
    var isFullscreen by remember { mutableStateOf(false) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var hasSubtitle by remember { mutableStateOf(false) }
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var currentSubtitleText by remember { mutableStateOf("") }
    val availableSpeeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var subtitleTextView by remember { mutableStateOf<TextView?>(null) }
    var wasPlayed by remember { mutableStateOf(false) }
    var isFirstPlay by remember { mutableStateOf(true) }
    var showResumeButton by remember { mutableStateOf(false) }
    var savedPosition by remember { mutableStateOf(0L) }
    var historyInitialized by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity

    val qualityMap = remember(capturedUrl) {
        if (capturedUrl.isBlank()) {
            emptyMap()
        } else {
            val resolutionSegment = Regex("/(\\d+)p/")
            if (resolutionSegment.containsMatchIn(capturedUrl)) {
                val qualities = listOf("360p", "480p", "720p", "1080p")
                linkedMapOf<String, String>().apply {
                    qualities.forEach { quality ->
                        put(
                            quality.uppercase(),
                            capturedUrl.replace(resolutionSegment, "/$quality/")
                        )
                    }
                }
            } else {
                linkedMapOf(QUALITY_UNAVAILABLE to capturedUrl)
            }
        }
    }

    val currentUrl = remember(selectedQuality, qualityMap) {
        qualityMap[selectedQuality] ?: qualityMap.values.firstOrNull() ?: ""
    }

    LaunchedEffect(qualityMap) {
        if (qualityMap.isNotEmpty() && selectedQuality !in qualityMap.keys) {
            selectedQuality = qualityMap.keys.first()
        }
    }

    val currentVideoInfo = remember(videoState) {
        (videoState as? VideoLoadingState.Success)?.info
    }

    val playerListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val player = exoPlayer ?: return
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration
                    currentPosition = player.currentPosition
                    updateSubtitleForPosition(
                        currentPosition,
                        subtitleCues,
                        subtitleTextView,
                        currentSubtitleText,
                        onNewText = { currentSubtitleText = it }
                    )
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val player = exoPlayer ?: return
                currentPosition = player.currentPosition
                duration = player.duration
                updateSubtitleForPosition(
                    currentPosition,
                    subtitleCues,
                    subtitleTextView,
                    currentSubtitleText,
                    onNewText = { currentSubtitleText = it }
                )
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                val player = exoPlayer ?: return
                currentPosition = player.currentPosition
                duration = player.duration
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        player?.addListener(playerListener)
        onDispose { player?.removeListener(playerListener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.let { p ->
                p.setVideoSurfaceView(null)
                p.stop()
                p.release()
            }
            exoPlayer = null
            webViewRef?.let { wv ->
                wv.stopLoading()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            webViewRef = null
        }
    }

    fun loadSubtitles(uri: Uri) {
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists() || file.length() == 0L) {
                    Toast.makeText(context, "Subtitle file missing or empty", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            val cues = SubtitleParser.parseSRT(uri, context)
            if (cues.isNotEmpty()) {
                subtitleCues = cues
                hasSubtitle = true
                subtitleUri = uri
                val pos = exoPlayer?.currentPosition ?: 0L
                updateSubtitleForPosition(pos, cues, subtitleTextView, currentSubtitleText) {
                    currentSubtitleText = it
                }
                Toast.makeText(context, "Loaded ${cues.size} subtitles", Toast.LENGTH_SHORT).show()
            } else {
                subtitleCues = emptyList()
                hasSubtitle = false
                subtitleUri = null
                Toast.makeText(context, "No subtitles found", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            subtitleCues = emptyList()
            hasSubtitle = false
            subtitleUri = null
            Toast.makeText(context, "Failed to load subtitle: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun clearSubtitle() {
        subtitleCues = emptyList()
        hasSubtitle = false
        subtitleUri = null
        currentSubtitleText = ""
        subtitleTextView?.text = ""
        subtitleTextView?.visibility = android.view.View.GONE
        Toast.makeText(context, "Subtitle removed", Toast.LENGTH_SHORT).show()
    }

    val subtitleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
                if (ext == "srt") {
                    subtitleUri = uri
                    loadSubtitles(uri)
                } else {
                    Toast.makeText(context, "Only .srt is supported", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openSubtitlePicker() {
        subtitleFilePickerLauncher.launch(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("text/srt", "application/x-subrip", "text/plain")
                )
            }
        )
    }

    fun enterFullscreen() {
        if (isFullscreen) return
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
        showControls = true
    }

    fun changeQuality(quality: String) {
        if (selectedQuality == quality) return
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val wasPlaying = exoPlayer?.playWhenReady ?: true
        val savedSubtitleUri = subtitleUri
        val savedSubtitleCues = subtitleCues

        selectedQuality = quality
        val newUrl = qualityMap[quality] ?: return

        exoPlayer?.let { player ->
            val baseUrl = Preferences.missAvBaseUrl
            val dataSourceFactory = DefaultDataSource.Factory(
                context,
                DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                    hashMapOf(
                        "Referer" to "$baseUrl/",
                        "Origin" to baseUrl.trimEnd('/')
                    )
                )
            )
            val mediaItem = androidx.media3.common.MediaItem.Builder().setUri(newUrl).build()
            val mediaSource = if (newUrl.contains(".m3u8")) {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else {
                androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            player.setMediaSource(mediaSource)
            player.prepare()
            player.seekTo(currentPos)
            player.setPlaybackSpeed(currentSpeed)
            player.playWhenReady = wasPlaying

            if (savedSubtitleUri != null && savedSubtitleCues.isNotEmpty()) {
                subtitleCues = savedSubtitleCues
                hasSubtitle = true
                subtitleUri = savedSubtitleUri
                updateSubtitleForPosition(
                    currentPos,
                    savedSubtitleCues,
                    subtitleTextView,
                    currentSubtitleText,
                ) { currentSubtitleText = it }
            }
        }
    }

    fun performSkip(deltaMillis: Long) {
        exoPlayer?.let { player ->
            val pos = player.currentPosition
            val dur = player.duration

            val target = if (dur > 0 && dur != androidx.media3.common.C.TIME_UNSET) {
                (pos + deltaMillis).coerceIn(0L, dur)
            } else {
                (pos + deltaMillis).coerceAtLeast(0L)
            }

            val wasPlaying = player.playWhenReady
            player.seekTo(target)
            currentPosition = target

            if (!wasPlaying) {
                player.playWhenReady = true
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    exoPlayer?.let { if (!it.isPlaying) it.playWhenReady = false }
                }, 250L)
            }
        }
    }

    LaunchedEffect(normalizedPath) {
        subtitleCues = emptyList()
        hasSubtitle = false
        subtitleUri = null
        currentSubtitleText = ""
        subtitleTextView?.visibility = android.view.View.GONE

        viewModel.getVideoDetail(normalizedPath)
        wasPlayed = false
        isFirstPlay = true
        showResumeButton = false
        savedPosition = 0L
        historyInitialized = false
        isPlaying = false
        playerStarted = false

        MissAvHistoryRepo.getByVideoCode(videoCode)?.let { history ->
            if (history.lastPosition > 5000) {
                savedPosition = history.lastPosition
                showResumeButton = true
            }
        }
    }

    LaunchedEffect(videoState) {
        val info = (videoState as? VideoLoadingState.Success)?.info
        if (info != null && info.title.isNotBlank() && !historyInitialized) {
            historyInitialized = true
            historyViewModel.updateWatchHistory(
                videoCode = videoCode,
                title = info.title,
                coverUrl = info.coverUrl,
                currentPosition = 0L,
                totalDuration = 0L,
                isPlaying = false,
                wasPlayed = false
            )
        }
    }

    LaunchedEffect(currentPosition, subtitleCues) {
        if (subtitleCues.isNotEmpty()) {
            val cue = subtitleCues.findLast { currentPosition >= it.startTime }
            currentSubtitleText = cue?.takeIf { currentPosition <= it.endTime }?.text ?: ""
        } else {
            currentSubtitleText = ""
        }
    }

    LaunchedEffect(currentSubtitleText) {
        subtitleTextView?.let { tv ->
            tv.text = currentSubtitleText
            tv.visibility = if (currentSubtitleText.isNotBlank())
                android.view.View.VISIBLE else android.view.View.GONE
            tv.invalidate()
        }
    }

    LaunchedEffect(capturedUrl) {
        if (capturedUrl.isNotBlank() && isExtractingUrl) {
            isExtractingUrl = false
            playerStarted = true
            webViewRef?.let { wv ->
                wv.stopLoading()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            webViewRef = null
        }
    }

    LaunchedEffect(isExtractingUrl) {
        if (isExtractingUrl) {
            delay(15000)
            if (isExtractingUrl) {
                if (capturedUrl.isNotBlank()) {
                    isExtractingUrl = false
                    playerStarted = true
                } else {
                    isExtractingUrl = false
                    extractionFailed = true
                }
                webViewRef?.let { wv ->
                    wv.stopLoading()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
                webViewRef = null
            }
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            delay(200)
            if (player.playWhenReady) {
                currentPosition = player.currentPosition
                duration = player.duration
                if (hasSubtitle) {
                    updateSubtitleForPosition(
                        currentPosition, subtitleCues, subtitleTextView, currentSubtitleText
                    ) { currentSubtitleText = it }
                }
            }
        }
    }

    LaunchedEffect(isPlaying, exoPlayer, currentVideoInfo) {
        val player = exoPlayer ?: return@LaunchedEffect
        val info = currentVideoInfo ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect

        wasPlayed = true
        isFirstPlay = false
        showResumeButton = false

        while (isActive) {
            delay(2000)
            val total = player.duration
            if (total > 0) {
                historyViewModel.updateWatchHistory(
                    videoCode = videoCode,
                    title = info.title,
                    coverUrl = info.coverUrl,
                    currentPosition = player.currentPosition,
                    totalDuration = total,
                    isPlaying = player.playWhenReady,
                    wasPlayed = wasPlayed
                )
            }
        }
    }

    BackHandler(enabled = isFullscreen) { exitFullscreen() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (hasSubtitle) clearSubtitle() else openSubtitlePicker()
                    }) {
                        Icon(
                            if (hasSubtitle) Icons.Filled.ClosedCaption else Icons.Filled.Subtitles,
                            contentDescription = "Subtitle",
                            tint = if (hasSubtitle) Color.Green else Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = videoState) {
            is VideoLoadingState.Loading -> LoadingContent(modifier = Modifier.padding(paddingValues))

            is VideoLoadingState.Success -> {
                val info = state.info
                val baseUrl = Preferences.missAvBaseUrl
                val videoPageUrl = baseUrl.trimEnd('/') + normalizedPath

                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (isFullscreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        )
                    } else {
                        MissAvVideoPlayer(
                            playerStarted = playerStarted,
                            currentUrl = currentUrl,
                            isExtractingUrl = isExtractingUrl,
                            extractionFailed = extractionFailed,
                            capturedUrl = capturedUrl,
                            videoPageUrl = videoPageUrl,
                            coverUrl = info.coverUrl,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition,
                            duration = duration,
                            showControls = showControls,
                            isFullscreen = false,
                            hasSubtitle = hasSubtitle,
                            qualityMap = qualityMap,
                            selectedQuality = selectedQuality,
                            availableSpeeds = availableSpeeds,
                            currentSpeed = currentSpeed,
                            showSpeedMenu = showSpeedMenu,
                            showQualityMenu = showQualityMenu,
                            exoPlayer = exoPlayer,
                            subtitleTextView = subtitleTextView,
                            showResumeButton = showResumeButton,
                            savedPosition = savedPosition,
                            playerListener = playerListener,
                            onResumeFromSaved = {
                                exoPlayer?.seekTo(savedPosition)
                                showResumeButton = false
                                isPlaying = true
                                exoPlayer?.playWhenReady = true
                                showControls = true
                                wasPlayed = true
                                isFirstPlay = false
                            },
                            onStartFromBeginning = {
                                exoPlayer?.seekTo(0L)
                                showResumeButton = false
                                isPlaying = true
                                exoPlayer?.playWhenReady = true
                                showControls = true
                                wasPlayed = true
                                isFirstPlay = false
                            },
                            onPlayerCreated = { player ->
                                exoPlayer = player
                                val dsFactory = DefaultDataSource.Factory(
                                    context,
                                    DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                                        hashMapOf(
                                            "Referer" to "$baseUrl/",
                                            "Origin" to baseUrl.trimEnd('/')
                                        )
                                    )
                                )
                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                    .setUri(currentUrl).build()
                                val mediaSource = if (currentUrl.contains(".m3u8")) {
                                    HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
                                } else {
                                    androidx.media3.exoplayer.source.ProgressiveMediaSource
                                        .Factory(dsFactory).createMediaSource(mediaItem)
                                }
                                player.setMediaSource(mediaSource)
                                player.prepare()
                                player.setPlaybackSpeed(currentSpeed)
                                if (savedPosition > 0) player.seekTo(savedPosition)
                                if (isPlaying) player.playWhenReady = true
                            },
                            onSubtitleTextViewCreated = { tv -> subtitleTextView = tv },
                            onSurfaceReleased = { },
                            onPlayPause = {
                                exoPlayer?.let { player ->
                                    if (player.playWhenReady) {
                                        player.playWhenReady = false
                                        isPlaying = false
                                    } else {
                                        player.playWhenReady = true
                                        isPlaying = true
                                        showControls = true
                                        if (showResumeButton) {
                                            showResumeButton = false
                                            wasPlayed = true
                                            isFirstPlay = false
                                        }
                                    }
                                }
                            },
                            onSeek = { position ->
                                exoPlayer?.seekTo(position)
                                currentPosition = position
                            },
                            onSkip = { deltaMillis -> performSkip(deltaMillis) },
                            onToggleControls = { showControls = !showControls },
                            onSpeedChange = { speed ->
                                currentSpeed = speed
                                exoPlayer?.setPlaybackSpeed(speed)
                                showSpeedMenu = false
                                showControls = true
                            },
                            onQualityChange = { quality ->
                                changeQuality(quality)
                                showQualityMenu = false
                                showControls = true
                            },
                            onToggleFullscreen = {
                                if (isFullscreen) exitFullscreen() else enterFullscreen()
                            },
                            onExitFullscreen = { exitFullscreen() },
                            onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                            onToggleQualityMenu = { showQualityMenu = !showQualityMenu },
                            onDismissSpeedMenu = { showSpeedMenu = false },
                            onDismissQualityMenu = { showQualityMenu = false },
                            onSubtitleToggle = {
                                if (hasSubtitle) clearSubtitle() else openSubtitlePicker()
                            },
                            onPlayClick = {
                                capturedUrl = ""
                                extractionFailed = false
                                isExtractingUrl = true
                                if (savedPosition > 0) showResumeButton = true
                            },
                            onRetryExtraction = {
                                extractionFailed = false
                                capturedUrl = ""
                                isExtractingUrl = true
                            },
                            onPositionUpdate = { position, total ->
                                currentPosition = position
                                duration = total
                                if (hasSubtitle) {
                                    updateSubtitleForPosition(
                                        position, subtitleCues, subtitleTextView, currentSubtitleText
                                    ) { currentSubtitleText = it }
                                }
                            },
                            webViewRef = webViewRef,
                            onWebViewRefChange = { webViewRef = it },
                            onUrlCaptured = { url ->
                                if (capturedUrl.isBlank()) capturedUrl = url
                            }
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            MissAvVideoDetails(
                                info = info,
                                onNavigateToSearch = onNavigateToSearch,
                                onSubtitleDownloaded = { uri ->
                                    subtitleUri = uri
                                    loadSubtitles(uri)
                                    currentPosition = exoPlayer?.currentPosition ?: 0L
                                },
                                context = context
                            )
                        }
                    }
                }
            }

            is VideoLoadingState.Error -> ErrorContent(
                message = state.throwable.message ?: "Failed",
                onRetry = { viewModel.getVideoDetail(normalizedPath) },
                modifier = Modifier.padding(paddingValues)
            )

            is VideoLoadingState.NoContent -> ErrorContent(
                message = "No content",
                onRetry = { viewModel.getVideoDetail(normalizedPath) },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    if (isFullscreen && videoState is VideoLoadingState.Success) {
        val info = (videoState as VideoLoadingState.Success).info
        val baseUrl = Preferences.missAvBaseUrl
        val videoPageUrl = baseUrl.trimEnd('/') + normalizedPath

        Dialog(
            onDismissRequest = { exitFullscreen() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                MissAvVideoPlayer(
                    playerStarted = playerStarted,
                    currentUrl = currentUrl,
                    isExtractingUrl = isExtractingUrl,
                    extractionFailed = extractionFailed,
                    capturedUrl = capturedUrl,
                    videoPageUrl = videoPageUrl,
                    coverUrl = info.coverUrl,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    showControls = showControls,
                    isFullscreen = true,
                    hasSubtitle = hasSubtitle,
                    qualityMap = qualityMap,
                    selectedQuality = selectedQuality,
                    availableSpeeds = availableSpeeds,
                    currentSpeed = currentSpeed,
                    showSpeedMenu = showSpeedMenu,
                    showQualityMenu = showQualityMenu,
                    exoPlayer = exoPlayer,
                    subtitleTextView = subtitleTextView,
                    showResumeButton = false,
                    savedPosition = savedPosition,
                    playerListener = playerListener,
                    onPlayerCreated = { },
                    onSubtitleTextViewCreated = { tv -> subtitleTextView = tv },
                    onSurfaceReleased = { },
                    onPlayPause = {
                        exoPlayer?.let { player ->
                            if (player.playWhenReady) {
                                player.playWhenReady = false
                                isPlaying = false
                            } else {
                                player.playWhenReady = true
                                isPlaying = true
                                showControls = true
                            }
                        }
                    },
                    onSeek = { position ->
                        exoPlayer?.seekTo(position)
                        currentPosition = position
                    },
                    onSkip = { deltaMillis -> performSkip(deltaMillis) },
                    onToggleControls = { showControls = !showControls },
                    onSpeedChange = { speed ->
                        currentSpeed = speed
                        exoPlayer?.setPlaybackSpeed(speed)
                        showSpeedMenu = false
                    },
                    onQualityChange = { quality ->
                        changeQuality(quality)
                        showQualityMenu = false
                    },
                    onToggleFullscreen = { exitFullscreen() },
                    onExitFullscreen = { exitFullscreen() },
                    onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                    onToggleQualityMenu = { showQualityMenu = !showQualityMenu },
                    onDismissSpeedMenu = { showSpeedMenu = false },
                    onDismissQualityMenu = { showQualityMenu = false },
                    onSubtitleToggle = {
                        if (hasSubtitle) clearSubtitle() else openSubtitlePicker()
                    },
                    onPlayClick = { },
                    onRetryExtraction = { },
                    onPositionUpdate = { position, total ->
                        currentPosition = position
                        duration = total
                        if (hasSubtitle) {
                            updateSubtitleForPosition(
                                position, subtitleCues, subtitleTextView, currentSubtitleText
                            ) { currentSubtitleText = it }
                        }
                    },
                    webViewRef = webViewRef,
                    onWebViewRefChange = { webViewRef = it },
                    onUrlCaptured = { url -> if (capturedUrl.isBlank()) capturedUrl = url },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun updateSubtitleForPosition(
    position: Long,
    cues: List<SubtitleCue>,
    tv: TextView?,
    currentText: String,
    onNewText: (String) -> Unit,
) {
    if (cues.isEmpty()) {
        tv?.visibility = android.view.View.GONE
        return
    }
    val cue = cues.findLast { position >= it.startTime }
    val newText = cue?.takeIf { position <= it.endTime }?.text ?: ""
    if (newText != currentText) {
        onNewText(newText)
        tv?.text = newText
        tv?.visibility = if (newText.isNotBlank())
            android.view.View.VISIBLE else android.view.View.GONE
        tv?.invalidate()
    }
}
