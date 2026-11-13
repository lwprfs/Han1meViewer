package com.yenaly.han1meviewer.XHamster.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterImageLoader
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCollection
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterFeedItem
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListKind
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterAuth
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterUserRepo
import com.yenaly.han1meviewer.XHamster.ui.discover.XHamsterDiscoverActions
import com.yenaly.han1meviewer.XHamster.ui.discover.XHamsterDiscoverStrips
import com.yenaly.han1meviewer.XHamster.ui.entities.XHamsterPagedViewModel
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterListUiState
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterVideoGrid
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteChipRow
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class XHamsterLibraryTab(val titleRes: Int) {
    HISTORY(R.string.xhamster_watch_history),
    FAVORITES(R.string.xhamster_favorites),
    WATCH_LATER(R.string.watch_later),
    LIKED(R.string.xhamster_liked_videos),
    PLAYLISTS(R.string.xhamster_playlists),
    SUBSCRIPTIONS(R.string.xhamster_subscriptions_feed),
}

class XHamsterMyListViewModel : XHamsterPagedViewModel<HanimeInfo>() {
    var kind: XHamsterListKind = XHamsterListKind.FAVORITES

    override suspend fun load(url: String): Result<XHamsterPage<HanimeInfo>> =
        XHamsterUserRepo.myVideos(url, kind).map { XHamsterPage(it.items, it.nextUrl) }

    override fun keyOf(item: HanimeInfo): Any = item.videoCode
}

data class XHamsterPlaylistsState(
    val items: List<XHamsterCollection> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
)

class XHamsterPlaylistsViewModel : ViewModel() {
    private val _state = MutableStateFlow(XHamsterPlaylistsState())
    val state: StateFlow<XHamsterPlaylistsState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            XHamsterUserRepo.collections()
                .onSuccess { list -> _state.update { XHamsterPlaylistsState(items = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    private fun act(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block().onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(actionError = e.message ?: "error") } }
        }
    }

    fun create(title: String) = act { XHamsterUserRepo.createCollection(title) }
    fun rename(id: String, title: String) = act { XHamsterUserRepo.renameCollection(id, title) }
    fun delete(id: String) = act { XHamsterUserRepo.deleteCollection(id) }
    fun clearActionError() = _state.update { it.copy(actionError = null) }
}

class XHamsterFeedViewModel : ViewModel() {
    private val _state = MutableStateFlow<Triple<List<XHamsterFeedItem>, Boolean, String?>>(
        Triple(emptyList(), false, null),
    )
    val state: StateFlow<Triple<List<XHamsterFeedItem>, Boolean, String?>> = _state.asStateFlow()

    fun load() {
        _state.value = Triple(emptyList(), true, null)
        viewModelScope.launch {
            XHamsterUserRepo.newsFeed()
                .onSuccess { _state.value = Triple(it, false, null) }
                .onFailure { _state.value = Triple(emptyList(), false, it.message) }
        }
    }
}

private fun loginMessage(msg: String?, loginText: String): String? =
    if (msg == XHamsterUserRepo.LOGIN_REQUIRED) loginText else msg

@Composable
fun XHamsterLibraryScreen(
    initialTab: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenCollection: (path: String, title: String) -> Unit,
    discover: XHamsterDiscoverActions,
) {
    var tabName by rememberSaveable { mutableStateOf(initialTab) }
    val ctx = LocalContext.current
    val tab = XHamsterLibraryTab.entries.firstOrNull { it.name == tabName } ?: XHamsterLibraryTab.HISTORY
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.xhamster_my_library)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SiteChipRow(
                options = XHamsterLibraryTab.entries.toList(),
                selected = tab,
                label = { ctx.getString(it.titleRes) },
                onSelect = { tabName = it.name },
                key = { it.name },
            )
            when (tab) {
                XHamsterLibraryTab.PLAYLISTS -> PlaylistsTab(onOpenCollection)
                XHamsterLibraryTab.SUBSCRIPTIONS -> FeedTab(onNavigateToVideo, discover)
                else -> VideosTab(tab, onNavigateToVideo, discover)
            }
        }
    }
}

@Composable
private fun VideosTab(
    tab: XHamsterLibraryTab,
    onOpenVideo: (String) -> Unit,
    discover: XHamsterDiscoverActions,
    viewModel: XHamsterMyListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loginText = stringResource(R.string.xhamster_login_required)
    LaunchedEffect(tab) {
        viewModel.kind = when (tab) {
            XHamsterLibraryTab.HISTORY -> XHamsterListKind.WATCH_HISTORY
            XHamsterLibraryTab.LIKED -> XHamsterListKind.LIKED
            XHamsterLibraryTab.WATCH_LATER -> XHamsterListKind.WATCH_LATER
            else -> XHamsterListKind.FAVORITES
        }
        val path = when (tab) {
            XHamsterLibraryTab.HISTORY -> "/my/watch-history"
            XHamsterLibraryTab.LIKED -> "/my/liked/videos"
            XHamsterLibraryTab.WATCH_LATER -> XHamsterUserRepo.watchLaterPath()
            else -> "/my/favorites/videos"
        }
        viewModel.setUrl(XHamsterConstants.listUrl(path), force = true)
    }
    XHamsterVideoGrid(
        state = XHamsterListUiState(
            items = state.items,
            isLoading = state.isLoading,
            isLoadingMore = state.isLoadingMore,
            hasNext = state.hasNext,
            error = loginMessage(state.error, loginText),
        ),
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpenVideo = onOpenVideo,
        header = { XHamsterDiscoverStrips(discover, showFeed = false) },
    )
}

@Composable
private fun FeedTab(
    onOpenVideo: (String) -> Unit,
    discover: XHamsterDiscoverActions,
    viewModel: XHamsterFeedViewModel = viewModel(),
) {
    val (items, loading, error) = viewModel.state.collectAsStateWithLifecycle().value
    val loginText = stringResource(R.string.xhamster_login_required)
    LaunchedEffect(Unit) { viewModel.load() }
    XHamsterVideoGrid(
        state = XHamsterListUiState(
            items = items.map { it.video.copy(currentArtist = it.authorName) },
            isLoading = loading,
            error = loginMessage(error, loginText),
        ),
        onRetry = viewModel::load,
        onLoadMore = {},
        onOpenVideo = onOpenVideo,
        header = { XHamsterDiscoverStrips(discover, showFeed = false) },
    )
}

@Composable
private fun PlaylistsTab(
    onOpen: (String, String) -> Unit,
    viewModel: XHamsterPlaylistsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loginText = stringResource(R.string.xhamster_login_required)
    var dialogFor by remember { mutableStateOf<XHamsterCollection?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<XHamsterCollection?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading && state.items.isEmpty() -> SiteLoadingContent()
            state.error != null && state.items.isEmpty() ->
                SiteErrorContent(message = loginMessage(state.error, loginText), onRetry = viewModel::load)

            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "create") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { creating = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.xhamster_playlist_create))
                        }
                    }
                }
                if (state.items.isEmpty()) item(key = "empty") {
                    SiteEmptyContent(hint = stringResource(R.string.xhamster_nothing_here))
                }
                items(state.items, key = { it.id }) { c ->
                    val system = c.isDefault || c.isWatchLater
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(c.path, c.title) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                c.quantity?.let {
                                    Text(
                                        stringResource(R.string.xhamster_n_videos, it.toString()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!system) {
                                IconButton(onClick = { dialogFor = c }) { Icon(Icons.Default.Edit, stringResource(R.string.xhamster_playlist_rename)) }
                                IconButton(onClick = { deleting = c }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) PlaylistNameDialog(null, { creating = false }) { viewModel.create(it) }
    dialogFor?.let { c -> PlaylistNameDialog(c.title, { dialogFor = null }) { viewModel.rename(c.id, it) } }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(c.title) },
            confirmButton = { TextButton(onClick = { viewModel.delete(c.id); deleting = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.actionError?.let { err ->
        val ctx = LocalContext.current
        LaunchedEffect(err) {
            android.widget.Toast.makeText(
                ctx, ctx.getString(R.string.xhamster_action_failed, err), android.widget.Toast.LENGTH_LONG,
            ).show()
            viewModel.clearActionError()
        }
    }
}

@Composable
private fun PlaylistNameDialog(initial: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.xhamster_playlist_create else R.string.xhamster_playlist_rename)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.xhamster_playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text); onDismiss() }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Plain video grid for one playlist / watch-later page. */
@Composable
fun XHamsterCollectionScreen(
    path: String,
    title: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    viewModel: XHamsterMyListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(path) {
        viewModel.kind = XHamsterListKind.COLLECTION
        viewModel.setUrl(XHamsterConstants.listUrl(path))
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        XHamsterVideoGrid(
            modifier = Modifier.padding(padding),
            state = XHamsterListUiState(
                items = state.items, isLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore, hasNext = state.hasNext, error = state.error,
            ),
            onRetry = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onOpenVideo = onNavigateToVideo,
        )
    }
}
