package com.yenaly.han1meviewer.XHamster.ui.entities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.common.XHamsterImageLoader
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterAccountSettings
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterChannel
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCreator
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDashboard
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGallery
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterProfile
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterAuth
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterListViewModel
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterSource
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterVideoGrid
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

class XHamsterChannelsViewModel : XHamsterPagedViewModel<XHamsterChannel>() {
    override suspend fun load(url: String): Result<XHamsterPage<XHamsterChannel>> =
        XHamsterRepo.channels(url)

    override fun keyOf(item: XHamsterChannel): Any = item.path
}

class XHamsterCreatorsViewModel : XHamsterPagedViewModel<XHamsterCreator>() {
    override suspend fun load(url: String): Result<XHamsterPage<XHamsterCreator>> =
        XHamsterRepo.creators(url)

    override fun keyOf(item: XHamsterCreator): Any = item.path
}

class XHamsterPhotosViewModel : XHamsterPagedViewModel<XHamsterGallery>() {
    override suspend fun load(url: String): Result<XHamsterPage<XHamsterGallery>> =
        XHamsterRepo.galleries(url)

    override fun keyOf(item: XHamsterGallery): Any = item.id
}

@Composable
private fun EntityImage(url: String, modifier: Modifier, circle: Boolean = false) {
    val loader = XHamsterImageLoader.get(LocalContext.current)
    RetryableImage(
        model = url,
        modifier = modifier.then(if (circle) Modifier.clip(CircleShape) else Modifier),
        placeholder = painterResource(R.drawable.h_chan_loading),
        error = painterResource(R.drawable.h_chan_load_failed),
        contentScale = ContentScale.Crop,
        imageLoader = loader,
    )
}

@Composable
private fun NearEndEffect(gridState: LazyGridState, size: Int, onLoadMore: () -> Unit) {
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(gridState, size) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
}

@Composable
private fun NearEndEffect(listState: LazyListState, size: Int, onLoadMore: () -> Unit) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(listState, size) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
}

@Composable
private fun EntityScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding -> Box(modifier = Modifier.padding(padding)) { content() } }
}

@Composable
private fun OptionChips(
    options: List<XHamsterOption>,
    selectedPath: String,
    onSelect: (XHamsterOption) -> Unit,
) {
    if (options.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.path }) { option ->
            FilterChip(
                selected = option.path == selectedPath,
                onClick = { onSelect(option) },
                label = { Text(option.name, maxLines = 1) },
            )
        }
    }
}

private fun formatCount(value: Long?): String? = XHamsterFormat.count(value)

@Composable
fun XHamsterChannelsScreen(
    onBack: () -> Unit,
    onOpenChannel: (path: String, title: String) -> Unit,
    viewModel: XHamsterChannelsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.setUrl(XHamsterConstants.listUrl("/channels")) }
    val gridState = rememberLazyGridState()
    NearEndEffect(gridState, state.items.size) { viewModel.loadMore() }

    EntityScaffold(stringResource(R.string.xhamster_channels), onBack) {
        when {
            state.isLoading && state.items.isEmpty() -> SiteLoadingContent()
            state.error != null && state.items.isEmpty() ->
                SiteErrorContent(message = state.error, onRetry = { viewModel.refresh() })

            state.items.isEmpty() -> SiteEmptyContent(hint = stringResource(R.string.xhamster_nothing_here))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.path }) { channel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChannel(channel.path, channel.name) },
                    ) {
                        EntityImage(
                            channel.logoUrl,
                            Modifier
                                .fillMaxWidth()
                                .size(width = 160.dp, height = 90.dp),
                        )
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = listOfNotNull(
                                formatCount(channel.videoCount)?.let {
                                    stringResource(R.string.xhamster_n_videos, it)
                                },
                                formatCount(channel.subscribers)?.let {
                                    stringResource(R.string.xhamster_n_subscribers, it)
                                },
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun XHamsterCreatorsScreen(
    onBack: () -> Unit,
    onOpenCreator: (path: String, title: String) -> Unit,
    onOpenTopVideos: () -> Unit,
    viewModel: XHamsterCreatorsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.setUrl(XHamsterConstants.listUrl("/creators")) }
    val listState = rememberLazyListState()
    NearEndEffect(listState, state.items.size) { viewModel.loadMore() }

    EntityScaffold(stringResource(R.string.xhamster_creators), onBack) {
        when {
            state.isLoading && state.items.isEmpty() -> SiteLoadingContent()
            state.error != null && state.items.isEmpty() ->
                SiteErrorContent(message = state.error, onRetry = { viewModel.refresh() })

            state.items.isEmpty() -> SiteEmptyContent(hint = stringResource(R.string.xhamster_nothing_here))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "top-videos") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTopVideos),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.xhamster_top_creator_videos),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                items(state.items, key = { it.path }) { creator ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCreator(creator.path, creator.name) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EntityImage(creator.avatarUrl, Modifier.size(56.dp), circle = true)
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (creator.verified) "${creator.name} ✓" else creator.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = listOfNotNull(
                                formatCount(creator.videoCount)?.let {
                                    stringResource(R.string.xhamster_n_videos, it)
                                },
                                formatCount(creator.viewsCount)?.let {
                                    stringResource(R.string.xhamster_n_views, it)
                                },
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.isLoadingMore) item { LoadingRow() }
            }
        }
    }
}

@Composable
fun XHamsterTopCreatorVideosScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    viewModel: XHamsterListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var path by remember { mutableStateOf("/creators/videos/sa") }
    var periods by remember { mutableStateOf<List<XHamsterOption>>(emptyList()) }

    LaunchedEffect(Unit) {
        XHamsterRepo.topCreatorPeriods(XHamsterConstants.listUrl(path)).onSuccess { list ->
            periods = list
            list.firstOrNull { it.selected }?.let { path = it.path }
        }
    }
    LaunchedEffect(path) { viewModel.setSource(XHamsterSource.Path(path)) }

    EntityScaffold(stringResource(R.string.xhamster_top_creator_videos), onBack) {
        Column(Modifier.fillMaxSize()) {
            OptionChips(periods.take(12), path) { path = it.path }
            XHamsterVideoGrid(
                state = state,
                onRetry = { viewModel.refresh() },
                onLoadMore = { viewModel.loadMore() },
                onOpenVideo = onNavigateToVideo,
            )
        }
    }
}

@Composable
fun XHamsterPhotosScreen(
    onBack: () -> Unit,
    onOpenGallery: (url: String) -> Unit,
    onOpenAuthor: (path: String, title: String) -> Unit,
    viewModel: XHamsterPhotosViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var path by remember { mutableStateOf("/photos") }
    var filters by remember { mutableStateOf<List<XHamsterOption>>(emptyList()) }
    LaunchedEffect(Unit) {
        XHamsterRepo.photoFilters().onSuccess { filters = it }
    }
    LaunchedEffect(path) { viewModel.setUrl(XHamsterConstants.listUrl(path)) }
    val gridState = rememberLazyGridState()
    NearEndEffect(gridState, state.items.size) { viewModel.loadMore() }

    EntityScaffold(stringResource(R.string.xhamster_photos), onBack) {
        Column(Modifier.fillMaxSize()) {
            OptionChips(filters, path) { path = it.path }
            when {
                state.isLoading && state.items.isEmpty() -> SiteLoadingContent()
                state.error != null && state.items.isEmpty() ->
                    SiteErrorContent(message = state.error, onRetry = { viewModel.refresh() })

                state.items.isEmpty() ->
                    SiteEmptyContent(hint = stringResource(R.string.xhamster_nothing_here))

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { gallery ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenGallery(gallery.url) },
                        ) {
                            EntityImage(
                                gallery.coverUrl,
                                Modifier
                                    .fillMaxWidth()
                                    .size(width = 150.dp, height = 150.dp),
                            )
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    gallery.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val meta = listOfNotNull(
                                    gallery.photosCount?.let {
                                        stringResource(R.string.xhamster_n_photos, it.toString())
                                    },
                                    gallery.viewsText,
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                gallery.authorName?.let { name ->
                                    Text(
                                        name,
                                        modifier = Modifier.clickable {
                                            gallery.authorPath?.let { onOpenAuthor(it, name) }
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    if (state.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
                }
            }
        }
    }
}

@Composable
fun XHamsterProfileScreen(
    path: String,
    title: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
) {
    var profile by remember(path) { mutableStateOf<XHamsterProfile?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    val imageLoader = XHamsterImageLoader.get(LocalContext.current)

    LaunchedEffect(path, reload) {
        loading = true
        error = null
        XHamsterRepo.profile(path)
            .onSuccess { profile = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    EntityScaffold(profile?.name ?: title, onBack) {
        val current = profile
        when {
            loading && current == null -> SiteLoadingContent()
            current == null -> SiteErrorContent(message = error, onRetry = { reload++ })
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.name, style = MaterialTheme.typography.headlineSmall)
                        val stats = listOfNotNull(
                            formatCount(current.videosTotal)?.let {
                                stringResource(R.string.xhamster_n_videos, it)
                            },
                            formatCount(current.momentsTotal)?.let {
                                stringResource(R.string.xhamster_n_moments, it)
                            },
                            formatCount(current.favoritesTotal)?.takeIf { current.favoritesTotal != 0L }
                                ?.let { stringResource(R.string.xhamster_n_favorites, it) },
                        ).joinToString(" · ")
                        if (stats.isNotEmpty()) Text(
                            stats,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!current.isPublic) Text(
                            stringResource(R.string.xhamster_profile_private),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (current.channels.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(current.channels, key = { it.path }) { ch ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onOpenPath(ch.path, ch.name) },
                                        label = { Text(ch.name) },
                                    )
                                }
                            }
                        }
                    }
                }
                items(current.videos, key = { it.videoCode }) { video ->
                    com.yenaly.han1meviewer.ui.component.SiteVideoCard(
                        title = video.title,
                        coverUrl = video.coverUrl,
                        onClick = { onNavigateToVideo(video.videoCode) },
                        aspectRatio = 16f / 9f,
                        views = video.views,
                        duration = video.duration,
                        imageLoader = imageLoader,
                    )
                }
                if (current.videos.isNotEmpty() && (current.videosTotal ?: 0L) > current.videos.size) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPath("${XHamsterConstants.relativePath(path)}/videos", current.name) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                stringResource(R.string.xhamster_view_all_videos),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XHamsterCreatorDashboardScreen(
    onBack: () -> Unit,
    onOpenTopVideos: () -> Unit,
) {
    var dash by remember { mutableStateOf<XHamsterDashboard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    val loginRequired = stringResource(R.string.xhamster_login_required)

    LaunchedEffect(reload) {
        loading = true
        error = null
        if (!XHamsterAuth.isLoggedIn) {
            error = loginRequired
        } else {
            XHamsterRepo.dashboard()
                .onSuccess { dash = it }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
        }
        loading = false
    }

    EntityScaffold(stringResource(R.string.xhamster_creator_dashboard), onBack) {
        val d = dash
        when {
            loading && d == null -> SiteLoadingContent()
            d == null -> SiteErrorContent(message = error, onRetry = { reload++ })
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(stringResource(R.string.xhamster_dash_balance), d.balance ?: "0")
                StatCard(stringResource(R.string.xhamster_dash_pending), d.pending ?: "0")
                StatCard(stringResource(R.string.xhamster_dash_premium), d.premiumRevenue ?: "0")
                StatCard(
                    stringResource(R.string.xhamster_dash_adshare),
                    stringResource(if (d.adShareEnabled) R.string.xhamster_on else R.string.xhamster_off),
                )
                StatCard(
                    stringResource(R.string.xhamster_dash_upload),
                    stringResource(if (d.canUpload) R.string.xhamster_on else R.string.xhamster_off),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenTopVideos),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        stringResource(R.string.xhamster_top_creator_videos),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (d.menu.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        d.menu.joinToString(" · ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun XHamsterAccountSettingsScreen(
    onBack: () -> Unit,
    onOpenInBrowser: (path: String) -> Unit,
) {
    var settings by remember { mutableStateOf<XHamsterAccountSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    val loginRequired = stringResource(R.string.xhamster_login_required)

    LaunchedEffect(reload) {
        loading = true
        error = null
        if (!XHamsterAuth.isLoggedIn) {
            error = loginRequired
        } else {
            XHamsterRepo.accountSettings()
                .onSuccess { settings = it }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
        }
        loading = false
    }

    EntityScaffold(stringResource(R.string.xhamster_account_settings), onBack) {
        val s = settings
        when {
            loading && s == null -> SiteLoadingContent()
            s == null -> SiteErrorContent(message = error, onRetry = { reload++ })
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.xhamster_settings_profile), style = MaterialTheme.typography.titleMedium)
                s.displayName?.let { StatCard(stringResource(R.string.xhamster_username), it) }
                s.email?.let {
                    StatCard(
                        stringResource(R.string.xhamster_email),
                        if (s.emailVerified) "$it ✓" else it,
                    )
                }
                s.gender?.let { StatCard(stringResource(R.string.xhamster_gender), it) }
                s.country?.let { StatCard(stringResource(R.string.xhamster_country), it) }
                s.relation?.let { StatCard(stringResource(R.string.xhamster_relationship), it) }

                if (s.privacy.isNotEmpty()) {
                    Text(
                        stringResource(R.string.xhamster_settings_privacy),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    s.privacy.forEach { StatCard(it.title, it.url) }
                }

                Text(
                    stringResource(R.string.xhamster_settings_edit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInBrowser("/my/edit/profile") },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        stringResource(R.string.xhamster_edit_profile_web),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInBrowser("/my/edit/password") },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        stringResource(R.string.xhamster_change_password_web),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}
