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
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    var playerContainer by remember { mutableStateOf<FrameLayout?>(null) }
    var containerParent by remember { mutableStateOf<ViewGroup?>(null) }
    var containerLayoutParams by remember { mutableStateOf<ViewGroup.LayoutParams?>(null) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var hasSubtitle by remember { mutableStateOf(false) }
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var currentSubtitleText by remember { mutableStateOf("") }
    var availableSpeeds by remember { mutableStateOf(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) }
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
            val qualities = listOf("360p", "480p", "720p", "1080p")
            linkedMapOf<String, String>().apply {
                qualities.forEach { quality ->
                    val url = capturedUrl.replace(Regex("/\\d+p/"), "/$quality/")
                    put(quality.uppercase(), url)
                }
            }
        }
    }

    val currentUrl = remember(selectedQuality, qualityMap) {
        qualityMap[selectedQuality] ?: qualityMap.values.firstOrNull() ?: ""
    }

    fun releasePlayer() {
        exoPlayer?.let { player ->
            player.playWhenReady = false
            player.stop()
            player.release()
        }
        exoPlayer = null
        webViewRef?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.destroy()
        }
        webViewRef = null
        isPlaying = false
    }

    fun handleBack() {
        releasePlayer()
        playerStarted = false
        isExtractingUrl = false
        extractionFailed = false
        capturedUrl = ""
        onBack()
    }

    fun updateCurrentSubtitle(position: Long) {
        if (subtitleCues.isNotEmpty()) {
            val cue = subtitleCues.findLast { position >= it.startTime }
            val newText = cue?.takeIf { position <= it.endTime }?.text ?: ""
            if (currentSubtitleText != newText) {
                currentSubtitleText = newText
                subtitleTextView?.apply {
                    text = newText
                    visibility = if (newText.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
                    invalidate()
                }
            }
        } else {
            subtitleTextView?.visibility = android.view.View.GONE
        }
    }

    fun loadSubtitles(uri: Uri) {
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists()) {
                    Toast.makeText(context, "Subtitle file not found", Toast.LENGTH_SHORT).show()
                    return
                }
                if (file.length() == 0L) {
                    Toast.makeText(context, "Subtitle file is empty", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            val cues = SubtitleParser.parseSRT(uri, context)
            if (cues.isNotEmpty()) {
                subtitleCues = cues
                hasSubtitle = true
                subtitleUri = uri
                currentSubtitleText = ""
                val currentPos = exoPlayer?.currentPosition ?: 0
                val cue = cues.findLast { currentPos >= it.startTime }
                currentSubtitleText = cue?.takeIf { currentPos <= it.endTime }?.text ?: ""
                subtitleTextView?.apply {
                    text = currentSubtitleText
                    visibility = if (currentSubtitleText.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
                    bringToFront()
                    invalidate()
                }
                Toast.makeText(context, "Loaded ${cues.size} subtitles", Toast.LENGTH_SHORT).show()
            } else {
                subtitleCues = emptyList()
                hasSubtitle = false
                subtitleUri = null
                Toast.makeText(context, "No subtitles found in file", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            subtitleCues = emptyList()
            hasSubtitle = false
            subtitleUri = null
            Toast.makeText(context, "Failed to load subtitle: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("MissAvVideoScreen", "Subtitle load error", e)
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
            val uri = result.data?.data
            uri?.let {
                val extension = it.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
                if (extension == "srt") {
                    subtitleUri = it
                    loadSubtitles(it)
                } else {
                    Toast.makeText(context, "Only .srt files are supported", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openSubtitlePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/srt", "application/x-subrip", "text/plain"))
        }
        subtitleFilePickerLauncher.launch(intent)
    }

    fun resumeFromSavedPosition() {
        exoPlayer?.seekTo(savedPosition)
        showResumeButton = false
        isPlaying = true
        exoPlayer?.playWhenReady = true
        showControls = true
        wasPlayed = true
        isFirstPlay = false
    }

    fun startFromBeginning() {
        exoPlayer?.seekTo(0)
        showResumeButton = false
        isPlaying = true
        exoPlayer?.playWhenReady = true
        showControls = true
        wasPlayed = true
        isFirstPlay = false
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
        playerContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
            containerParent?.let { parent ->
                parent.addView(container, containerLayoutParams)
            }
        }
        isFullscreen = false
        showControls = true
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
        playerContainer?.let { container ->
            containerParent = container.parent as? ViewGroup
            containerLayoutParams = container.layoutParams
            (container.parent as? ViewGroup)?.removeView(container)
            (activity?.window?.decorView as? ViewGroup)?.addView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        isFullscreen = true
        showControls = true
    }

    fun changeQuality(quality: String) {
        if (selectedQuality != quality) {
            val currentPos = exoPlayer?.currentPosition ?: 0
            val wasPlaying = exoPlayer?.playWhenReady ?: true
            val savedSubtitleUri = subtitleUri
            val savedSubtitleCues = subtitleCues
            
            selectedQuality = quality
            val newUrl = qualityMap[quality] ?: return
            
            exoPlayer?.let { player ->
                val dataSourceFactory = DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                        hashMapOf(
                            "Referer" to "${Preferences.missAvBaseUrl}/",
                            "Origin" to Preferences.missAvBaseUrl.trimEnd('/')
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
                
                savedSubtitleUri?.let { uri ->
                    subtitleCues = savedSubtitleCues
                    loadSubtitles(uri)
                    updateCurrentSubtitle(currentPos)
                }
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
        
        val history = MissAvHistoryRepo.getByVideoCode(videoCode)
        if (history != null && history.lastPosition > 5000) {
            savedPosition = history.lastPosition
            showResumeButton = true
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
                currentPosition = 0,
                totalDuration = 0,
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
            tv.visibility = if (currentSubtitleText.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
            tv.invalidate()
        }
    }

    LaunchedEffect(capturedUrl) {
        if (capturedUrl.isNotBlank() && isExtractingUrl) {
            webViewRef?.stopLoading()
            webViewRef?.let { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
            webViewRef = null
            isExtractingUrl = false
            playerStarted = true
        }
    }

    LaunchedEffect(isExtractingUrl) {
        if (isExtractingUrl) {
            delay(15000)
            if (isExtractingUrl) {
                if (capturedUrl.isNotBlank()) {
                    webViewRef?.stopLoading()
                    webViewRef?.let { webView ->
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView.destroy()
                    }
                    webViewRef = null
                    isExtractingUrl = false
                    playerStarted = true
                } else {
                    webViewRef?.stopLoading()
                    webViewRef?.let { webView ->
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView.destroy()
                    }
                    webViewRef = null
                    isExtractingUrl = false
                    extractionFailed = true
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

    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying && hasSubtitle) {
            delay(100)
            currentPosition = exoPlayer?.currentPosition ?: 0
        }
    }

    LaunchedEffect(isPlaying, exoPlayer) {
        if (!isPlaying || exoPlayer == null) return@LaunchedEffect
        val info = (videoState as? VideoLoadingState.Success)?.info
        if (info == null || info.title.isBlank()) return@LaunchedEffect
        wasPlayed = true
        isFirstPlay = false
        showResumeButton = false
        while (isActive && isPlaying && exoPlayer != null) {
            val position = exoPlayer?.currentPosition ?: 0
            val totalDuration = exoPlayer?.duration ?: 0
            if (totalDuration > 0) {
                historyViewModel.updateWatchHistory(
                    videoCode = videoCode,
                    title = info.title,
                    coverUrl = info.coverUrl,
                    currentPosition = position,
                    totalDuration = totalDuration,
                    isPlaying = true,
                    wasPlayed = wasPlayed
                )
            }
            delay(2000)
        }
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val info = (videoState as? VideoLoadingState.Success)?.info
                if (info == null || info.title.isBlank()) return
                val position = exoPlayer?.currentPosition ?: 0
                val totalDuration = exoPlayer?.duration ?: 0
                if (totalDuration > 0) {
                    historyViewModel.updateWatchHistory(
                        videoCode = videoCode,
                        title = info.title,
                        coverUrl = info.coverUrl,
                        currentPosition = position,
                        totalDuration = totalDuration,
                        isPlaying = exoPlayer?.playWhenReady ?: false,
                        wasPlayed = wasPlayed || exoPlayer?.playWhenReady == true
                    )
                }
            }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isExtractingUrl) {
                webViewRef?.stopLoading()
                webViewRef?.let { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
                webViewRef = null
                isExtractingUrl = false
            }
            exitFullscreen()
            releasePlayer()
        }
    }

    BackHandler(enabled = isFullscreen) { exitFullscreen() }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text("Video", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { handleBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            if (hasSubtitle) {
                                clearSubtitle()
                            } else {
                                openSubtitlePicker()
                            }
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
        }
    ) { paddingValues ->
        when (val state = videoState) {
            is VideoLoadingState.Loading -> LoadingContent(modifier = Modifier.padding(paddingValues))
            is VideoLoadingState.Success -> {
                val baseUrl = Preferences.missAvBaseUrl
                val videoPageUrl = baseUrl.trimEnd('/') + normalizedPath
                val info = state.info

                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize()
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
                                isFullscreen = isFullscreen,
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
                                onResumeFromSaved = { resumeFromSavedPosition() },
                                onStartFromBeginning = { startFromBeginning() },
                                onPlayerCreated = { player ->
                                    exoPlayer = player
                                    val dataSourceFactory = DefaultDataSource.Factory(
                                        context,
                                        DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                                            hashMapOf(
                                                "Referer" to "${Preferences.missAvBaseUrl}/",
                                                "Origin" to Preferences.missAvBaseUrl.trimEnd('/')
                                            )
                                        )
                                    )
                                    val mediaItem = androidx.media3.common.MediaItem.Builder().setUri(currentUrl).build()
                                    val mediaSource = if (currentUrl.contains(".m3u8")) {
                                        HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                                    } else {
                                        androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                                            .createMediaSource(mediaItem)
                                    }
                                    player.setMediaSource(mediaSource)
                                    player.prepare()
                                    player.setPlaybackSpeed(currentSpeed)
                                    if (savedPosition > 0) {
                                        player.seekTo(savedPosition)
                                    }
                                    
                                    subtitleUri?.let { uri ->
                                        loadSubtitles(uri)
                                    }
                                    
                                    player.addListener(object : Player.Listener {
                                        override fun onPlaybackStateChanged(playbackState: Int) {
                                            if (playbackState == Player.STATE_READY) {
                                                duration = player.duration
                                                currentPosition = player.currentPosition
                                                updateCurrentSubtitle(player.currentPosition)
                                            }
                                        }
                                        override fun onPositionDiscontinuity(
                                            oldPosition: Player.PositionInfo,
                                            newPosition: Player.PositionInfo,
                                            reason: Int
                                        ) {
                                            currentPosition = player.currentPosition
                                            duration = player.duration
                                            updateCurrentSubtitle(player.currentPosition)
                                        }
                                        override fun onIsPlayingChanged(playing: Boolean) {
                                            if (playing) {
                                                currentPosition = player.currentPosition
                                                duration = player.duration
                                            }
                                        }
                                    })
                                    if (isPlaying) {
                                        player.playWhenReady = true
                                    }
                                },
                                onContainerCreated = { container ->
                                    playerContainer = container
                                },
                                onSubtitleTextViewCreated = { textView ->
                                    subtitleTextView = textView
                                },
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
                                    if (hasSubtitle) {
                                        clearSubtitle()
                                    } else {
                                        openSubtitlePicker()
                                    }
                                },
                                onPlayClick = {
                                    capturedUrl = ""
                                    extractionFailed = false
                                    isExtractingUrl = true
                                    if (savedPosition > 0) {
                                        showResumeButton = true
                                    }
                                },
                                onRetryExtraction = {
                                    extractionFailed = false
                                    capturedUrl = ""
                                    isExtractingUrl = true
                                },
                                onPositionUpdate = { position, totalDuration ->
                                    currentPosition = position
                                    duration = totalDuration
                                    if (hasSubtitle) {
                                        updateCurrentSubtitle(position)
                                    }
                                },
                                webViewRef = webViewRef,
                                onWebViewRefChange = { webViewRef = it },
                                onUrlCaptured = { url ->
                                    if (capturedUrl.isBlank()) {
                                        capturedUrl = url
                                    }
                                }
                            )
                        }
                    }

                    item {
                        MissAvVideoDetails(
                            info = info,
                            onNavigateToSearch = onNavigateToSearch,
                            onSubtitleDownloaded = { uri ->
                                subtitleUri = uri
                                loadSubtitles(uri)
                                currentPosition = exoPlayer?.currentPosition ?: 0
                            },
                            context = context
                        )
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
}