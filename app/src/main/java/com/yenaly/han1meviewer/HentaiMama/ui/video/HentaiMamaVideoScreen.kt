package com.yenaly.han1meviewer.HentaiMama.ui.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryRepo
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoInfo
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoLink
import com.yenaly.han1meviewer.HentaiMama.data.model.SimilarCard
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RESUME_THRESHOLD_MS = 5_000L
private const val RESUME_CLAMP_BACKOFF_MS = 5_000L
private const val SAVE_INTERVAL_MS = 2_000L
private const val DELTA_GUARD_MS = 10_000L

private val EPISODE_SUFFIX_REGEX: Regex =
    Regex(""".*-episode-\d+/?$""", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaVideoScreen(
    videoCode: String,
    path: String,
    resumePosition: Long = 0L,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToSeries: (slug: String) -> Unit,
    viewModel: HentaiMamaVideoViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val pageState by viewModel.pageState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val mirrorLinks by viewModel.mirrorLinks.collectAsStateWithLifecycle()
    val selectedMirrorIndex by viewModel.selectedMirrorIndex.collectAsStateWithLifecycle()
    val selectedMirrorLabel by viewModel.selectedMirrorLabel.collectAsStateWithLifecycle()

    val normalizedUrl: String = remember(path) {
        if (path.startsWith("http")) path
        else HentaiMamaNetwork.normalizeUrl(path)
    }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var pendingResume by remember { mutableLongStateOf(0L) }
    var resumeApplied by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isVideoVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < 200
        }
    }

    HentaiMamaHistoryRepo.init(context)

    LaunchedEffect(normalizedUrl) {
        viewModel.loadEpisodePage(normalizedUrl)
        resumeApplied = false
        pendingResume = 0L
    }

    LaunchedEffect(normalizedUrl, resumePosition) {
        val history = withContext(Dispatchers.IO) {
            HentaiMamaHistoryRepo.getByVideoCode(videoCode)
        }
        pendingResume = when {
            resumePosition > 0L -> resumePosition
            history != null && !history.completed &&
                    history.lastPosition > RESUME_THRESHOLD_MS -> history.lastPosition
            else -> 0L
        }
        if (pendingResume > 0L) showResumeDialog = true
    }

    LaunchedEffect(playerState.duration, pendingResume) {
        if (!resumeApplied && pendingResume > 0L && playerState.duration > 0L) {
            val maxSeek: Long =
                (playerState.duration - RESUME_CLAMP_BACKOFF_MS).coerceAtLeast(0L)
            val seek: Long = pendingResume.coerceAtMost(maxSeek)
            exoPlayer?.seekTo(seek)
            resumeApplied = true
        }
    }

    LaunchedEffect(isVideoVisible) {
        if (!isVideoVisible && playerState.isPlaying) {
            viewModel.setPlaying(false)
        }
    }

    LaunchedEffect(playerState.isPlaying, pageState) {
        val videoInfo: HentaiMamaVideoInfo =
            (pageState as? VideoLoadingState.Success)?.info ?: return@LaunchedEffect
        val page: EpisodeDetailPage = videoInfo.page ?: return@LaunchedEffect
        if (!playerState.isPlaying) return@LaunchedEffect
        var lastSavedPosition: Long = playerState.position
        while (isActive) {
            delay(SAVE_INTERVAL_MS)
            val pos: Long = playerState.position
            val delta: Long = pos - lastSavedPosition
            val validDelta: Long = if (delta in 0L..DELTA_GUARD_MS) delta else 0L
            lastSavedPosition = pos
            val total: Long = playerState.duration
            if (total > 0L && pos > 0L) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        HentaiMamaHistoryRepo.saveProgress(
                            videoCode = videoCode,
                            title = page.info.title,
                            coverUrl = page.info.seriesPoster,
                            episodeUrl = page.info.slug,
                            episodeNumber = page.seriesSidebar
                                .firstOrNull { it.slug == page.info.slug }
                                ?.episodeNumber
                                ?: 1f,
                            episodeTitle = page.info.title,
                            position = pos,
                            duration = total,
                            isPlaying = true,
                            completed = pos >= total - RESUME_CLAMP_BACKOFF_MS,
                        )
                    }
                }
            }
        }
    }

    fun enterFullscreen() {
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
        viewModel.setFullscreen(true)
    }

    fun exitFullscreen() {
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
        viewModel.setFullscreen(false)
    }

    fun openEpisode(episode: HentaiMamaEpisode) {
        if (episode.slug == videoCode) {
            return
        }
        val target: String = if (episode.url.startsWith("http")) episode.url
        else HentaiMamaNetwork.normalizeUrl(episode.url)
        onNavigateToVideo(episode.slug, target)
    }

    fun deriveSeriesSlug(episodeUrl: String, seriesUrlFromPage: String?): String? {
        seriesUrlFromPage?.let { url: String ->
            val slug: String = url.trimEnd('/').substringAfterLast('/')
            if (slug.isNotBlank()) return slug
        }
        if (!EPISODE_SUFFIX_REGEX.containsMatchIn(episodeUrl)) return null
        val slug: String = episodeUrl.trimEnd('/')
            .substringAfterLast('/')
            .substringBeforeLast("-episode-")
        return slug.takeIf { it.isNotBlank() }
    }

    BackHandler(enabled = true) {
        if (playerState.isFullscreen) exitFullscreen() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val videoInfo: HentaiMamaVideoInfo? =
                        (pageState as? VideoLoadingState.Success)?.info
                    val title: String = videoInfo?.page?.info?.title
                        ?: videoInfo?.title
                        ?: "Video"
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        val state = pageState
        when (state) {
            is VideoLoadingState.Loading -> LoadingContent(
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.Error -> ErrorContent(
                message = state.throwable.message ?: "Failed",
                onRetry = { viewModel.loadEpisodePage(normalizedUrl, force = true) },
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.NoContent -> ErrorContent(
                message = "No content",
                onRetry = { viewModel.loadEpisodePage(normalizedUrl, force = true) },
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.Success -> {
                val videoInfo: HentaiMamaVideoInfo = state.info
                val page: EpisodeDetailPage? = videoInfo.page

                if (page == null) {
                    ErrorContent(
                        message = "Episode page data missing",
                        onRetry = { viewModel.loadEpisodePage(normalizedUrl, force = true) },
                        modifier = Modifier.padding(paddingValues),
                    )
                } else {
                    val info = page.info

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        item(key = "player") {
                            HentaiMamaVideoPlayer(
                                state = playerState,
                                coverUrl = info.seriesPoster,
                                isFullscreen = false,
                                isVisible = isVideoVisible,
                                onPlayerReady = { exoPlayer = it },
                                onPlayPause = { viewModel.setPlaying(!playerState.isPlaying) },
                                onSeek = { pos: Long -> exoPlayer?.seekTo(pos) },
                                onSkip = { delta: Long ->
                                    val current: Long = exoPlayer?.currentPosition ?: 0L
                                    val target: Long = (current + delta).coerceAtLeast(0L)
                                    exoPlayer?.seekTo(target)
                                },
                                onToggleFullscreen = { enterFullscreen() },
                                onPositionUpdate = { pos: Long, dur: Long ->
                                    viewModel.setPosition(pos, dur)
                                },
                            )
                        }

                        item(key = "nav") {
                            HentaiMamaEpisodeNav(
                                nav = page.nav,
                                onPrev = {
                                    page.nav.prevUrl?.let { url: String ->
                                        val slug: String = url.trimEnd('/').substringAfterLast('/')
                                        onNavigateToVideo(slug, url)
                                    }
                                },
                                onSeries = {
                                    val slug: String? = deriveSeriesSlug(
                                        info.slug,
                                        page.nav.seriesUrl,
                                    )
                                    slug?.let(onNavigateToSeries)
                                },
                                onNext = {
                                    page.nav.nextUrl?.let { url: String ->
                                        val slug: String = url.trimEnd('/').substringAfterLast('/')
                                        onNavigateToVideo(slug, url)
                                    }
                                },
                            )
                        }

                        item(key = "mirrors") {
                            HentaiMamaMirrorSelector(
                                mirrors = page.player.mirrors,
                                selectedMirrorIndex = selectedMirrorIndex,
                                onMirrorSelected = { viewModel.selectMirror(it) },
                                qualities = playerState.qualityOptions,
                                selectedQuality = playerState.quality,
                                onQualitySelected = { viewModel.setQuality(it) },
                                speeds = playerState.availableSpeeds,
                                selectedSpeed = playerState.speed,
                                onSpeedSelected = { viewModel.setSpeed(it) },
                            )
                        }

                        if (extractionState is HentaiMamaExtractionState.Loading) {
                            item(key = "extracting") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .width(18.dp)
                                            .height(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = "Loading mirror ${selectedMirrorLabel ?: ""}…",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        if (extractionState is HentaiMamaExtractionState.Failed) {
                            item(key = "extraction_error") {
                                val reason: String =
                                    (extractionState as HentaiMamaExtractionState.Failed).reason
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Mirror failed",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = reason,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        TextButton(
                                            onClick = { viewModel.retryExtraction() },
                                        ) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }

                        if (mirrorLinks.size > 1) {
                            item(key = "sources_header") {
                                Text(
                                    text = "Sources (${mirrorLinks.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 4.dp,
                                    ),
                                )
                            }
                            itemsIndexed(
                                items = mirrorLinks,
                                key = { _, link: HentaiMamaVideoLink -> link.url },
                            ) { _, link: HentaiMamaVideoLink ->
                                val selected: Boolean = link.quality == playerState.quality
                                Surface(
                                    color = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                    onClick = { viewModel.setQuality(link.quality) },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = if (selected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = link.quality,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected)
                                                FontWeight.Bold
                                            else
                                                FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "handler") {
                            HentaiMamaEpisodeHandler(info = info)
                        }

                        item(key = "header") {
                            HentaiMamaEpisodeHeader(
                                info = info,
                                onSeriesClick = {
                                    val slug: String? = deriveSeriesSlug(
                                        info.slug,
                                        page.nav.seriesUrl,
                                    )
                                    slug?.let(onNavigateToSeries)
                                },
                                onGenreClick = { genre: String -> onNavigateToSearch(genre) },
                            )
                        }

                        item(key = "gallery") {
                            HentaiMamaEpisodeGallery(
                                previewUrls = info.previewUrls,
                                columns = info.galleryColumns,
                            )
                        }

                        if (page.seriesSidebar.isNotEmpty()) {
                            item(key = "sidebar_header") {
                                Text(
                                    text = "Episodes ${page.seriesSidebarCount ?: ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp,
                                    ),
                                )
                            }
                            itemsIndexed(
                                items = page.seriesSidebar,
                                key = { _, episode: HentaiMamaEpisode -> episode.slug },
                            ) { _, episode: HentaiMamaEpisode ->
                                EpisodeSidebarRow(
                                    episode = episode,
                                    onClick = { openEpisode(episode) },
                                )
                            }
                        }

                        if (page.similar.isNotEmpty()) {
                            item(key = "similar_header") {
                                Text(
                                    text = "Similar titles",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp,
                                    ),
                                )
                            }
                            itemsIndexed(
                                items = page.similar,
                                key = { _, sim: SimilarCard -> sim.slug },
                            ) { _, sim: SimilarCard ->
                                SimilarRow(
                                    title = sim.name,
                                    poster = sim.poster,
                                    rating = sim.rating,
                                    year = sim.year,
                                    episodeCount = sim.episodeCount,
                                    onClick = { onNavigateToVideo(sim.slug, sim.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (playerState.isFullscreen) {
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
                    .background(Color.Black),
            ) {
                val cover: String = (pageState as? VideoLoadingState.Success)
                    ?.info
                    ?.page
                    ?.info
                    ?.seriesPoster
                    .orEmpty()
                HentaiMamaVideoPlayer(
                    state = playerState,
                    coverUrl = cover,
                    isFullscreen = true,
                    isVisible = true,
                    onPlayerReady = { exoPlayer = it },
                    onPlayPause = { viewModel.setPlaying(!playerState.isPlaying) },
                    onSeek = { pos: Long -> exoPlayer?.seekTo(pos) },
                    onSkip = { delta: Long ->
                        val current: Long = exoPlayer?.currentPosition ?: 0L
                        val target: Long = (current + delta).coerceAtLeast(0L)
                        exoPlayer?.seekTo(target)
                    },
                    onToggleFullscreen = { exitFullscreen() },
                    onPositionUpdate = { pos: Long, dur: Long ->
                        viewModel.setPosition(pos, dur)
                    },
                )
            }
        }
    }

    if (showResumeDialog && resumePosition > 0L) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text("Resume playback?") },
            text = { Text("Resume from ${formatTime(resumePosition)}?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingResume = resumePosition
                    showResumeDialog = false
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingResume = 0L
                    resumeApplied = true
                    showResumeDialog = false
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                HentaiMamaHistoryRepo.markStartedOver(videoCode)
                            }
                        }
                    }
                }) { Text("Start over") }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.setPlaying(false) }
    }
}

@Composable
private fun EpisodeSidebarRow(
    episode: HentaiMamaEpisode,
    onClick: () -> Unit,
) {
    Surface(
        color = if (episode.isCurrent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = episode.episodeNumber?.let { n: Float -> "EP ${n.toInt()}" } ?: "EP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.date?.let { d: String ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (episode.isCurrent) {
                Text(
                    text = "▶ Current",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SimilarRow(
    title: String,
    poster: String,
    rating: Double?,
    year: Int?,
    episodeCount: Int?,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                RetryableImage(
                    model = poster,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(com.yenaly.han1meviewer.R.drawable.h_chan_loading),
                    error = painterResource(com.yenaly.han1meviewer.R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    rating?.let { r: Double ->
                        Text(
                            text = "★ %.1f".format(r),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    year?.let { y: Int ->
                        Text(
                            text = y.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    episodeCount?.let { c: Int ->
                        Text(
                            text = "$c eps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
