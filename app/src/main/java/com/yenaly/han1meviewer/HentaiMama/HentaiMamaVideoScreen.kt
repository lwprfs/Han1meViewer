package com.yenaly.han1meviewer.HentaiMama

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import android.view.WindowManager
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.MissAV.MissAvVideoPlayer
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.rememberCardResponsiveWidth
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaVideoScreen(
    videoCode: String,
    path: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // Player states
    var playerStarted by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var isExtractingUrl by remember { mutableStateOf(false) }
    var extractionFailed by remember { mutableStateOf(false) }
    var capturedUrl by remember { mutableStateOf("") }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
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
    var showPlayButton by remember { mutableStateOf(true) }
    var isFetchingLinks by remember { mutableStateOf(false) }
    
    // Episode tracking - CRITICAL for episode switching
    var currentEpisodeCode by remember { mutableStateOf(videoCode) }
    var currentEpisodePath by remember { mutableStateOf(path) }

    val availableSpeeds = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }

    // Load video detail when path changes - this is the key fix
    LaunchedEffect(currentEpisodePath) {
        Log.d("HentaiMamaVideo", "Loading episode: code=$currentEpisodeCode, path=$currentEpisodePath")
        
        // Reset all player states
        playerStarted = false
        currentUrl = ""
        isExtractingUrl = false
        extractionFailed = false
        showPlayButton = true
        videoLinks = emptyList()
        qualityMap = emptyMap()
        
        // Reset player
        exoPlayer?.release()
        exoPlayer = null
        
        // Load new video detail
        viewModel.getVideoDetail(currentEpisodePath)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Function to extract video links and start playing
    fun extractAndPlay() {
        coroutineScope.launch {
            isFetchingLinks = true
            extractionFailed = false
            
            try {
                val detailBody = withContext(Dispatchers.IO) {
                    val response = HentaiMamaNetwork.service.getVideoDetail(currentEpisodePath)
                    if (response.isSuccessful) response.body()?.string() ?: ""
                    else ""
                }
                
                if (detailBody.isEmpty()) {
                    extractionFailed = true
                    isFetchingLinks = false
                    return@launch
                }
                
                val links = withContext(Dispatchers.IO) {
                    HentaiMamaParser.videoListParse(
                        detailBody,
                        HentaiMamaConstants.BASE_URL,
                        HentaiMamaConstants.API_URL
                    )
                }
                
                if (links.isNotEmpty()) {
                    videoLinks = links
                    qualityMap = links.associate { it.quality to it.url }
                    val bestQuality = links.first()
                    currentUrl = bestQuality.url
                    selectedQuality = bestQuality.quality
                    playerStarted = true
                    showPlayButton = false
                    
                    Toast.makeText(context, "Available: ${links.joinToString(", ") { it.quality }}", Toast.LENGTH_SHORT).show()
                } else {
                    extractionFailed = true
                    Toast.makeText(context, "No video sources found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                extractionFailed = true
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isFetchingLinks = false
            }
        }
    }

    // Position update callback
    val onPositionUpdate: (Long, Long) -> Unit = { pos, dur ->
        currentPosition = pos
        duration = dur
    }

    // Fullscreen toggle
    val toggleFullscreen = {
        isFullscreenMode = !isFullscreenMode
        if (isFullscreenMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    BackHandler(enabled = true) {
        if (isFullscreenMode) {
            toggleFullscreen()
        } else {
            onBack()
        }
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
                }
            )
        }
    ) { paddingValues ->
        when (val state = videoState) {
            is VideoLoadingState.Loading -> LoadingContent(modifier = Modifier.padding(paddingValues))

            is VideoLoadingState.Success -> {
                val info = state.info
                val (cardWidth, _) = rememberCardResponsiveWidth()

                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Video Player
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playerStarted && currentUrl.isNotEmpty()) {
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
                                    onPlayerCreated = { player ->
                                        exoPlayer = player
                                        if (currentUrl.isNotEmpty()) {
                                            try {
                                                val dataSourceFactory = DefaultDataSource.Factory(
                                                    context,
                                                    DefaultHttpDataSource.Factory()
                                                        .setDefaultRequestProperties(
                                                            hashMapOf("Referer" to (Preferences.hentaiMamaBaseUrl ?: HentaiMamaConstants.BASE_URL))
                                                        )
                                                )
                                                val mediaSource = if (currentUrl.contains(".m3u8")) {
                                                    HlsMediaSource.Factory(dataSourceFactory)
                                                        .createMediaSource(MediaItem.fromUri(currentUrl))
                                                } else {
                                                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                                                        .createMediaSource(MediaItem.fromUri(currentUrl))
                                                }
                                                player.setMediaSource(mediaSource)
                                                player.prepare()
                                                player.playWhenReady = true
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onContainerCreated = {},
                                    onSubtitleTextViewCreated = { subtitleTextView = it },
                                    onPlayPause = {
                                        exoPlayer?.let { player ->
                                            if (player.isPlaying) {
                                                player.pause()
                                                isPlaying = false
                                            } else {
                                                player.play()
                                                isPlaying = true
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
                                    },
                                    onQualityChange = { quality ->
                                        selectedQuality = quality
                                        val url = qualityMap[quality]
                                        if (url != null && exoPlayer != null) {
                                            currentUrl = url
                                            try {
                                                val dataSourceFactory = DefaultDataSource.Factory(
                                                    context,
                                                    DefaultHttpDataSource.Factory()
                                                        .setDefaultRequestProperties(
                                                            hashMapOf("Referer" to (Preferences.hentaiMamaBaseUrl ?: HentaiMamaConstants.BASE_URL))
                                                        )
                                                )
                                                val mediaSource = if (url.contains(".m3u8")) {
                                                    HlsMediaSource.Factory(dataSourceFactory)
                                                        .createMediaSource(MediaItem.fromUri(url))
                                                } else {
                                                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                                                        .createMediaSource(MediaItem.fromUri(url))
                                                }
                                                val currentPos = exoPlayer?.currentPosition ?: 0
                                                exoPlayer?.setMediaSource(mediaSource)
                                                exoPlayer?.seekTo(currentPos)
                                                exoPlayer?.prepare()
                                                exoPlayer?.playWhenReady = true
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        showQualityMenu = false
                                    },
                                    onToggleFullscreen = { toggleFullscreen() },
                                    onExitFullscreen = { toggleFullscreen() },
                                    onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                                    onToggleQualityMenu = { showQualityMenu = !showQualityMenu },
                                    onDismissSpeedMenu = { showSpeedMenu = false },
                                    onDismissQualityMenu = { showQualityMenu = false },
                                    onSubtitleToggle = { hasSubtitle = !hasSubtitle },
                                    onPlayClick = { extractAndPlay() },
                                    onRetryExtraction = { extractAndPlay() },
                                    onPositionUpdate = onPositionUpdate,
                                    webViewRef = null,
                                    onWebViewRefChange = {},
                                    onUrlCaptured = {}
                                )
                            } else if (isFetchingLinks) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Extracting video links...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else if (extractionFailed) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (info.coverUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = info.coverUrl,
                                            contentDescription = "Video cover",
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
                                            Text(
                                                "Failed to load video",
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { extractAndPlay() }) {
                                                Text("Retry")
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (info.coverUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = info.coverUrl,
                                        contentDescription = "Video cover",
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
                                    onClick = { extractAndPlay() }
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

                    // Title
                    item {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Description
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

                    // Details
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (!info.genre.isNullOrBlank()) {
                                Row {
                                    Text("Genres: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(info.genre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (!info.author.isNullOrBlank()) {
                                Row {
                                    Text("Author: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(info.author, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (!info.status.isNullOrBlank()) {
                                Row {
                                    Text("Status: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (info.status == "Ongoing") MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(info.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    // Quality options
                    if (videoLinks.size > 1) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Available Qualities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    videoLinks.forEach { link ->
                                        FilterChip(
                                            selected = link.quality == selectedQuality,
                                            onClick = {
                                                selectedQuality = link.quality
                                                currentUrl = link.url
                                                exoPlayer?.let { player ->
                                                    try {
                                                        val dataSourceFactory = DefaultDataSource.Factory(
                                                            context,
                                                            DefaultHttpDataSource.Factory()
                                                                .setDefaultRequestProperties(
                                                                    hashMapOf("Referer" to (Preferences.hentaiMamaBaseUrl ?: HentaiMamaConstants.BASE_URL))
                                                                )
                                                        )
                                                        val mediaSource = if (link.url.contains(".m3u8")) {
                                                            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(link.url))
                                                        } else {
                                                            androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(link.url))
                                                        }
                                                        val pos = player.currentPosition
                                                        player.setMediaSource(mediaSource)
                                                        player.seekTo(pos)
                                                        player.prepare()
                                                        player.playWhenReady = true
                                                    } catch (e: Exception) {}
                                                }
                                            },
                                            label = { Text(link.quality) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Divider
                    item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }

                    // ============================================================
                    // EPISODES SECTION - FIXED with proper navigation
                    // ============================================================
                    if (info.episodes.isNotEmpty()) {
                        item {
                            Text(
                                "Episodes (${info.episodes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(info.episodes) { episode ->
                            // Get the episode code from the URL
                            val episodeCode = episode.url.trimEnd('/').substringAfterLast("/")
                            
                            // Check if this is the current episode
                            val isCurrentEpisode = episodeCode == currentEpisodeCode || 
                                                  episode.url.contains("/$currentEpisodeCode") ||
                                                  (episode.episodeNumber != null && 
                                                   episode.episodeNumber == info.episodes.find { 
                                                       it.url.contains("/$currentEpisodeCode") 
                                                   }?.episodeNumber)
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable {
                                        // Extract episode path and code
                                        val episodePath = episode.url.removePrefix(HentaiMamaConstants.BASE_URL)
                                        val newCode = episodeCode
                                        
                                        Log.d("HentaiMamaVideo", "Episode clicked: code=$newCode, path=$episodePath")
                                        
                                        // Update current episode tracking BEFORE navigation
                                        currentEpisodeCode = newCode
                                        currentEpisodePath = episodePath
                                        
                                        // Navigate to new episode
                                        onNavigateToVideo(newCode)
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
                                            text = episode.title.ifEmpty { "Episode ${episode.episodeNumber ?: ""}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrentEpisode) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (episode.episodeNumber != null) {
                                            Text(
                                                "Episode ${String.format("%.0f", episode.episodeNumber)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isCurrentEpisode) 
                                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!episode.date.isNullOrBlank()) {
                                            Text(
                                                episode.date, 
                                                style = MaterialTheme.typography.bodySmall, 
                                                color = if (isCurrentEpisode)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isCurrentEpisode) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    "▶ Playing",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play episode",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Related Videos
                    if (info.relatedVideos.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            Text(
                                "Related Videos",
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
                                        onClickVideosItem = { onNavigateToVideo(video.videoCode) },
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
                Log.e("HentaiMamaVideo", "Error loading video: ${state.throwable.message}")
                ErrorContent(
                    message = state.throwable.message ?: "Failed to load video",
                    onRetry = { 
                        Log.d("HentaiMamaVideo", "Retrying: path=$currentEpisodePath")
                        viewModel.getVideoDetail(currentEpisodePath) 
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is VideoLoadingState.NoContent -> ErrorContent(
                message = "No content found",
                onRetry = { viewModel.getVideoDetail(currentEpisodePath) },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
