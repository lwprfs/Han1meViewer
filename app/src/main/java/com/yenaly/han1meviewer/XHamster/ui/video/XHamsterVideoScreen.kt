package com.yenaly.han1meviewer.XHamster.ui.video

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterComment
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterUploader
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.ui.screen.video.VideoTabItem
import com.yenaly.han1meviewer.ui.screen.video.VideoTabsContent
import com.yenaly.han1meviewer.ui.screen.video.VideoTabKind
import androidx.compose.foundation.lazy.items
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterAuth
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
    onNavigateToUploader: (String, String) -> Unit = onNavigateToPath,
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
                            onNavigateToUploader = onNavigateToUploader,
                        )
                    },
                )
            }
        }
    }
}

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
    onNavigateToUploader: (String, String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val commentsState by viewModel.comments.collectAsStateWithLifecycle()
    val commentSending by viewModel.commentSending.collectAsStateWithLifecycle()
    val postedTick by viewModel.commentPostedTick.collectAsStateWithLifecycle()
    XHamsterMessageEffect(viewModel)
    val total = commentsState.total ?: info.commentsTotal ?: info.comments.size.toLong()
    val tabs = remember(total) {
        listOf(
            VideoTabItem(VideoTabKind.INTRODUCTION, R.string.introduction),
            VideoTabItem(VideoTabKind.COMMENT, R.string.comment, badgeCount = total.coerceIn(0L, 999L).toInt()),
        )
    }
    VideoTabsContent(
        tabs = tabs,
        selectedTabIndex = selectedTab,
        onSelectedTabChange = { selectedTab = it },
    ) { page ->
        if (page == 0) {
            XHamsterVideoDetailsPage(
                info = info,
                listState = listState,
                playerUrl = playerUrl,
                videoId = videoId,
                viewModel = viewModel,
                streamError = streamError,
                onNavigateToVideo = onNavigateToVideo,
                onNavigateToPath = onNavigateToPath,
                onNavigateToUploader = onNavigateToUploader,
            )
        } else {
            XHamsterCommentsPage(
                info = info,
                state = commentsState,
                onLoadMore = viewModel::loadMoreComments,
                onRetry = viewModel::retryComments,
                onOpenAuthor = onNavigateToUploader,
                sending = commentSending,
                postedTick = postedTick,
                onSend = viewModel::postComment,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XHamsterVideoDetailsPage(
    info: XHamsterDetail,
    listState: LazyListState,
    playerUrl: String,
    videoId: String,
    viewModel: XHamsterVideoViewModel,
    streamError: String?,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToPath: (String, String) -> Unit,
    onNavigateToUploader: (String, String) -> Unit,
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

        item(key = "engagement") {
            val engagement by viewModel.engagement.collectAsStateWithLifecycle()
            var showPlaylists by rememberSaveable { mutableStateOf(false) }
            var showReport by rememberSaveable { mutableStateOf(false) }
            XHamsterEngagementBar(
                state = engagement,
                loggedIn = XHamsterAuth.isLoggedIn,
                onLoginRequired = {
                    Toast.makeText(
                        context,
                        R.string.xhamster_action_login_required,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onLike = { viewModel.rate(true) },
                onDislike = { viewModel.rate(false) },
                onFavorite = viewModel::toggleFavorite,
                onWatchLater = viewModel::toggleWatchLater,
                onPlaylists = {
                    viewModel.loadCollections()
                    showPlaylists = true
                },
                onReport = { showReport = true },
            )
            if (showPlaylists) {
                XHamsterPlaylistPickerDialog(
                    state = engagement,
                    onDismiss = { showPlaylists = false },
                    onSave = viewModel::setPlaylists,
                )
            }
            if (showReport) {
                XHamsterReportDialog(
                    videoTitle = info.title,
                    videoUrl = info.pageUrl,
                    onDismiss = { showReport = false },
                )
            }
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
                val dislikes = XHamsterFormat.count(info.dislikes)
                val meta = buildList {
                    if (views != null) add(stringResource(R.string.xhamster_views_count, views))
                    if (likes != null) add(stringResource(R.string.xhamster_likes_count, likes))
                    if (dislikes != null && (info.dislikes ?: 0L) > 0L) {
                        add(stringResource(R.string.xhamster_dislikes_count, dislikes))
                    }
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

        info.uploaderInfo?.let { uploader ->
            item(key = "uploader") {
                val subState by viewModel.subscribe.collectAsStateWithLifecycle()
                LaunchedEffect(subState.message) {
                    val msg = subState.message ?: return@LaunchedEffect
                    Toast.makeText(
                        context,
                        context.getString(
                            if (msg == SubscribeMessage.LoginRequired) {
                                R.string.xhamster_subscribe_login_required
                            } else R.string.xhamster_subscribe_failed
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                    viewModel.consumeSubscribeMessage()
                }
                XHamsterUploaderCard(
                    uploader = uploader,
                    subscribed = subState.subscribed,
                    subscribers = subState.subscribers ?: uploader.subscribers,
                    subscribeBusy = subState.busy,
                    canSubscribe = uploader.subscriptionId != null,
                    onSubscribe = viewModel::toggleSubscribe,
                    onClick = { onNavigateToUploader(uploader.path, uploader.name) },
                )
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
            item(key = "related_grid") {
                val relatedColumns = rememberVideoGridColumns()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    info.related.chunked(relatedColumns).forEach { row ->
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
                            repeat(relatedColumns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XHamsterUploaderCard(
    uploader: XHamsterUploader,
    subscribed: Boolean,
    subscribers: Long?,
    subscribeBusy: Boolean,
    canSubscribe: Boolean,
    onSubscribe: () -> Unit,
    onClick: () -> Unit,
) {
    val imageLoader = XHamsterImageLoader.get(LocalContext.current)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uploader.avatarUrl.isNullOrBlank()) {
                RetryableImage(
                    model = uploader.avatarUrl,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    imageLoader = imageLoader,
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = uploader.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = uploader.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (uploader.verified) {
                        Icon(
                            painter = painterResource(R.drawable.ic_video_verified_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val subs = XHamsterFormat.count(subscribers)
                val kind = stringResource(
                    if (uploader.isChannel) R.string.xhamster_channel_label else R.string.xhamster_uploader_label
                )
                Text(
                    text = if (subs != null) {
                        "$kind  ·  " + stringResource(R.string.xhamster_n_subscribers, subs)
                    } else kind,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canSubscribe) {
                val label = stringResource(
                    if (subscribed) R.string.xhamster_subscribed else R.string.xhamster_subscribe
                )
                val iconRes = if (subscribed) {
                    R.drawable.ic_video_subscribed_24
                } else R.drawable.ic_video_subscribe_24
                if (subscribed) {
                    FilledTonalButton(
                        onClick = onSubscribe,
                        enabled = !subscribeBusy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(painterResource(iconRes), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onSubscribe,
                        enabled = !subscribeBusy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(painterResource(iconRes), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.xhamster_view_profile),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun XHamsterCommentsPage(
    info: XHamsterDetail,
    state: XHamsterCommentsState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenAuthor: (String, String) -> Unit,
    sending: Boolean,
    postedTick: Int,
    onSend: (String, XHamsterComment?) -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = XHamsterImageLoader.get(context)
    val listState = rememberLazyListState()
    val expanded = remember { mutableStateOf(setOf<String>()) }
    var replyTo by remember { mutableStateOf<XHamsterComment?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(postedTick) { replyTo = null }
    val onReplyClick: (XHamsterComment) -> Unit = { target ->
        replyTo = target
        scope.launch { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(listState, state.hasMore, state.loading, state.items.size) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= layout.totalItemsCount - 3
        }.collect { nearEnd ->
            if (nearEnd && state.hasMore && !state.loading && state.error == null) onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!state.writeDenied) {
            item(key = "comment_composer") {
                XHamsterCommentComposer(
                    replyTo = replyTo,
                    sending = sending,
                    postedTick = postedTick,
                    enabled = true,
                    onCancelReply = { replyTo = null },
                    onSend = { onSend(it, replyTo) },
                )
            }
        }

        if (state.writeDenied) {
            item(key = "comments_closed") {
                Text(
                    text = stringResource(R.string.xhamster_comments_closed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.items.isEmpty() && !state.loading && state.error == null) {
            item(key = "comments_empty") {
                Text(
                    text = stringResource(R.string.xhamster_no_comments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.items, key = { it.id }) { comment ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                XHamsterCommentRow(comment, imageLoader, onOpenAuthor, onReply = onReplyClick)
                if (comment.replies.isNotEmpty()) {
                    val open = comment.id in expanded.value
                    Box(modifier = Modifier.padding(start = 42.dp)) {
                        TextButton(
                            onClick = {
                                expanded.value = if (open) expanded.value - comment.id
                                else expanded.value + comment.id
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (open) R.drawable.ic_video_expand_less_24
                                    else R.drawable.ic_video_expand_more_24
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (open) {
                                    stringResource(R.string.xhamster_comment_replies_hide)
                                } else {
                                    stringResource(
                                        R.string.xhamster_comment_replies_show,
                                        comment.replies.size,
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (open) {
                        comment.replies.forEach { reply ->
                            Box(modifier = Modifier.padding(start = 42.dp)) {
                                XHamsterCommentRow(reply, imageLoader, onOpenAuthor, isReply = true, onReply = onReplyClick)
                            }
                        }
                    }
                }
            }
        }

        item(key = "comments_footer") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    state.error != null -> {
                        Text(
                            text = stringResource(R.string.xhamster_comments_failed, state.error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRetry) {
                                Text(stringResource(R.string.xhamster_comments_retry))
                            }
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
                                Icon(
                                    painter = painterResource(R.drawable.ic_video_open_in_new_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.xhamster_open_in_browser))
                            }
                        }
                    }
                    state.hasMore -> OutlinedButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.xhamster_comments_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun XHamsterCommentRow(
    comment: XHamsterComment,
    imageLoader: coil3.ImageLoader,
    onOpenAuthor: (String, String) -> Unit,
    isReply: Boolean = false,
    onReply: ((XHamsterComment) -> Unit)? = null,
) {
    val avatarSize = if (isReply) 28.dp else 32.dp
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!comment.avatarUrl.isNullOrBlank()) {
            RetryableImage(
                model = comment.avatarUrl,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape),
                placeholder = painterResource(R.drawable.h_chan_loading),
                error = painterResource(R.drawable.h_chan_load_failed),
                contentScale = ContentScale.Crop,
                imageLoader = imageLoader,
            )
        } else {
            Surface(
                modifier = Modifier.size(avatarSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(comment.author.take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (comment.authorPath != null) {
                        Modifier.clickable { onOpenAuthor(comment.authorPath, comment.author) }
                    } else Modifier,
                )
                if (comment.isOwner) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.xhamster_comment_owner),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (!comment.replyToName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_video_reply_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.xhamster_comment_reply_to, comment.replyToName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            val meta = listOfNotNull(
                comment.date,
                XHamsterFormat.count(comment.likes)?.let { "♥ $it" },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onReply != null) {
                Text(
                    text = stringResource(R.string.xhamster_comment_reply),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onReply(comment) }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}
