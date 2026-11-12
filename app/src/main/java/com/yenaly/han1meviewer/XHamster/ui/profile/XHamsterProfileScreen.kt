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

    Column(Modifier.fillMaxSize()) {
        ProfileHeader(profile, onOpenPath)
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
                ProfileTab.VIDEOS -> ProfileVideos(videosPath, onNavigateToVideo)
                ProfileTab.SHORTS -> ProfileShorts(shortsPath, profile.shorts)
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: XHamsterEntityProfile, onOpenPath: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLoader = XHamsterImageLoader.get(context)
    var subscribed by remember(profile.path) { mutableStateOf(profile.subscribed) }
    var busy by remember { mutableStateOf(false) }
    val loginRequired = stringResource(R.string.xhamster_login_required)
    val canSubscribe = profile.entityId > 0L && !profile.subscriptionModel.isNullOrBlank() && !profile.isMine

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
                val onClick: () -> Unit = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            XHamsterRepo.setSubscribed(
                                uploader = XHamsterUploader(
                                    name = profile.name, path = profile.path, avatarUrl = profile.avatarUrl,
                                    subscribers = profile.subscribers, verified = profile.verified, isChannel = false,
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
                if (subscribed) OutlinedButton(onClick = onClick, enabled = !busy) { Text(stringResource(R.string.xhamster_subscribed)) }
                else Button(onClick = onClick, enabled = !busy) { Text(stringResource(R.string.xhamster_subscribe)) }
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
private fun ProfileVideos(path: String, onNavigateToVideo: (String) -> Unit) {
    val vm: XHamsterListViewModel = viewModel(key = "xh-profile-videos-$path")
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(path) { vm.setSource(XHamsterSource.Path(path)) }
    XHamsterVideoGrid(
        state = state,
        onRetry = { vm.refresh() },
        onLoadMore = { vm.loadMore() },
        onOpenVideo = onNavigateToVideo,
    )
}

@Composable
private fun ProfileShorts(path: String, preview: List<XHamsterShort>) {
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
        )
    }
}

@Composable
private fun ShortsPlayerDialog(
    items: List<XHamsterShort>,
    initialIndex: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
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
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(pagerState.currentPage, items.size) {
        failed = false
        val short = items.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        player.setMediaSource(
            ProgressiveMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(short.videoUrl))
        )
        player.prepare()
        player.play()
        if (hasMore && pagerState.currentPage >= items.size - 3) onLoadMore()
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
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(16.dp).padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (short.title.isNotBlank()) Text(short.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        XHamsterFormat.count(short.views)?.let {
                            Text(stringResource(R.string.xhamster_n_views, it), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
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
        }
    }
}
