package com.yenaly.han1meviewer.XHamster.ui.video

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import com.yenaly.han1meviewer.ui.component.SiteBackWithHomeButton
import com.yenaly.han1meviewer.util.PlayerOrientation
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
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.common.XHamsterImageLoader
import com.yenaly.han1meviewer.XHamster.common.XHamsterSettings
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDetail
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterNetwork
import com.yenaly.han1meviewer.XHamster.data.repository.XHamsterHistoryRepo
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
import com.yenaly.han1meviewer.ui.component.player.createSitePlayer

private const val PLAYER_TAG = "XHamsterPlayer"
private const val RESUME_THRESHOLD_MS = 5_000L
private const val RESUME_CLAMP_BACKOFF_MS = 5_000L
private const val SAVE_INTERVAL_MS = 2_000L
private const val DELTA_GUARD_MS = 10_000L

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private fun buildXHamsterMediaSource(context: Context, url: String): MediaSource {
    val http = OkHttpDataSource.Factory(XHamsterNetwork.streamClient)
    val dsFactory = DefaultDataSource.Factory(context, http)
    if (!url.contains(".m3u8", ignoreCase = true)) {

        return ProgressiveMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri(url))
    }
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .build()
    return HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun XHamsterVideoScreen(
    videoId: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToPath: (String, String) -> Unit,
    resumePosition: Long = 0L,
    viewModel: XHamsterVideoViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }

    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val streamError by viewModel.streamError.collectAsStateWithLifecycle()

    var pendingResume by remember { mutableLongStateOf(0L) }
    var resumeApplied by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val exoPlayer = remember { mutableStateOf<ExoPlayer?>(null) }
    val currentMediaUrl = remember { mutableStateOf<String?>(null) }

    XHamsterHistoryRepo.init(context)

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
            val player = createSitePlayer(context)
            player.setMediaSource(buildXHamsterMediaSource(context, url))
            player.prepare()
            player.setPlaybackSpeed(playerState.speed)
            exoPlayer.value = player
            currentMediaUrl.value = url
        } else {
            val wasPlaying = existing.playWhenReady
            val pos = existing.currentPosition
            existing.setMediaSource(buildXHamsterMediaSource(context, url))
            existing.prepare()
            existing.seekTo(pos)
            existing.playWhenReady = wasPlaying
            currentMediaUrl.value = url
        }
    }

    val playerConfig = SitePlayerSettings.config(SiteType.XHAMSTER)
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
        val history = withContext(Dispatchers.IO) { XHamsterHistoryRepo.getByVideoCode(videoId) }
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
        val detail = (detailState as? XHamsterDetailState.Success)?.detail ?: return@LaunchedEffect
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
                        XHamsterHistoryRepo.upsertWatchProgress(
                            videoCode = videoId,
                            title = detail.title,
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
        activity?.requestedOrientation = PlayerOrientation.fullscreenRequest()
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

    val detail = (detailState as? XHamsterDetailState.Success)?.detail

    @Composable
    fun Player(fullscreen: Boolean, modifier: Modifier) {
        XHamsterVideoPlayer(
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
            title = detail?.title.orEmpty(),
            onBack = { if (fullscreen) exitFullscreen() else onBack() },
            onSpeedSelect = { viewModel.setSpeed(it) },
            onQualitySelect = { viewModel.setQuality(it) },
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
                        text = detail?.title ?: stringResource(R.string.video),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { SiteBackWithHomeButton(onBack = onBack) },
            )
        },
    ) { paddingValues ->
        when (val state = detailState) {
            is XHamsterDetailState.Loading ->
                SiteLoadingContent(modifier = Modifier.padding(paddingValues))

            is XHamsterDetailState.Error -> SiteErrorContent(
                message = state.message,
                onRetry = { viewModel.load(videoId, force = true) },
                modifier = Modifier.padding(paddingValues),
            )

            is XHamsterDetailState.Success -> {
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
                        XHamsterVideoContent(
                            info = state.detail,
                            listState = listState,
                            playerUrl = playerState.url,
                            videoId = videoId,
                            viewModel = viewModel,
                            onNavigateToVideo = onNavigateToVideo,
                            streamError = streamError,
                            onNavigateToPath = onNavigateToPath,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XHamsterVideoContent(
    info: XHamsterDetail,
    listState: LazyListState,
    playerUrl: String,
    videoId: String,
    viewModel: XHamsterVideoViewModel,
    streamError: String?,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToPath: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = XHamsterImageLoader.get(context)
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
                site = SiteType.XHAMSTER,
                videoCode = videoId,
                title = info.title,
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

        if (streamError != null) {
            item(key = "stream_error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = streamError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(info.pageUrl),
                                )
                            )
                        }
                    }) {
                        Text(stringResource(R.string.xhamster_open_in_browser))
                    }
                }
            }
        }

        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val views = XHamsterFormat.count(info.views)
                val likes = XHamsterFormat.count(info.likes)
                val meta = buildList {
                    if (views != null) add(stringResource(R.string.xhamster_views_count, views))
                    if (likes != null) add(stringResource(R.string.xhamster_likes_count, likes))
                    XHamsterFormat.duration(info.durationSec)?.let { add(it) }
                    info.dateAgo?.let { add(it) }
                    info.uploader?.let { add(it) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (info.description.isNotBlank()) {
            item(key = "description") {
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (info.tags.isNotEmpty()) {
            item(key = "tags") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    info.tags.forEach { tag ->
                        val path = tag.url
                            .removePrefix("https://xhamster.com")
                            .takeIf { it.startsWith("/") }
                        Surface(
                            modifier = if (path != null) {
                                Modifier.clickable { onNavigateToPath(path, tag.title) }
                            } else Modifier,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = tag.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        if (info.related.isNotEmpty()) {
            item(key = "related_title") {
                Text(
                    text = stringResource(R.string.xhamster_related),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(info.related.chunked(2), key = { row -> row.first().videoCode }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { video ->
                        SiteVideoCard(
                            title = video.title,
                            coverUrl = video.coverUrl,
                            onClick = { onNavigateToVideo(video.videoCode) },
                            modifier = Modifier.weight(1f),
                            views = video.views,
                            duration = video.duration,
                            imageLoader = imageLoader,
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
