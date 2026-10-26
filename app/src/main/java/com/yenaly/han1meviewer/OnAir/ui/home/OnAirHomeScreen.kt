package com.yenaly.han1meviewer.OnAir.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.OnAir.data.model.OnAirUserProfile
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.ui.components.OnAirVideoCardItem
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirHomeScreen(
    onOpenVideo: (String) -> Unit,
    onOpenSearch: (String?) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenTags: () -> Unit,
    onOpenPictures: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnnouncement: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchSite: () -> Unit,
    onLoadFailure: (String) -> Unit = {},
    viewModel: OnAirHomeViewModel = viewModel(),
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val columns = rememberVideoGridColumns()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadHomePage() }

    LaunchedEffect(homeState) {
        if (homeState !is WebsiteState.Loading) isRefreshing = false
    }

    LaunchedEffect(gridState, isLoadingMore) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 && last >= total - 4 && !isLoadingMore) {
                    viewModel.loadNextPage()
                }
            }
    }

    LaunchedEffect(homeState) {
        val s = homeState
        if (s is WebsiteState.Error) onLoadFailure(s.throwable.message ?: "Load failed")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OnAir", color = MaterialTheme.colorScheme.onSurface) },
                actions = {
                    IconButton(onClick = onOpenPictures) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Pictures")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onSwitchSite) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_switch_24),
                            contentDescription = stringResource(R.string.switch_site),
                        )
                    }
                    IconButton(onClick = { onOpenSearch(null) }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    currentUser?.let { profile ->
                        CurrentUserAvatar(
                            profile = profile,
                            onClick = { onOpenUser(profile.username) },
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch { gridState.scrollToItem(0) }
                    viewModel.refresh()
                },
                state = refreshState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = homeState) {
                    is WebsiteState.Loading -> {
                        if (cards.isEmpty()) LoadingContent() else HomeGrid(
                            cards, gridState, columns, isLoadingMore, onOpenVideo
                        ) {
                            HomeHeader(
                                data = null,
                                onOpenTag = onOpenTag,
                                onOpenUser = onOpenUser,
                            )
                        }
                    }

                    is WebsiteState.Error -> {
                        if (cards.isEmpty()) {
                            ErrorContent(
                                message = state.throwable.message ?: "Failed to load",
                                onRetry = { viewModel.refresh() },
                            )
                        } else HomeGrid(cards, gridState, columns, isLoadingMore, onOpenVideo) {
                            HomeHeader(
                                data = null,
                                onOpenTag = onOpenTag,
                                onOpenUser = onOpenUser,
                            )
                        }
                    }

                    is WebsiteState.Success -> HomeGrid(
                        cards, gridState, columns, isLoadingMore, onOpenVideo
                    ) {
                        HomeHeader(
                            data = state.info,
                            onOpenTag = onOpenTag,
                            onOpenUser = onOpenUser,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentUserAvatar(
    profile: OnAirUserProfile,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val avatar = profile.avatarUrl
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = profile.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = profile.displayName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HomeGrid(
    cards: List<OnAirVideoCard>,
    gridState: LazyGridState,
    columns: Int,
    isLoadingMore: Boolean,
    onOpenVideo: (String) -> Unit,
    header: @Composable (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SpacingNormal),
        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
        verticalArrangement = Arrangement.spacedBy(SpacingNormal),
        enableItemAnimation = false,
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(cards, key = { it.videoId }) { card ->
            OnAirVideoCardItem(card = card, onClick = { onOpenVideo(card.videoId) })
        }
        if (cards.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfiniteScrollFooter(isLoadingMore = isLoadingMore)
            }
        }
    }
}

@Composable
internal fun InfiniteScrollFooter(isLoadingMore: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    data: OnAirHomeUiData?,
    onOpenTag: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    if (data == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPill(label = "Videos", value = data.totalVideos?.toString() ?: "—")
            StatPill(label = "Pictures", value = data.totalPictures?.toString() ?: "—")
            StatPill(label = "Members", value = data.totalMembers?.toString() ?: "—")
        }
        data.announcementTitle?.let { title ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Announcement",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.weight(1f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
