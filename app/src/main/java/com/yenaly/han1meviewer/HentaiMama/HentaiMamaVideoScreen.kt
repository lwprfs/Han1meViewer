package com.yenaly.han1meviewer.HentaiMama

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaVideoScreen(
    videoCode: String,
    path: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf("") }
    var playerStarted by remember { mutableStateOf(false) }
    var isExtractingUrl by remember { mutableStateOf(false) }
    var extractionFailed by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var hasSubtitle by remember { mutableStateOf(false) }
    var subtitleTextView by remember { mutableStateOf<TextView?>(null) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("") }
    var qualityMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isFullscreenMode by remember { mutableStateOf(false) }

    var videoLinks by remember { mutableStateOf<List<HentaiMamaVideoLink>>(emptyList()) }
    var detailBody by remember { mutableStateOf("") }
    var hosterTabs by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var selectedHosterIndex by remember { mutableIntStateOf(0) }

    var currentUrlPath by remember(path) { mutableStateOf(path) }

    val availableSpeeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    val playerListener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                exoPlayer?.let { player ->
                    if (playbackState == Player.STATE_READY) {
                        duration = player.duration.coerceAtLeast(0L)
                        currentPosition = player.currentPosition
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                exoPlayer?.let {
                    currentPosition = it.currentPosition
                    duration = it.duration.coerceAtLeast(0L)
                }
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (exoPlayer != null) {
            exoPlayer?.let {
                currentPosition = it.currentPosition
                val d = it.duration
                if (d > 0) duration = d
            }
            delay(500)
        }
    }

    LaunchedEffect(currentUrlPath) {
        Log.d("HentaiMamaVideo", "Loading path=$currentUrlPath")

        playerStarted = false
        currentUrl = ""
        isExtractingUrl = false
        extractionFailed = false
        videoLinks = emptyList()
        qualityMap = emptyMap()
        detailBody = ""
        hosterTabs = emptyList()
        selectedHosterIndex = 0

        exoPlayer?.release()
        exoPlayer = null

        viewModel.getVideoDetail(currentUrlPath)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
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

    fun loadLink(link: HentaiMamaVideoLink, player: ExoPlayer?) {
        currentUrl = link.url
        selectedQuality = link.quality
        playerStarted = true
        if (player != null) {
            val pos = player.currentPosition
            try {
                player.setMediaSource(buildMediaSource(link.url))
                player.seekTo(pos)
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun extractLinksForHoster(optionNumber: Int) {
        coroutineScope.launch {
            isExtractingUrl = true
            extractionFailed = false
            try {
                val body = detailBody.ifBlank {
                    viewModel.fetchDetailBody(currentUrlPath).also { detailBody = it }
                }
                if (body.isBlank()) {
                    extractionFailed = true
                    return@launch
                }
                val links = withContext(Dispatchers.IO) {
                    HentaiMamaNetworkRepo.extractVideoLinks(body, optionNumber)
                }
                if (links.isNotEmpty()) {
                    videoLinks = links
                    qualityMap = links.associate { it.quality to it.url }
                    loadLink(links.first(), exoPlayer)
                    isPlaying = true
                } else {
                    extractionFailed = true
                    Toast.makeText(context, "No sources for this mirror", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                extractionFailed = true
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isExtractingUrl = false
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
            player.seekTo(target)
            currentPosition = target
        }
    }

    val successInfo = (videoState as? VideoLoadingState.Success)?.info
    LaunchedEffect(successInfo?.url) {
        val info = successInfo ?: return@LaunchedEffect
        val body = viewModel.fetchDetailBody(info.url)
        detailBody = body
        hosterTabs = viewModel.fetchHosterTabs(info.url)
        if (hosterTabs.isNotEmpty()) {
            selectedHosterIndex = 0
            extractLinksForHoster(hosterTabs[0].second)
        }
    }

    val toggleFullscreen = {
        isFullscreenMode = !isFullscreenMode
        if (isFullscreenMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
        isFullscreen = isFullscreenMode
    }

    BackHandler(enabled = true) {
        if (isFullscreenMode) toggleFullscreen() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val info = successInfo
                    Text(text = info?.title ?: "Video", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
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
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                playerStarted && currentUrl.isNotEmpty() -> {
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
                                        showResumeButton = false,
                                        savedPosition = 0L,
                                        playerListener = playerListener,
                                        onPlayerCreated = { player ->
                                            exoPlayer = player
                                            if (currentUrl.isNotEmpty()) {
                                                try {
                                                    player.setMediaSource(buildMediaSource(currentUrl))
                                                    player.prepare()
                                                    player.setPlaybackSpeed(currentSpeed)
                                                    player.playWhenReady = true
                                                    isPlaying = true
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "Error: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        onSubtitleTextViewCreated = { subtitleTextView = it },
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
                                            showControls = true
                                        },
                                        onQualityChange = { quality ->
                                            val url = qualityMap[quality]
                                            if (url != null) {
                                                loadLink(
                                                    HentaiMamaVideoLink(
                                                        quality = quality,
                                                        url = url
                                                    ),
                                                    exoPlayer
                                                )
                                            }
                                            showQualityMenu = false
                                            showControls = true
                                        },
                                        onToggleFullscreen = { toggleFullscreen() },
                                        onExitFullscreen = { toggleFullscreen() },
                                        onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                                        onToggleQualityMenu = { showQualityMenu = !showQualityMenu },
                                        onDismissSpeedMenu = { showSpeedMenu = false },
                                        onDismissQualityMenu = { showQualityMenu = false },
                                        onSubtitleToggle = { hasSubtitle = !hasSubtitle },
                                        onPlayClick = {
                                            if (hosterTabs.isNotEmpty()) {
                                                extractLinksForHoster(hosterTabs[selectedHosterIndex].second)
                                            }
                                        },
                                        onRetryExtraction = {
                                            if (hosterTabs.isNotEmpty()) {
                                                extractLinksForHoster(hosterTabs[selectedHosterIndex].second)
                                            }
                                        },
                                        onPositionUpdate = { pos, dur ->
                                            currentPosition = pos
                                            if (dur > 0) duration = dur
                                        },
                                        webViewRef = null,
                                        onWebViewRefChange = {},
                                        onUrlCaptured = {}
                                    )
                                }
                                isExtractingUrl -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Extracting video links...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                extractionFailed -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (info.coverUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = info.coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("Failed to load video")
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(onClick = {
                                                    if (hosterTabs.isNotEmpty()) {
                                                        extractLinksForHoster(hosterTabs[selectedHosterIndex].second)
                                                    }
                                                }) { Text("Retry") }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    if (info.coverUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = info.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {}
                                    }
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = RoundedCornerShape(36.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                        onClick = {
                                            if (hosterTabs.isNotEmpty()) {
                                                extractLinksForHoster(hosterTabs[selectedHosterIndex].second)
                                            }
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (!info.description.isNullOrBlank()) {
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(
                                    text = info.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (info.description.length > 150) {
                                    TextButton(onClick = { expanded = !expanded }) {
                                        Text(if (expanded) "Show less" else "Show more")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (!info.genre.isNullOrBlank()) {
                                Row {
                                    Text(
                                        "Genres: ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        info.genre,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!info.author.isNullOrBlank()) {
                                Row {
                                    Text(
                                        "Author: ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(info.author, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (!info.status.isNullOrBlank()) {
                                Row {
                                    Text(
                                        "Status: ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (info.status == "Ongoing")
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            info.status,
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 2.dp
                                            ),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (hosterTabs.size > 1) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Mirrors",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(hosterTabs) { (name, index) ->
                                        FilterChip(
                                            selected = index ==
                                                hosterTabs.getOrNull(selectedHosterIndex)?.second,
                                            onClick = {
                                                selectedHosterIndex =
                                                    hosterTabs.indexOfFirst { it.second == index }
                                                extractLinksForHoster(index)
                                            },
                                            label = { Text(name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (videoLinks.size > 1) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Qualities",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(videoLinks) { link ->
                                        FilterChip(
                                            selected = link.quality == selectedQuality,
                                            onClick = { loadLink(link, exoPlayer) },
                                            label = { Text(link.quality) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }

                    if (info.episodes.isNotEmpty()) {
                        item {
                            Text(
                                "Episodes (${info.episodes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(info.episodes, key = { it.url }) { episode ->
                            val episodeCode = episode.url.trimEnd('/').substringAfterLast("/")
                            val isCurrentEpisode = episodeCode == info.videoCode

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable {
                                        if (isCurrentEpisode) return@clickable
                                        val newPath = if (episode.url.startsWith("http")) {
                                            episode.url
                                        } else {
                                            HentaiMamaNetwork.normalizeUrl(episode.url)
                                        }

                                        currentUrlPath = newPath
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentEpisode)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = episode.title.ifEmpty { "Episode" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrentEpisode)
                                                FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        episode.episodeNumber?.let {
                                            Text(
                                                "Episode ${String.format("%.0f", it)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        episode.date?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (isCurrentEpisode) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    "▶ Playing",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp
                                                    )
                                                )
                                            }
                                        } else {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (info.relatedVideos.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            Text(
                                "Related",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                                contentPadding = PaddingValues(horizontal = SpacingLarge)
                            ) {
                                items(info.relatedVideos, key = { it.videoCode }) { video ->
                                    VideoCardItem(
                                        modifier = Modifier.width(cardWidth),
                                        videoItem = video,
                                        isHorizontalCard = true,
                                        onClickVideosItem = {
                                            val newPath =
                                                HentaiMamaNetwork.normalizeUrl("/${video.videoCode}")

                                            onNavigateToVideo(video.videoCode, newPath)
                                        },
                                        onLongClickVideosItem = { _, _ -> },
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }

            is VideoLoadingState.Error -> {
                ErrorContent(
                    message = state.throwable.message ?: "Failed to load video",
                    onRetry = { viewModel.getVideoDetail(currentUrlPath) },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is VideoLoadingState.NoContent -> ErrorContent(
                message = "No content found",
                onRetry = { viewModel.getVideoDetail(currentUrlPath) },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
