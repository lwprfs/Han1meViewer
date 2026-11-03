package com.yenaly.han1meviewer.HentaiMama.ui.video

import com.yenaly.han1meviewer.SiteType
import kotlinx.coroutines.flow.first
import com.yenaly.han1meviewer.ui.component.SiteVideoActionBar
import com.yenaly.han1meviewer.ui.component.CollapsingPlayerLayout
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.yenaly.han1meviewer.ui.theme.VideoSimplifiedCardMinWidth
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import com.yenaly.han1meviewer.HentaiMama.data.model.SimilarCard
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.MediaSource
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaImageLoader
import com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaUrlUtils
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryRepo
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaEpisode
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val PLAYER_TAG = "HentaiMamaPlayer"
private const val RESUME_THRESHOLD_MS = 5_000L
private const val RESUME_CLAMP_BACKOFF_MS = 5_000L
private const val SAVE_INTERVAL_MS = 2_000L
private const val DELTA_GUARD_MS = 10_000L

private val EPISODE_SUFFIX_REGEX: Regex =
    Regex(""".*-episode-\d+/?$""", RegexOption.IGNORE_CASE)

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private fun buildHentaiMamaMediaSource(context: Context, url: String): MediaSource {
    val base = HentaiMamaNetwork.baseUrl.trimEnd('/')
    val http = OkHttpDataSource.Factory(HentaiMamaNetwork.sharedOkHttpClient)
        .setDefaultRequestProperties(
            hashMapOf(
                "Referer" to "$base/",
                "Origin" to base,
            )
        )
    val dsFactory = DefaultDataSource.Factory(context, http)
    val mediaItem = MediaItem.Builder().setUri(url).build()
    return if (url.substringBefore('?').contains(".m3u8", ignoreCase = true)) {
        HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
    } else {
        ProgressiveMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
    }
}

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
    val activity = remember(context) { context.findHostActivity() }

    val pageState by viewModel.pageState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val mirrorLinks by viewModel.mirrorLinks.collectAsStateWithLifecycle()
    val selectedMirrorIndex by viewModel.selectedMirrorIndex.collectAsStateWithLifecycle()
    val selectedMirrorLabel by viewModel.selectedMirrorLabel.collectAsStateWithLifecycle()

    val normalizedUrl: String = remember(path) {
        HentaiMamaUrlUtils.normalizeEpisodeUrl(path)
    }

    var pendingResume by remember { mutableLongStateOf(0L) }
    var resumeApplied by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val exoPlayer = remember { mutableStateOf<ExoPlayer?>(null) }
    val currentMediaUrl = remember { mutableStateOf<String?>(null) }

    HentaiMamaHistoryRepo.init(context)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && viewModel.playerState.value.isPlaying) {
                viewModel.setPlaying(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (viewModel.playerState.value.isFullscreen) {
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
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(PLAYER_TAG, "Screen disposing: releasing player")
            exoPlayer.value?.release()
            exoPlayer.value = null
            currentMediaUrl.value = null
        }
    }

    LaunchedEffect(playerState.url) {
        val url = playerState.url
        if (!playerState.isReady || url.isBlank()) return@LaunchedEffect

        val existing = exoPlayer.value
        if (existing != null && currentMediaUrl.value == url) {
            return@LaunchedEffect
        }

        if (existing == null) {
            Log.d(PLAYER_TAG, "Creating ExoPlayer for url=$url")
            val player = createHentaiMamaPlayer(context)
            player.setMediaSource(buildHentaiMamaMediaSource(context, url))
            player.prepare()
            player.setPlaybackSpeed(playerState.speed)
            exoPlayer.value = player
            currentMediaUrl.value = url
        } else {
            Log.d(PLAYER_TAG, "Swapping media source to url=$url")
            val wasPlaying = existing.playWhenReady
            val pos = existing.currentPosition
            existing.setMediaSource(buildHentaiMamaMediaSource(context, url))
            existing.prepare()
            existing.seekTo(pos)
            existing.playWhenReady = wasPlaying
            currentMediaUrl.value = url
        }
    }

    val playerConfig = SitePlayerSettings.config(SiteType.HENTAIMAMA)
    LaunchedEffect(playerConfig.defaultSpeed) {
        viewModel.setSpeed(playerConfig.defaultSpeed)
    }
    LaunchedEffect(playerState.isReady, playerState.url, playerConfig.autoPlay) {
        if (playerConfig.autoPlay && playerState.isReady && playerState.url.isNotBlank()) {

            delay(500)
            if (!showResumeDialog && !viewModel.playerState.value.isPlaying) {
                viewModel.setPlaying(true)
            }
        }
    }

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
        if (pendingResume > 0L && com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.HENTAIMAMA).resumePlayback) showResumeDialog = true
    }

    LaunchedEffect(playerState.duration, pendingResume, exoPlayer.value) {
        if (!resumeApplied && pendingResume > 0L && playerState.duration > 0L) {
            val maxSeek = (playerState.duration - RESUME_CLAMP_BACKOFF_MS).coerceAtLeast(0L)
            val seek = pendingResume.coerceAtMost(maxSeek)
            exoPlayer.value?.seekTo(seek)
            resumeApplied = true
        }
    }

    LaunchedEffect(playerState.isPlaying, pageState) {
        val videoInfo = (pageState as? VideoLoadingState.Success)?.info ?: return@LaunchedEffect
        val page = videoInfo.page ?: return@LaunchedEffect
        if (!playerState.isPlaying) return@LaunchedEffect
        var lastSavedPosition = playerState.position
        while (isActive) {
            delay(SAVE_INTERVAL_MS)
            val pos = playerState.position
            val delta = pos - lastSavedPosition
            val validDelta = if (delta in 0L..DELTA_GUARD_MS) delta else 0L
            lastSavedPosition = pos
            val total = playerState.duration
            if (total > 0L && pos > 0L) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        HentaiMamaHistoryRepo.saveProgress(
                            videoCode = videoCode,
                            title = page.info.title,
                            coverUrl = page.info.seriesPoster,
                            episodeUrl = normalizedUrl,
                            episodeNumber = page.seriesSidebar
                                .firstOrNull { it.slug == page.info.slug }
                                ?.episodeNumber ?: 1f,
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
        if (episode.slug == videoCode) return
        val target = HentaiMamaUrlUtils.normalizeEpisodeUrl(episode.url)
        onNavigateToVideo(episode.slug, target)
    }

    fun deriveSeriesSlug(episodeUrl: String, seriesUrlFromPage: String?): String? {
        seriesUrlFromPage?.let { url ->
            val slug = url.trimEnd('/').substringAfterLast('/')
            if (slug.isNotBlank()) return slug
        }
        if (!EPISODE_SUFFIX_REGEX.containsMatchIn(episodeUrl)) return null
        val slug = episodeUrl.trimEnd('/')
            .substringAfterLast('/')
            .substringBeforeLast("-episode-")
        return slug.takeIf { it.isNotBlank() }
    }

    BackHandler(enabled = playerState.isFullscreen) {
        exitFullscreen()
    }

    if (playerState.isFullscreen) {
        Dialog(
            onDismissRequest = { exitFullscreen() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            HentaiMamaVideoPlayer(
                state = playerState,
                exoPlayer = exoPlayer.value,
                coverUrl = (pageState as? VideoLoadingState.Success)?.info?.page?.info?.seriesPoster.orEmpty(),
                isFullscreen = true,
                onPlayPause = { viewModel.setPlaying(!playerState.isPlaying) },
                onSeek = { pos -> exoPlayer.value?.seekTo(pos) },
                onSkip = { delta ->
                    val current = exoPlayer.value?.currentPosition ?: 0L
                    val target = (current + delta).coerceAtLeast(0L)
                    exoPlayer.value?.seekTo(target)
                },
                onToggleFullscreen = { exitFullscreen() },
                onPositionUpdate = { pos, dur -> viewModel.setPosition(pos, dur) },
                modifier = Modifier.fillMaxSize(),
                showResumeButton = false,
                onResumeFromSaved = {},
                onStartFromBeginning = {},
                title = (pageState as? VideoLoadingState.Success)?.info?.page?.info?.title.orEmpty(),
                onBack = { exitFullscreen() },
                onSpeedSelect = { viewModel.setSpeed(it) },
                onQualitySelect = { viewModel.setQuality(it) },
            )
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    val videoInfo = (pageState as? VideoLoadingState.Success)?.info
                    val title = videoInfo?.page?.info?.title ?: videoInfo?.title ?: "Video"
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        val state = pageState
        when (state) {
            is VideoLoadingState.Loading -> SiteLoadingContent(modifier = Modifier.padding(paddingValues))
            is VideoLoadingState.Error -> SiteErrorContent(
                message = state.throwable.message ?: "Failed",
                onRetry = { viewModel.loadEpisodePage(normalizedUrl, force = true) },
                modifier = Modifier.padding(paddingValues),
            )
            is VideoLoadingState.NoContent -> SiteErrorContent(
                message = "No content",
                onRetry = { viewModel.loadEpisodePage(normalizedUrl, force = true) },
                modifier = Modifier.padding(paddingValues),
            )

            is VideoLoadingState.Success -> {
                val videoInfo = state.info
                val page = videoInfo.page
                if (page == null) {
                    SiteErrorContent(
                        message = "Episode page data missing",
                        onRetry = { viewModel.loadEpisodePage(normalizedUrl, force = true) },
                        modifier = Modifier.padding(paddingValues),
                    )
                } else {
                    val info = page.info
                    CollapsingPlayerLayout(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        player = {
                        if (!playerState.isFullscreen) {
                            HentaiMamaVideoPlayer(
                                state = playerState,
                                exoPlayer = exoPlayer.value,
                                coverUrl = info.seriesPoster,
                                isFullscreen = false,
                                onPlayPause = { viewModel.setPlaying(!playerState.isPlaying) },
                                onSeek = { pos -> exoPlayer.value?.seekTo(pos) },
                                onSkip = { delta ->
                                    val current = exoPlayer.value?.currentPosition ?: 0L
                                    val target = (current + delta).coerceAtLeast(0L)
                                    exoPlayer.value?.seekTo(target)
                                },
                                onToggleFullscreen = { enterFullscreen() },
                                onPositionUpdate = { pos, dur -> viewModel.setPosition(pos, dur) },
                                modifier = Modifier.fillMaxWidth(),
                                showResumeButton = showResumeDialog,
                                savedPosition = pendingResume,
                                onResumeFromSaved = {
                                    exoPlayer.value?.seekTo(pendingResume)
                                    showResumeDialog = false
                                },
                                onStartFromBeginning = {
                                    exoPlayer.value?.seekTo(0)
                                    showResumeDialog = false
                                },
                                title = info.title,
                                onBack = onBack,
                                onSpeedSelect = { viewModel.setSpeed(it) },
                                onQualitySelect = { viewModel.setQuality(it) },
                            )
                        }
                        },
                        content = {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 6.dp,
                                top = 12.dp,
                                end = 6.dp,
                                bottom = 24.dp + WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding(),
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item(key = "nav") {
                                HentaiMamaEpisodeNav(
                                    nav = page.nav,
                                    onPrev = {
                                        page.nav.prevUrl?.let { url ->
                                            val slug = url.trimEnd('/').substringAfterLast('/')
                                            onNavigateToVideo(
                                                slug,
                                                HentaiMamaUrlUtils.normalizeEpisodeUrl(url),
                                            )
                                        }
                                    },
                                    onSeries = {
                                        val slug = deriveSeriesSlug(info.slug, page.nav.seriesUrl)
                                        slug?.let(onNavigateToSeries)
                                    },
                                    onNext = {
                                        page.nav.nextUrl?.let { url ->
                                            val slug = url.trimEnd('/').substringAfterLast('/')
                                            onNavigateToVideo(
                                                slug,
                                                HentaiMamaUrlUtils.normalizeEpisodeUrl(url),
                                            )
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
                                            .padding(horizontal = 4.dp),
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
                                    val reason =
                                        (extractionState as HentaiMamaExtractionState.Failed).reason
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
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
                                            TextButton(onClick = { viewModel.retryExtraction() }) {
                                                Text("Retry")
                                            }
                                        }
                                    }
                                }
                            }

                            if (mirrorLinks.size > 1) {
                                item(key = "sources") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SiteVideoSectionHeader(title = "Sources (${mirrorLinks.size})")
                                        mirrorLinks.forEach { link ->
                                            val selected = link.quality == playerState.quality
                                            Surface(
                                                color = if (selected)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = { viewModel.setQuality(link.quality) },
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PlayArrow,
                                                        contentDescription = null,
                                                        tint = if (selected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        text = link.quality,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "site_actions") {
                                val activeLink = mirrorLinks.firstOrNull { it.quality == playerState.quality }
                                    ?: mirrorLinks.firstOrNull()
                                SiteVideoActionBar(
                                    site = SiteType.HENTAIMAMA,
                                    videoCode = videoCode,
                                    title = page.info.title,
                                    coverUrl = page.info.seriesPoster,
                                    webUrl = normalizedUrl,
                                    downloadUrl = activeLink?.url
                                        ?: playerState.url.takeIf { it.isNotBlank() },
                                    downloadQuality = activeLink?.quality.orEmpty(),
                                    resolveDownloadUrl = {

                                        viewModel.playerState.value.url.takeIf { it.isNotBlank() }
                                            ?: kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                                                viewModel.mirrorLinks.first { it.isNotEmpty() }
                                            }?.let { links ->
                                                (links.firstOrNull { it.quality == playerState.quality }
                                                    ?: links.firstOrNull())?.url
                                            }
                                    },
                                )
                            }

                            item(key = "handler") {
                                HentaiMamaEpisodeHandler(info = info)
                            }

                            item(key = "header") {
                                HentaiMamaEpisodeHeader(
                                    info = info,
                                    onSeriesClick = {
                                        val slug = deriveSeriesSlug(info.slug, page.nav.seriesUrl)
                                        slug?.let(onNavigateToSeries)
                                    },
                                    onGenreClick = { genre -> onNavigateToSearch(genre) },
                                )
                            }

                            item(key = "gallery") {
                                HentaiMamaEpisodeGallery(
                                    previewUrls = info.previewUrls,
                                    columns = info.galleryColumns,
                                )
                            }

                            if (page.seriesSidebar.isNotEmpty()) {
                                item(key = "sidebar") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SiteVideoSectionHeader(
                                            title = "Episodes ${page.seriesSidebarCount ?: ""}".trim(),
                                        )
                                        page.seriesSidebar.forEach { episode ->
                                            EpisodeSidebarRow(
                                                episode = episode,
                                                onClick = { openEpisode(episode) },
                                            )
                                        }
                                    }
                                }
                            }

                            if (page.similar.isNotEmpty()) {
                                item(key = "similar") {
                                    SimilarGrid(
                                        items = page.similar,
                                        onOpen = { sim ->
                                            onNavigateToVideo(
                                                sim.slug,
                                                HentaiMamaUrlUtils.normalizeEpisodeUrl(sim.url),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        },
                    )
                }
            }
        }
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
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                RetryableImage(
                    model = episode.thumb,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = episode.episodeNumber?.let { "EP ${it.toInt()}" } ?: "EP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    episode.rating?.let {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "%.1f".format(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.date?.let { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (episode.isCurrent) {
                Text(
                    text = "▶",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SimilarGrid(
    items: List<SimilarCard>,
    onOpen: (SimilarCard) -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }
    val uniqueItems = remember(items) { items.distinctBy { it.slug } }
    val cardAspectRatio by HentaiMamaCardSettings.effectiveAspectRatioState

    Column(verticalArrangement = Arrangement.spacedBy(SpacingNormal)) {
        SiteVideoSectionHeader(title = "Similar titles")
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacing = SpacingNormal
            val columns = maxOf(
                2,
                ((maxWidth + spacing) /
                    ((VideoSimplifiedCardMinWidth / HentaiMamaCardSettings.effectiveCardCount(1f)
                        .coerceAtLeast(0.5f)) + spacing)).toInt(),
            )
            val itemWidth = ((maxWidth - (spacing * (columns - 1))) / columns) - 0.5.dp
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                uniqueItems.forEach { sim ->
                    SiteVideoCard(
                        modifier = Modifier.width(itemWidth),
                        title = sim.name,
                        coverUrl = sim.poster,
                        aspectRatio = cardAspectRatio,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(
                            MaterialTheme.colorScheme.surfaceVariant
                        ),
                        duration = sim.episodeCount?.takeIf { it > 0 }?.let { "$it EP" },
                        reviews = sim.rating?.let { "★ %.1f".format(it) },
                        uploadTime = sim.year?.toString(),
                        imageLoader = imageLoader,
                        onClick = { onOpen(sim) },
                    )
                }
            }
        }
    }
}
