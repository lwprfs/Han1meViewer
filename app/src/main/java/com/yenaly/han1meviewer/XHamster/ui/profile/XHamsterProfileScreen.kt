package com.yenaly.han1meviewer.XHamster.ui.profile

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterShortMeta
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.common.XHamsterImageLoader
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterEntityProfile
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterShort
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterUploader
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterNetwork
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterListViewModel
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterSource
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterVideoGrid
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.launch

private enum class ProfileTab { VIDEOS, SHORTS }

@Composable
fun XHamsterProfileScreen(
    path: String,
    title: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
) {
    var profile by remember(path) { mutableStateOf<XHamsterEntityProfile?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(path, reload) {
        loading = true
        error = null
        XHamsterRepo.entityProfile(path)
            .onSuccess { profile = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(profile?.name ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val current = profile
            when {
                loading && current == null -> SiteLoadingContent()
                current == null -> SiteErrorContent(message = error, onRetry = { reload++ })
                else -> ProfileContent(current, onNavigateToVideo, onOpenPath)
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: XHamsterEntityProfile,
    onNavigateToVideo: (String) -> Unit,
    onOpenPath: (String, String) -> Unit,
) {
    val base = XHamsterConstants.relativePath(profile.path)
    val shortsPath = profile.tabs.firstOrNull { it.path.endsWith("/shorts") }?.path ?: "$base/shorts"
    val videosPath = if (profile.kind == XHamsterEntityProfile.Kind.CREATOR) "$base/newest"
    else profile.tabs.firstOrNull { !it.path.endsWith("/shorts") }?.path ?: "$base/videos"
    val hasShorts = (profile.shortsCount ?: 0L) > 0L || profile.shorts.isNotEmpty() ||
            profile.kind == XHamsterEntityProfile.Kind.USER
    val tabs = if (hasShorts) listOf(ProfileTab.VIDEOS, ProfileTab.SHORTS) else listOf(ProfileTab.VIDEOS)
    var selected by remember(profile.path) { mutableIntStateOf(0) }

    // Subscribe state is shared by the header button and the shorts player.
    val scope = rememberCoroutineScope()
    var subscribed by remember(profile.path) { mutableStateOf(profile.subscribed) }
    var busy by remember(profile.path) { mutableStateOf(false) }
    val loginRequired = stringResource(R.string.xhamster_login_required)
    val canSubscribe = profile.entityId > 0L && !profile.subscriptionModel.isNullOrBlank() && !profile.isMine
    val toggleSubscribe: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                XHamsterRepo.setSubscribed(
                    uploader = XHamsterUploader(
                        name = profile.name, path = profile.path, avatarUrl = profile.avatarUrl,
                        subscribers = profile.subscribers, verified = profile.verified,
                        isChannel = profile.kind == XHamsterEntityProfile.Kind.CHANNEL,
                        subscriptionId = profile.entityId, subscriptionModel = profile.subscriptionModel,
                        subscribed = subscribed,
                    ),
                    videoCode = "",
                    subscribe = !subscribed,
                    refererUrl = XHamsterConstants.BASE_URL + XHamsterConstants.relativePath(profile.path),
                ).onSuccess { subscribed = it }
                    .onFailure {
                        GlobalToasts.show(
                            if (it.message == "LOGIN_REQUIRED") loginRequired else (it.message ?: "Failed"),
                            level = GlobalToasts.ToastLevel.ERROR,
                        )
                    }
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ProfileHeader(profile, subscribed, busy, canSubscribe, toggleSubscribe, onOpenPath)
        if (tabs.size > 1) {
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, tab ->
                    val count = if (tab == ProfileTab.VIDEOS) profile.videoCount else profile.shortsCount
                    Tab(
                        selected = selected == i,
                        onClick = { selected = i },
                        text = {
                            Text(
                                stringResource(
                                    if (tab == ProfileTab.VIDEOS) R.string.xhamster_tab_videos
                                    else R.string.xhamster_tab_shorts
                                ) + (XHamsterFormat.count(count)?.let { " $it" } ?: "")
                            )
                        },
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tabs.getOrElse(selected) { ProfileTab.VIDEOS }) {
                ProfileTab.VIDEOS -> ProfileVideos(videosPath, profile.tags, profile.sorts, onNavigateToVideo)
                ProfileTab.SHORTS -> ProfileShorts(
                    path = shortsPath,
                    preview = profile.shorts,
                    ownerName = profile.name,
                    ownerAvatar = profile.avatarUrl,
                    pageUrl = XHamsterConstants.BASE_URL + XHamsterConstants.relativePath(profile.path) + "/shorts",
                    canSubscribe = canSubscribe,
                    subscribed = subscribed,
                    onToggleSubscribe = toggleSubscribe,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: XHamsterEntityProfile,
    subscribed: Boolean,
    busy: Boolean,
    canSubscribe: Boolean,
    onToggleSubscribe: () -> Unit,
    onOpenPath: (String, String) -> Unit,
) {
    val imageLoader = XHamsterImageLoader.get(LocalContext.current)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!profile.avatarUrl.isNullOrBlank()) {
                RetryableImage(
                    model = profile.avatarUrl,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    imageLoader = imageLoader,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val stats = listOfNotNull(
                    profile.country,
                    XHamsterFormat.count(profile.subscribers)?.let { stringResource(R.string.xhamster_n_subscribers, it) },
                    XHamsterFormat.count(profile.viewsCount)?.let { stringResource(R.string.xhamster_n_views, it) },
                    XHamsterFormat.count(profile.videoCount)?.let { stringResource(R.string.xhamster_n_videos, it) },
                    XHamsterFormat.count(profile.shortsCount)?.let { stringResource(R.string.xhamster_n_shorts, it) },
                ).joinToString(" · ")
                if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canSubscribe) {
                if (subscribed) OutlinedButton(onClick = onToggleSubscribe, enabled = !busy) { Text(stringResource(R.string.xhamster_subscribed)) }
                else Button(onClick = onToggleSubscribe, enabled = !busy) { Text(stringResource(R.string.xhamster_subscribe)) }
            }
        }
        if (!profile.aliases.isNullOrBlank()) {
            Text(profile.aliases, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!profile.isPublic) {
            Text(stringResource(R.string.xhamster_profile_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (profile.channels.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profile.channels, key = { it.path }) { ch ->
                    FilterChip(selected = false, onClick = { onOpenPath(ch.path, ch.name) }, label = { Text(ch.name) })
                }
            }
        }
    }
}

@Composable
private fun ProfileVideos(
    basePath: String,
    tags: List<XHamsterOption>,
    sorts: List<XHamsterOption>,
    onNavigateToVideo: (String) -> Unit,
) {
    var tag by remember(basePath) { mutableStateOf<String?>(null) }
    val defaultSort = sorts.firstOrNull { it.selected }?.path
    var sort by remember(basePath) { mutableStateOf(defaultSort) }

    // tag links already look like "/channels/x?tag=y"; the sort is appended on top.
    val path = remember(basePath, tag, sort) {
        val start = tag ?: basePath
        if (sort != null && sort != defaultSort) {
            start + (if (start.contains('?')) "&" else "?") + "sort=$sort"
        } else start
    }
    val vm: XHamsterListViewModel = viewModel(key = "xh-profile-videos-$path")
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(path) { vm.setSource(XHamsterSource.Path(path)) }

    Column(Modifier.fillMaxSize()) {
        if (sorts.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorts, key = { "sort-" + it.path }) { option ->
                    FilterChip(
                        selected = sort == option.path,
                        onClick = { sort = option.path },
                        label = { Text(option.name) },
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tags, key = { "tag-" + it.path }) { option ->
                    FilterChip(
                        selected = tag == option.path,
                        onClick = { tag = if (tag == option.path) null else option.path },
                        label = { Text(option.name) },
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            XHamsterVideoGrid(
                state = state,
                onRetry = { vm.refresh() },
                onLoadMore = { vm.loadMore() },
                onOpenVideo = onNavigateToVideo,
            )
        }
    }
}

@Composable
internal fun ProfileShorts(
    path: String,
    preview: List<XHamsterShort>,
    ownerName: String,
    ownerAvatar: String?,
    pageUrl: String,
    canSubscribe: Boolean,
    subscribed: Boolean,
    onToggleSubscribe: () -> Unit,
) {
    val imageLoader = XHamsterImageLoader.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var items by remember(path) { mutableStateOf(preview) }
    var nextUrl by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(true) }
    var loadingMore by remember(path) { mutableStateOf(false) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var playing by remember(path) { mutableStateOf<Int?>(null) }

    LaunchedEffect(path, reload) {
        loading = true
        error = null
        XHamsterRepo.shorts(path)
            .onSuccess { page ->
                if (page.items.isNotEmpty()) items = page.items
                nextUrl = page.nextUrl
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    fun loadMore() {
        val next = nextUrl ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            XHamsterRepo.shorts(next)
                .onSuccess { page ->
                    val known = items.map { it.id }.toHashSet()
                    items = items + page.items.filter { it.id !in known }
                    nextUrl = page.nextUrl?.takeIf { it != next }
                }
                .onFailure { nextUrl = null }
            loadingMore = false
        }
    }

    val gridState = rememberLazyGridState()
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val li = gridState.layoutInfo
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            li.totalItemsCount > 0 && last >= li.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, items.size, nextUrl) { if (nearEnd && nextUrl != null) loadMore() }

    when {
        loading && items.isEmpty() -> SiteLoadingContent()
        items.isEmpty() && error != null -> SiteErrorContent(message = error, onRetry = { reload++ })
        items.isEmpty() -> SiteEmptyContent(hint = stringResource(R.string.xhamster_shorts_empty))
        else -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(items, key = { _, s -> s.id }) { index, short ->
                Box(
                    Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { playing = index },
                ) {
                    RetryableImage(
                        model = short.coverUrl,
                        contentDescription = short.title,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed),
                        contentScale = ContentScale.Crop,
                        imageLoader = imageLoader,
                    )
                    XHamsterFormat.count(short.views)?.let {
                        Text(
                            "▶ $it", color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (loadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }

    playing?.let { start ->
        ShortsPlayerDialog(
            items = items,
            initialIndex = start,
            hasMore = nextUrl != null,
            onLoadMore = { loadMore() },
            onDismiss = { playing = null },
            referer = pageUrl,
            ownerName = ownerName,
            ownerAvatar = ownerAvatar,
            canSubscribe = canSubscribe,
            subscribed = subscribed,
            onToggleSubscribe = onToggleSubscribe,
        )
    }
}

@Composable
private fun ShortRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) { content() }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ShortsPlayerDialog(
    items: List<XHamsterShort>,
    initialIndex: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    referer: String,
    ownerName: String,
    ownerAvatar: String?,
    canSubscribe: Boolean,
    subscribed: Boolean,
    onToggleSubscribe: () -> Unit,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = XHamsterImageLoader.get(context)
    val pagerState: PagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    var failed by remember { mutableStateOf(false) }
    val failedMsg = stringResource(R.string.xhamster_shorts_play_failed)
    val loginRequired = stringResource(R.string.xhamster_login_required)
    val scope = rememberCoroutineScope()

    var muted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val metas = remember { mutableStateMapOf<Long, XHamsterShortMeta>() }
    val likeBusy = remember { mutableStateMapOf<Long, Boolean>() }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    val dsFactory = remember {
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(XHamsterNetwork.streamClient))
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    // Progress bar.
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            progress = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }

    LaunchedEffect(pagerState.currentPage, items.size) {
        failed = false
        progress = 0f
        val page = pagerState.currentPage
        val short = items.getOrNull(page) ?: return@LaunchedEffect

        // The grid only knows the silent preview clip; ask the site for the real stream
        // (and rating) of this short plus the next few so swiping stays quick.
        if (metas[short.id] == null) {
            val batch = items.drop(page).take(6).map { it.id }.filter { it !in metas }
            XHamsterRepo.shortMetas(batch, referer).onSuccess { metas.putAll(it) }
        }

        val stream = metas[short.id]?.streamUrl
        val mediaItem = MediaItem.fromUri(stream ?: short.videoUrl)
        val source = if (stream != null && stream.contains(".m3u8")) {
            HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
        }
        player.setMediaSource(source)
        player.prepare()
        player.play()
        if (hasMore && page >= items.size - 3) onLoadMore()
    }
    LaunchedEffect(failed) { if (failed) GlobalToasts.show(failedMsg, level = GlobalToasts.ToastLevel.ERROR) }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                val short = items[page]
                val meta = metas[short.id]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RetryableImage(
                        model = short.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed),
                        contentScale = ContentScale.Fit,
                        imageLoader = imageLoader,
                    )
                    if (pagerState.currentPage == page) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().clickable {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                            onRelease = { player.clearVideoTextureView(it) },
                        )
                        if (!isPlaying) {
                            Icon(
                                Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(72.dp),
                            )
                        }
                    }

                    // Title / views, bottom-left.
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 88.dp)
                            .padding(bottom = 32.dp).navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(ownerName, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (short.title.isNotBlank()) Text(short.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        XHamsterFormat.count(short.views)?.let {
                            Text(stringResource(R.string.xhamster_n_views, it), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Action column, bottom-right: follow, like, sound.
                    Column(
                        Modifier.align(Alignment.BottomEnd).padding(end = 12.dp)
                            .padding(bottom = 32.dp).navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!ownerAvatar.isNullOrBlank()) {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                RetryableImage(
                                    model = ownerAvatar,
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                        .clickable(enabled = canSubscribe && !subscribed, onClick = onToggleSubscribe),
                                    placeholder = painterResource(R.drawable.h_chan_loading),
                                    error = painterResource(R.drawable.h_chan_load_failed),
                                    contentScale = ContentScale.Crop,
                                    imageLoader = imageLoader,
                                )
                                if (canSubscribe && !subscribed) {
                                    Icon(
                                        Icons.Filled.Add, stringResource(R.string.xhamster_subscribe),
                                        tint = Color.White,
                                        modifier = Modifier.offset(y = 8.dp).size(20.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .padding(2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        val liked = meta?.liked == true
                        ShortRoundButton(onClick = {
                            val current = metas[short.id] ?: XHamsterShortMeta(null, 0L, false)
                            if (likeBusy[short.id] == true) return@ShortRoundButton
                            likeBusy[short.id] = true
                            scope.launch {
                                XHamsterRepo.setLiked(short.id, !current.liked, referer)
                                    .onSuccess { now ->
                                        metas[short.id] = current.copy(
                                            liked = now,
                                            likes = (current.likes + if (now) 1 else -1).coerceAtLeast(0),
                                        )
                                    }
                                    .onFailure {
                                        GlobalToasts.show(
                                            if (it.message == "LOGIN_REQUIRED") loginRequired else (it.message ?: "Failed"),
                                            level = GlobalToasts.ToastLevel.ERROR,
                                        )
                                    }
                                likeBusy[short.id] = false
                            }
                        }) {
                            Icon(
                                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                stringResource(R.string.xhamster_short_like),
                                tint = if (liked) Color(0xFFFF4D6D) else Color.White,
                            )
                        }
                        meta?.likes?.takeIf { it > 0 }?.let {
                            Text(XHamsterFormat.count(it).orEmpty(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }

                        ShortRoundButton(onClick = { muted = !muted }) {
                            Icon(
                                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                stringResource(if (muted) R.string.xhamster_short_unmute else R.string.xhamster_short_mute),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().height(3.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}
