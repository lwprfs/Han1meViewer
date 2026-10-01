package com.yenaly.han1meviewer.HentaiMama

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryRepo
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaVideoSettings
import com.yenaly.han1meviewer.MissAV.ui.video.MissAvVideoPlayer
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.rememberCardResponsiveWidth
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "HentaiMamaVideo"
private const val SAVE_INTERVAL_MS = 2_000L
private const val RESUME_THRESHOLD_MS = 5_000L
private const val COMPLETE_GRACE_MS = 3_000L
private const val PLAYER_RELEASE_SETTLE_MS = 120L

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private data class ResumeOffer(
    val episodeUrl: String,
    val episodeNumber: Float,
    val episodeTitle: String,
    val position: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaVideoScreen(
    videoCode: String,
    path: String,
    resumePosition: Long = 0L,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    HentaiMamaHistoryRepo.init(context)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val seriesUrl = remember(path, videoCode) {
        if (path.contains("/episodes/", ignoreCase = true)) {
            val match = Regex("""/episodes/(.+?)-episode-""").find(path)
            if (match != null) {
                "${HentaiMamaNetwork.baseUrl}/${match.groupValues[1]}"
            } else {
                HentaiMamaNetwork.normalizeUrl("/$videoCode")
            }
        } else {
            path
        }
    }

    var currentUrl by remember { mutableStateOf("") }
    var playerStarted by remember { mutableStateOf(false) }
    var isExtractingUrl by remember { mutableStateOf(false) }
    var extractionFailed by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var hasSubtitle by remember { mutableStateOf(false) }
    var subtitleTextView by remember { mutableStateOf<TextView?>(null) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("") }
    var qualityMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var videoLinks by remember { mutableStateOf<List<HentaiMamaVideoLink>>(emptyList()) }
    var detailBody by remember { mutableStateOf("") }
    var hosterTabs by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var selectedHosterIndex by remember { mutableIntStateOf(0) }

    var selectedEpisodeUrl by remember { mutableStateOf("") }
    var currentEpisodeNumber by remember { mutableStateOf(1f) }
    var currentEpisodeTitle by remember { mutableStateOf("") }

    var pendingResumeOffer by remember { mutableStateOf<ResumeOffer?>(null) }
    var pendingResumePosition by remember { mutableLongStateOf(resumePosition) }
    var historyLoaded by remember { mutableStateOf(false) }

    var playIntent by remember { mutableStateOf(false) }
    var episodeCompleted by remember { mutableStateOf(false) }
    var playerGeneration by remember { mutableIntStateOf(0) }

    val availableSpeeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    val playerListener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                mainHandler.post {
                    val p = exoPlayer ?: return@post
                    when (state) {
                        Player.STATE_READY -> {
                            duration = p.duration.coerceAtLeast(0L)
                            currentPosition = p.currentPosition
                            if (pendingResumePosition > 0L && p.currentPosition < 500L) {
                                p.seekTo(pendingResumePosition)
                                pendingResumePosition = 0L
                            }
                        }
                        Player.STATE_ENDED -> {
                            episodeCompleted = true
                            isPlaying = false
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                old: Player.PositionInfo,
                new: Player.PositionInfo,
                reason: Int,
            ) {
                mainHandler.post {
                    val p = exoPlayer ?: return@post
                    currentPosition = p.currentPosition
                    duration = p.duration.coerceAtLeast(0L)
                }
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        val p = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            val pos = runCatching { p.currentPosition }.getOrDefault(currentPosition)
            val dur = runCatching { p.duration }.getOrDefault(duration)
            currentPosition = pos
            if (dur > 0) duration = dur
            delay(500)
        }
    }

    LaunchedEffect(videoCode) {
        val history = withContext(Dispatchers.IO) {
            HentaiMamaHistoryRepo.getByVideoCode(videoCode)
        }
        if (history != null) {
            currentEpisodeNumber = history.lastEpisodeNumber
            currentEpisodeTitle = history.lastEpisodeTitle

            if (resumePosition > 0L) {
                pendingResumePosition = resumePosition
                selectedEpisodeUrl = history.lastEpisodeUrl
            } else if (history.lastPosition > RESUME_THRESHOLD_MS &&
                !history.completed &&
                history.lastEpisodeUrl.isNotBlank()
            ) {
                pendingResumeOffer = ResumeOffer(
                    episodeUrl = history.lastEpisodeUrl,
                    episodeNumber = history.lastEpisodeNumber,
                    episodeTitle = history.lastEpisodeTitle,
                    position = history.lastPosition,
                )
            }
        }
        historyLoaded = true
    }

    LaunchedEffect(historyLoaded, seriesUrl) {
        if (!historyLoaded) return@LaunchedEffect
        viewModel.getVideoDetail(seriesUrl)
    }

    fun releasePlayer() {
        val p = exoPlayer
        exoPlayer = null
        playerStarted = false
        isPlaying = false
        currentPosition = 0L
        duration = 0L
        subtitleTextView = null
        if (p != null) {
            mainHandler.post {
                runCatching {
                    p.setVideoSurfaceView(null)
                    p.stop()
                    p.release()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            releasePlayer()
        }
    }

    fun buildMediaSource(url: String): MediaSource {
        val referer = HentaiMamaNetwork.baseUrl
        val factory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                hashMapOf("Referer" to referer)
            )
        )
        return if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        }
    }

    suspend fun loadLink(link: HentaiMamaVideoLink, player: ExoPlayer?, seekTo: Long?) {
        currentUrl = link.url
        selectedQuality = link.quality
        playerStarted = true
        episodeCompleted = false
        if (player == null) return

        withContext(Dispatchers.Main) {
            runCatching {
                val pos = seekTo ?: 0L
                player.stop()
                player.clearMediaItems()
                player.setMediaSource(buildMediaSource(link.url))
                player.seekTo(pos)
                player.prepare()
                player.playWhenReady = true
            }.onFailure { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pickPreferred(links: List<HentaiMamaVideoLink>): HentaiMamaVideoLink {
        val pref = HentaiMamaVideoSettings.preferredQuality
        if (pref == HentaiMamaVideoSettings.QUALITY_AUTO) return links.first()
        return links.firstOrNull { it.quality.equals(pref, ignoreCase = true) } ?: links.first()
    }

    fun resolveHosterTabs(rawTabs: List<Pair<String, Int>>): List<Pair<String, Int>> {
        val preferred = HentaiMamaVideoSettings.preferredServer
        return if (preferred == HentaiMamaVideoSettings.SERVER_AUTO) rawTabs
        else rawTabs.sortedByDescending { it.first.contains(preferred, ignoreCase = true) }
    }

    fun startPlaybackForEpisode(targetPath: String, seekTo: Long) {
        coroutineScope.launch(Dispatchers.Main) {
            isExtractingUrl = true
            extractionFailed = false
            try {
                val isNewEpisode = targetPath != selectedEpisodeUrl
                val body = if (!isNewEpisode && detailBody.isNotBlank()) {
                    detailBody
                } else {
                    viewModel.fetchDetailBody(targetPath).also { detailBody = it }
                }
                if (body.isBlank()) {
                    extractionFailed = true
                    return@launch
                }

                if (isNewEpisode || hosterTabs.isEmpty()) {
                    val rawTabs = viewModel.fetchHosterTabs(targetPath)
                    hosterTabs = resolveHosterTabs(rawTabs)
                    selectedHosterIndex = 0
                }

                selectedEpisodeUrl = targetPath

                val optionNumber = hosterTabs.getOrNull(selectedHosterIndex)?.second
                    ?: hosterTabs.firstOrNull()?.second
                    ?: run {
                        extractionFailed = true
                        return@launch
                    }

                val links = withContext(Dispatchers.IO) {
                    HentaiMamaNetworkRepo.extractVideoLinks(body, optionNumber)
                }

                if (links.isEmpty()) {
                    extractionFailed = true
                    Toast.makeText(context, "No sources for this mirror", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                videoLinks = links
                qualityMap = links.associate { it.quality to it.url }

                releasePlayer()

                delay(PLAYER_RELEASE_SETTLE_MS)

                val picked = pickPreferred(links)
                currentUrl = picked.url
                selectedQuality = picked.quality
                pendingResumePosition = seekTo
                playIntent = true
                episodeCompleted = false
                playerGeneration += 1
                playerStarted = true

                mainHandler.post {
                    isPlaying = true
                }
            } catch (e: Exception) {
                extractionFailed = true
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isExtractingUrl = false
            }
        }
    }

    val episodes: List<HentaiMamaEpisode> = (videoState as? VideoLoadingState.Success)
        ?.info?.episodes.orEmpty()

    fun episodeIndexForUrl(targetUrl: String): Int {
        if (targetUrl.isBlank()) return -1
        val targetCode = targetUrl.trimEnd('/').substringAfterLast("/")
        return episodes.indexOfFirst {
            it.url.trimEnd('/').substringAfterLast("/") == targetCode
        }
    }

    val currentEpisodeIndex = episodeIndexForUrl(selectedEpisodeUrl)
    val hasPrevious = currentEpisodeIndex > 0
    val hasNext = currentEpisodeIndex in 0 until episodes.lastIndex

    fun playEpisodeAt(index: Int, seekTo: Long = 0L) {
        val ep = episodes.getOrNull(index) ?: return
        val newPath = if (ep.url.startsWith("http")) ep.url
        else HentaiMamaNetwork.normalizeUrl(ep.url)

        currentEpisodeNumber = ep.episodeNumber ?: 1f
        currentEpisodeTitle = ep.title
        pendingResumePosition = seekTo
        playIntent = true
        episodeCompleted = false

        startPlaybackForEpisode(newPath, seekTo)
    }

    fun replayCurrent() {
        episodeCompleted = false
        playIntent = true
        pendingResumePosition = 0L
        mainHandler.post {
            exoPlayer?.let {
                runCatching {
                    it.seekTo(0L)
                    it.playWhenReady = true
                }
            }
        }
    }

    val latestPlayer by rememberUpdatedState(exoPlayer)
    val latestEpisodeUrl by rememberUpdatedState(selectedEpisodeUrl)
    val latestEpisodeNumber by rememberUpdatedState(currentEpisodeNumber)
    val latestEpisodeTitle by rememberUpdatedState(currentEpisodeTitle)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    LaunchedEffect(videoCode, historyLoaded) {
        if (!historyLoaded) return@LaunchedEffect

        while (isActive) {
            delay(SAVE_INTERVAL_MS)
            val p = latestPlayer
            val info = (videoState as? VideoLoadingState.Success)?.info
            if (p == null || info == null || !latestIsPlaying || latestEpisodeUrl.isBlank()) continue

            val pos = withContext(Dispatchers.Main) {
                runCatching { p.currentPosition }.getOrDefault(0L)
            }
            val dur = withContext(Dispatchers.Main) {
                runCatching { p.duration }.getOrDefault(0L)
            }

            if (dur > 0 && pos > 0) {
                val completed = pos >= dur - COMPLETE_GRACE_MS
                withContext(Dispatchers.IO) {
                    runCatching {
                        HentaiMamaHistoryRepo.saveProgress(
                            videoCode = info.videoCode,
                            title = info.title,
                            coverUrl = info.coverUrl,
                            episodeUrl = latestEpisodeUrl,
                            episodeNumber = latestEpisodeNumber,
                            episodeTitle = latestEpisodeTitle,
                            position = pos,
                            duration = dur,
                            isPlaying = true,
                            completed = completed,
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val p = latestPlayer ?: return@LifecycleEventObserver
                val info = (videoState as? VideoLoadingState.Success)?.info
                    ?: return@LifecycleEventObserver
                if (latestEpisodeUrl.isBlank()) return@LifecycleEventObserver
                mainHandler.post {
                    val pos = runCatching { p.currentPosition }.getOrDefault(0L)
                    val dur = runCatching { p.duration }.getOrDefault(0L)
                    val playing = runCatching { p.playWhenReady }.getOrDefault(false)
                    if (dur > 0 && pos > 0) {
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                HentaiMamaHistoryRepo.saveProgress(
                                    videoCode = info.videoCode,
                                    title = info.title,
                                    coverUrl = info.coverUrl,
                                    episodeUrl = latestEpisodeUrl,
                                    episodeNumber = latestEpisodeNumber,
                                    episodeTitle = latestEpisodeTitle,
                                    position = pos,
                                    duration = dur,
                                    isPlaying = playing,
                                    completed = pos >= dur - COMPLETE_GRACE_MS,
                                )
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    val toggleFullscreen = {
        if (isFullscreen) exitFullscreen() else enterFullscreen()
    }

    BackHandler(enabled = true) {
        if (isFullscreen) exitFullscreen() else onBack()
    }

    pendingResumeOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = {
                pendingResumeOffer = null
            },
            title = { Text("Resume playback?") },
            text = {
                Text(
                    "You were watching episode ${offer.episodeNumber.toInt()} " +
                            "at ${formatMs(offer.position)}.\n\n" +
                            "Resume from where you left off, or start the episode from the beginning?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    currentEpisodeNumber = offer.episodeNumber
                    currentEpisodeTitle = offer.episodeTitle
                    pendingResumePosition = offer.position
                    playIntent = true
                    pendingResumeOffer = null
                    startPlaybackForEpisode(offer.episodeUrl, offer.position)
                }) {
                    Text("Resume")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        currentEpisodeNumber = offer.episodeNumber
                        currentEpisodeTitle = offer.episodeTitle
                        pendingResumePosition = 0L
                        playIntent = true
                        pendingResumeOffer = null
                        startPlaybackForEpisode(offer.episodeUrl, 0L)
                    }) {
                        Text("Start over")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        pendingResumeOffer = null
                    }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val info = (videoState as? VideoLoadingState.Success)?.info
                    Text(text = info?.title ?: "Video", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { paddingValues ->
        when (val state = videoState) {
            is VideoLoadingState.Loading ->
                LoadingContent(modifier = Modifier.padding(paddingValues))

            is VideoLoadingState.Success -> {
                val info = state.info
                val (cardWidth, _) = rememberCardResponsiveWidth()

                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                playerStarted && currentUrl.isNotEmpty() -> {
                                    key(playerGeneration) {
                                        MissAvVideoPlayer(
                                            playerStarted = true,
                                            currentUrl = currentUrl,
                                            isExtractingUrl = false,
                                            extractionFailed = false,
                                            capturedUrl = currentUrl,
                                            videoPageUrl = "",
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
                                            showResumeButton = false,
                                            savedPosition = 0L,
                                            playerListener = playerListener,
                                            onPlayerCreated = { player ->
                                                exoPlayer = player
                                                mainHandler.post {
                                                    runCatching {
                                                        if (currentUrl.isNotEmpty()) {
                                                            player.setMediaSource(buildMediaSource(currentUrl))
                                                            player.prepare()
                                                            player.setPlaybackSpeed(currentSpeed)
                                                            val seek = pendingResumePosition
                                                            if (seek > 0L) {
                                                                player.seekTo(seek)
                                                                pendingResumePosition = 0L
                                                            }
                                                            player.playWhenReady = playIntent
                                                            isPlaying = playIntent
                                                        }
                                                    }.onFailure { e ->
                                                        Toast.makeText(
                                                            context,
                                                            "Error: ${e.message}",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                }
                                            },
                                            onSubtitleTextViewCreated = { subtitleTextView = it },
                                            onSurfaceReleased = { },
                                            onPlayPause = {
                                                mainHandler.post {
                                                    val p = exoPlayer ?: return@post
                                                    runCatching {
                                                        if (p.playWhenReady) {
                                                            p.playWhenReady = false
                                                            isPlaying = false
                                                        } else {
                                                            p.playWhenReady = true
                                                            isPlaying = true
                                                            showControls = true
                                                        }
                                                    }
                                                }
                                            },
                                            onSeek = { pos ->
                                                mainHandler.post {
                                                    exoPlayer?.let {
                                                        runCatching { it.seekTo(pos) }
                                                        currentPosition = pos
                                                    }
                                                }
                                            },
                                            onSkip = { delta ->
                                                mainHandler.post {
                                                    exoPlayer?.let {
                                                        runCatching {
                                                            val pos = it.currentPosition
                                                            val dur = it.duration
                                                            val target = if (dur > 0 &&
                                                                dur != androidx.media3.common.C.TIME_UNSET
                                                            ) (pos + delta).coerceIn(0L, dur)
                                                            else (pos + delta).coerceAtLeast(0L)
                                                            it.seekTo(target)
                                                            currentPosition = target
                                                        }
                                                    }
                                                }
                                            },
                                            onToggleControls = { showControls = !showControls },
                                            onSpeedChange = { s ->
                                                currentSpeed = s
                                                mainHandler.post {
                                                    exoPlayer?.let { runCatching { it.setPlaybackSpeed(s) } }
                                                }
                                                showSpeedMenu = false
                                                showControls = true
                                            },
                                            onQualityChange = { q ->
                                                val url = qualityMap[q]
                                                if (url != null) {
                                                    coroutineScope.launch {
                                                        val pos = exoPlayer?.let {
                                                            withContext(Dispatchers.Main) {
                                                                runCatching { it.currentPosition }.getOrDefault(0L)
                                                            }
                                                        } ?: 0L
                                                        loadLink(HentaiMamaVideoLink(q, url), exoPlayer, pos)
                                                    }
                                                }
                                                showQualityMenu = false
                                                showControls = true
                                            },
                                            onToggleFullscreen = { toggleFullscreen() },
                                            onExitFullscreen = { exitFullscreen() },
                                            onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                                            onToggleQualityMenu = { showQualityMenu = !showQualityMenu },
                                            onDismissSpeedMenu = { showSpeedMenu = false },
                                            onDismissQualityMenu = { showQualityMenu = false },
                                            onSubtitleToggle = { hasSubtitle = !hasSubtitle },
                                            onPlayClick = { },
                                            onRetryExtraction = {
                                                if (selectedEpisodeUrl.isNotBlank()) {
                                                    startPlaybackForEpisode(
                                                        selectedEpisodeUrl,
                                                        pendingResumePosition,
                                                    )
                                                }
                                            },
                                            onPositionUpdate = { pos, dur ->
                                                currentPosition = pos
                                                if (dur > 0) duration = dur
                                            },
                                            webViewRef = null,
                                            onWebViewRefChange = {},
                                            onUrlCaptured = {},
                                        )
                                    }
                                }
                                isExtractingUrl -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(8.dp))
                                        Text("Extracting video links…")
                                    }
                                }
                                extractionFailed -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text("Failed to load video")
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = {
                                            playIntent = true
                                            if (selectedEpisodeUrl.isNotBlank()) {
                                                startPlaybackForEpisode(
                                                    selectedEpisodeUrl,
                                                    pendingResumePosition,
                                                )
                                            }
                                        }) { Text("Retry") }
                                    }
                                }
                                else -> {
                                    if (info.coverUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = info.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = RoundedCornerShape(36.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                        onClick = {
                                            if (pendingResumeOffer == null) {
                                                val target = episodes.firstOrNull()
                                                if (target != null) {
                                                    val targetPath =
                                                        if (target.url.startsWith("http")) target.url
                                                        else HentaiMamaNetwork.normalizeUrl(target.url)
                                                    currentEpisodeNumber =
                                                        target.episodeNumber ?: 1f
                                                    currentEpisodeTitle = target.title
                                                    playIntent = true
                                                    pendingResumePosition = 0L
                                                    startPlaybackForEpisode(targetPath, 0L)
                                                } else {
                                                    playIntent = true
                                                    startPlaybackForEpisode(
                                                        seriesUrl,
                                                        0L,
                                                    )
                                                }
                                            }
                                        },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play",
                                                modifier = Modifier.size(40.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (episodeCompleted && episodes.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (hasPrevious) playEpisodeAt(currentEpisodeIndex - 1)
                                    },
                                    enabled = hasPrevious,
                                ) {
                                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous episode")
                                }

                                FilledTonalIconButton(onClick = { replayCurrent() }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Replay")
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        if (hasNext) playEpisodeAt(currentEpisodeIndex + 1)
                                    },
                                    enabled = hasNext,
                                ) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = "Next episode")
                                }

                                Spacer(Modifier.weight(1f))

                                Text(
                                    text = "Episode finished",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    if (!info.genre.isNullOrBlank() ||
                        !info.author.isNullOrBlank() ||
                        !info.status.isNullOrBlank()
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                info.author?.takeIf { it.isNotBlank() }?.let { author ->
                                    Text(
                                        text = "Studio: $author",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                info.status?.takeIf { it.isNotBlank() }?.let { status ->
                                    Text(
                                        text = "Status: $status",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                info.genre?.takeIf { it.isNotBlank() }?.let { genres ->
                                    Text(
                                        text = "Genres: $genres",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (!info.description.isNullOrBlank()) {
                        item {
                            Text(
                                text = info.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (episodes.isNotEmpty()) {
                        item {
                            Text(
                                "Episodes (${episodes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(episodes, key = { it.url }) { ep ->
                            val epCode = ep.url.trimEnd('/').substringAfterLast("/")
                            val currentCode = selectedEpisodeUrl.trimEnd('/').substringAfterLast("/")
                            val isCurrent = epCode == currentCode

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable {
                                        if (isCurrent && playIntent) return@clickable
                                        val newPath = if (ep.url.startsWith("http")) ep.url
                                        else HentaiMamaNetwork.normalizeUrl(ep.url)
                                        currentEpisodeNumber = ep.episodeNumber ?: 1f
                                        currentEpisodeTitle = ep.title
                                        pendingResumePosition = 0L
                                        playIntent = true
                                        episodeCompleted = false
                                        startPlaybackForEpisode(newPath, 0L)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrent)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ep.title.ifEmpty { "Episode" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold
                                            else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        ep.episodeNumber?.let {
                                            Text(
                                                "Episode ${it.toInt()}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ep.date?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (isCurrent) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                            ) {
                                                Text(
                                                    "▶ Current",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp,
                                                    ),
                                                )
                                            }
                                        } else {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (info.relatedVideos.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            Text(
                                "Related",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                                contentPadding = PaddingValues(horizontal = SpacingLarge),
                            ) {
                                items(info.relatedVideos, key = { it.videoCode }) { v ->
                                    VideoCardItem(
                                        modifier = Modifier.width(cardWidth),
                                        videoItem = v,
                                        isHorizontalCard = true,
                                        onClickVideosItem = {
                                            val p = HentaiMamaNetwork.normalizeUrl("/${v.videoCode}")
                                            onNavigateToVideo(v.videoCode, p)
                                        },
                                        onLongClickVideosItem = { _, _ -> },
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }

            is VideoLoadingState.Error -> ErrorContent(
                message = state.throwable.message ?: "Failed to load video",
                onRetry = { viewModel.getVideoDetail(seriesUrl) },
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.NoContent -> ErrorContent(
                message = "No content found",
                onRetry = { viewModel.getVideoDetail(seriesUrl) },
                modifier = Modifier.padding(paddingValues),
            )
        }
    }

    if (isFullscreen && videoState is VideoLoadingState.Success) {
        val info = (videoState as VideoLoadingState.Success).info
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
                key(playerGeneration) {
                    MissAvVideoPlayer(
                        playerStarted = playerStarted,
                        currentUrl = currentUrl,
                        isExtractingUrl = isExtractingUrl,
                        extractionFailed = extractionFailed,
                        capturedUrl = currentUrl,
                        videoPageUrl = "",
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
                        savedPosition = 0L,
                        playerListener = playerListener,
                        onPlayerCreated = { },
                        onSubtitleTextViewCreated = { tv -> subtitleTextView = tv },
                        onSurfaceReleased = { },
                        onPlayPause = {
                            mainHandler.post {
                                val p = exoPlayer ?: return@post
                                runCatching {
                                    if (p.playWhenReady) {
                                        p.playWhenReady = false
                                        isPlaying = false
                                    } else {
                                        p.playWhenReady = true
                                        isPlaying = true
                                        showControls = true
                                    }
                                }
                            }
                        },
                        onSeek = { pos ->
                            mainHandler.post {
                                exoPlayer?.let {
                                    runCatching { it.seekTo(pos) }
                                    currentPosition = pos
                                }
                            }
                        },
                        onSkip = { delta ->
                            mainHandler.post {
                                exoPlayer?.let {
                                    runCatching {
                                        val pos = it.currentPosition
                                        val dur = it.duration
                                        val target = if (dur > 0 &&
                                            dur != androidx.media3.common.C.TIME_UNSET
                                        ) (pos + delta).coerceIn(0L, dur)
                                        else (pos + delta).coerceAtLeast(0L)
                                        it.seekTo(target)
                                        currentPosition = target
                                    }
                                }
                            }
                        },
                        onToggleControls = { showControls = !showControls },
                        onSpeedChange = { s ->
                            currentSpeed = s
                            mainHandler.post {
                                exoPlayer?.let { runCatching { it.setPlaybackSpeed(s) } }
                            }
                            showSpeedMenu = false
                        },
                        onQualityChange = { q ->
                            val url = qualityMap[q]
                            if (url != null) {
                                coroutineScope.launch {
                                    val pos = exoPlayer?.let {
                                        withContext(Dispatchers.Main) {
                                            runCatching { it.currentPosition }.getOrDefault(0L)
                                        }
                                    } ?: 0L
                                    loadLink(HentaiMamaVideoLink(q, url), exoPlayer, pos)
                                }
                            }
                            showQualityMenu = false
                        },
                        onToggleFullscreen = { exitFullscreen() },
                        onExitFullscreen = { exitFullscreen() },
                        onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                        onToggleQualityMenu = { showQualityMenu = !showQualityMenu },
                        onDismissSpeedMenu = { showSpeedMenu = false },
                        onDismissQualityMenu = { showQualityMenu = false },
                        onSubtitleToggle = { hasSubtitle = !hasSubtitle },
                        onPlayClick = { },
                        onRetryExtraction = {
                            if (selectedEpisodeUrl.isNotBlank()) {
                                startPlaybackForEpisode(selectedEpisodeUrl, pendingResumePosition)
                            }
                        },
                        onPositionUpdate = { pos, dur ->
                            currentPosition = pos
                            if (dur > 0) duration = dur
                        },
                        webViewRef = null,
                        onWebViewRefChange = {},
                        onUrlCaptured = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
