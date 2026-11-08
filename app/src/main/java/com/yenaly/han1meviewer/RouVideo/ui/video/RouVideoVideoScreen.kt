package com.yenaly.han1meviewer.RouVideo.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoFormat
import com.yenaly.han1meviewer.RouVideo.common.RouVideoSettings
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoDetail
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoNetwork
import com.yenaly.han1meviewer.RouVideo.data.repository.RouVideoHistoryRepo
import com.yenaly.han1meviewer.RouVideo.ui.home.RouVideoCardItem
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.component.CollapsingPlayerLayout
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteVideoActionBar
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val PLAYER_TAG = "RouVideoPlayer"
private const val RESUME_THRESHOLD_MS = 5_000L
private const val RESUME_CLAMP_BACKOFF_MS = 5_000L
private const val SAVE_INTERVAL_MS = 2_000L
private const val DELTA_GUARD_MS = 10_000L

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private fun buildRouVideoMediaSource(context: Context, url: String): MediaSource {
    val http = OkHttpDataSource.Factory(RouVideoNetwork.streamClient)
    val dsFactory = DefaultDataSource.Factory(context, http)
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .build()
    return HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouVideoVideoScreen(
    videoId: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    resumePosition: Long = 0L,
    viewModel: RouVideoVideoViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }

    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    var pendingResume by remember { mutableLongStateOf(0L) }
    var resumeApplied by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val exoPlayer = remember { mutableStateOf<ExoPlayer?>(null) }
    val currentMediaUrl = remember { mutableStateOf<String?>(null) }

    RouVideoHistoryRepo.init(context)

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
        if (existing != null && currentMediaUrl.value == url) return@LaunchedEffect

        if (existing == null) {
            val player = createRouVideoPlayer(context)
            player.setMediaSource(buildRouVideoMediaSource(context, url))
            player.prepare()
            player.setPlaybackSpeed(playerState.speed)
            exoPlayer.value = player
            currentMediaUrl.value = url
        } else {
            val wasPlaying = existing.playWhenReady
            val pos = existing.currentPosition
            existing.setMediaSource(buildRouVideoMediaSource(context, url))
            existing.prepare()
            existing.seekTo(pos)
            existing.playWhenReady = wasPlaying
            currentMediaUrl.value = url
        }
    }

    val playerConfig = SitePlayerSettings.config(SiteType.ROUVIDEO)
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

    LaunchedEffect(videoId) {
        viewModel.load(videoId)
        resumeApplied = false
        pendingResume = 0L
    }

    LaunchedEffect(videoId, resumePosition) {
        val history = withContext(Dispatchers.IO) { RouVideoHistoryRepo.getByVideoCode(videoId) }
        pendingResume = when {
            resumePosition > 0L -> resumePosition
            history != null && history.lastPosition > RESUME_THRESHOLD_MS &&
                    (history.totalDuration <= 0L ||
                            history.lastPosition < history.totalDuration - RESUME_CLAMP_BACKOFF_MS) ->
                history.lastPosition

            else -> 0L
        }
        if (pendingResume > 0L && playerConfig.resumePlayback) showResumeDialog = true
    }

    LaunchedEffect(playerState.duration, pendingResume, exoPlayer.value) {
        if (!resumeApplied && pendingResume > 0L && playerState.duration > 0L) {
            val maxSeek = (playerState.duration - RESUME_CLAMP_BACKOFF_MS).coerceAtLeast(0L)
            exoPlayer.value?.seekTo(pendingResume.coerceAtMost(maxSeek))
            resumeApplied = true
        }
    }

    LaunchedEffect(playerState.isPlaying, detailState) {
        val detail = (detailState as? RouVideoDetailState.Success)?.detail ?: return@LaunchedEffect
        if (!playerState.isPlaying) return@LaunchedEffect
        var lastSavedPosition = playerState.position
        while (isActive) {
            delay(SAVE_INTERVAL_MS)
            val pos = playerState.position
            val delta = pos - lastSavedPosition
            lastSavedPosition = pos
            val total = playerState.duration
            if (total > 0L && pos > 0L && delta in 0L..DELTA_GUARD_MS) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        RouVideoHistoryRepo.upsertWatchProgress(
                            videoCode = videoId,
                            title = detail.displayTitle,
                            coverUrl = detail.coverUrl,
                            currentPosition = pos,
                            totalDuration = total,
                            isPlaying = true,
                            wasPlayed = pos >= total - RESUME_CLAMP_BACKOFF_MS,
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

    BackHandler(enabled = playerState.isFullscreen) { exitFullscreen() }

    val detail = (detailState as? RouVideoDetailState.Success)?.detail

    @Composable
    fun Player(fullscreen: Boolean, modifier: Modifier) {
        RouVideoVideoPlayer(
            state = playerState,
            exoPlayer = exoPlayer.value,
            coverUrl = detail?.coverUrl.orEmpty(),
            isFullscreen = fullscreen,
            onPlayPause = { viewModel.setPlaying(!playerState.isPlaying) },
            onSeek = { pos -> exoPlayer.value?.seekTo(pos) },
            onSkip = { delta ->
                val current = exoPlayer.value?.currentPosition ?: 0L
                exoPlayer.value?.seekTo((current + delta).coerceAtLeast(0L))
            },
            onToggleFullscreen = { if (fullscreen) exitFullscreen() else enterFullscreen() },
            onPositionUpdate = { pos, dur -> viewModel.setPosition(pos, dur) },
            modifier = modifier,
            showResumeButton = !fullscreen && showResumeDialog,
            savedPosition = pendingResume,
            onResumeFromSaved = {
                exoPlayer.value?.seekTo(pendingResume)
                showResumeDialog = false
            },
            onStartFromBeginning = {
                exoPlayer.value?.seekTo(0)
                showResumeDialog = false
            },
            title = detail?.displayTitle.orEmpty(),
            onBack = { if (fullscreen) exitFullscreen() else onBack() },
            onSpeedSelect = { viewModel.setSpeed(it) },
            onQualitySelect = {},
            referer = "",
        )
    }

    if (playerState.isFullscreen) {
        Dialog(
            onDismissRequest = { exitFullscreen() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Player(fullscreen = true, modifier = Modifier.fillMaxSize())
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    Text(
                        text = detail?.displayTitle ?: stringResource(R.string.video),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        when (val state = detailState) {
            is RouVideoDetailState.Loading ->
                SiteLoadingContent(modifier = Modifier.padding(paddingValues))

            is RouVideoDetailState.Error -> SiteErrorContent(
                message = state.message,
                onRetry = { viewModel.load(videoId, force = true) },
                modifier = Modifier.padding(paddingValues),
            )

            is RouVideoDetailState.Success -> {
                CollapsingPlayerLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    player = {
                        if (!playerState.isFullscreen) {
                            Player(fullscreen = false, modifier = Modifier.fillMaxWidth())
                        }
                    },
                    content = {
                        RouVideoVideoContent(
                            info = state.detail,
                            listState = listState,
                            playerUrl = playerState.url,
                            videoId = videoId,
                            viewModel = viewModel,
                            onNavigateToVideo = onNavigateToVideo,
                            onNavigateToTag = onNavigateToTag,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouVideoVideoContent(
    info: RouVideoDetail,
    listState: LazyListState,
    playerUrl: String,
    videoId: String,
    viewModel: RouVideoVideoViewModel,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
) {
    var reversed by remember { mutableStateOf(false) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "actions") {
            SiteVideoActionBar(
                site = SiteType.ROUVIDEO,
                videoCode = videoId,
                title = info.displayTitle,
                coverUrl = info.coverUrl,
                webUrl = info.pageUrl,
                downloadUrl = playerUrl.takeIf { it.isNotBlank() },
                downloadQuality = "",
                resolveDownloadUrl = {
                    viewModel.playerState.value.url.takeIf { it.isNotBlank() }
                        ?: withTimeoutOrNull(20_000L) {
                            viewModel.playerState.first { it.url.isNotBlank() }.url
                        }
                },
            )
        }

        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = info.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val meta = buildList {
                    info.code?.let { add(it) }
                    if (info.viewCount > 0) add(stringResource(R.string.rouvideo_views_count, RouVideoFormat.count(info.viewCount)))
                    if (info.likeCount > 0) add(stringResource(R.string.rouvideo_likes_count, RouVideoFormat.count(info.likeCount)))
                    RouVideoFormat.duration(info.durationSec).takeIf { it.isNotBlank() }?.let { add(it) }
                    RouVideoFormat.age(info.createdAt).takeIf { it.isNotBlank() }?.let { add(it) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                info.series?.let { series ->
                    Text(
                        text = listOfNotNull(
                            series.displayTitle,
                            series.epLabel,
                            stringResource(
                                if (series.isCompleted) R.string.rouvideo_series_completed
                                else R.string.rouvideo_series_ongoing
                            ),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    series.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation.description(it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (info.tags.isNotEmpty()) {
            item(key = "tags") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    info.tags.forEachIndexed { index, tag ->
                        AssistChip(
                            onClick = { onNavigateToTag(tag) },
                            label = { Text(info.displayTagAt(index)) },
                        )
                    }
                }
            }
        }

        if (info.episodes.size > 1) {
            item(key = "episodes") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SiteVideoSectionHeader(
                        title = stringResource(R.string.rouvideo_episodes),
                        actionText = stringResource(
                            if (reversed) R.string.rouvideo_order_forward else R.string.rouvideo_order_reverse
                        ),
                        onActionClick = { reversed = !reversed },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val episodes = if (reversed) info.episodes.reversed() else info.episodes
                        episodes.forEach { ep ->
                            FilterChip(
                                selected = ep.id == info.id,
                                onClick = { if (ep.id != info.id) onNavigateToVideo(ep.id) },
                                label = {
                                    Text(
                                        stringResource(R.string.rouvideo_episode_label, ep.episode) +
                                                RouVideoFormat.duration(ep.durationSec)
                                                    .takeIf { it.isNotBlank() }
                                                    ?.let { " · $it" }.orEmpty()
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        if (info.related.isNotEmpty()) {
            item(key = "related_header") {
                SiteVideoSectionHeader(title = stringResource(R.string.rouvideo_related))
            }
            item(key = "related_grid") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    info.related.forEach { card ->
                        RouVideoCardItem(
                            modifier = Modifier.weight(1f),
                            card = card,
                            onClick = { onNavigateToVideo(card.id) },
                        )
                    }
                    if (info.related.size % 2 == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
