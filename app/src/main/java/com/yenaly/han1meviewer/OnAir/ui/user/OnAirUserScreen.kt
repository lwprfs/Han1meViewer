package com.yenaly.han1meviewer.OnAir.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.OnAir.data.local.OnAirProfileCache
import com.yenaly.han1meviewer.OnAir.data.model.OnAirUserProfile
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.OnAir.ui.components.OnAirVideoCardItem
import com.yenaly.han1meviewer.OnAir.ui.home.InfiniteScrollFooter
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirUserScreen(
    username: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onLoadFailure: (String) -> Unit = {},
) {
    var state by remember {
        mutableStateOf<VideoLoadingState<OnAirUserProfile>>(VideoLoadingState.Loading)
    }
    var videos by remember { mutableStateOf<List<OnAirVideoCard>>(emptyList()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var pendingPage by remember { mutableIntStateOf(0) }
    var reloadTick by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val columns = rememberVideoGridColumns()

    LaunchedEffect(username, reloadTick) {
        state = VideoLoadingState.Loading
        videos = emptyList()
        currentPage = 1
        totalPages = 1
        pendingPage = 0
        isLoadingMore = false

        OnAirNetworkRepo.getUserProfile(username, 1).collect { s ->
            state = s
            if (s is VideoLoadingState.Success) {
                videos = s.info.videos
                totalPages = s.info.pageInfo?.total ?: 1
                if (s.info.isCurrentUser) {
                    OnAirProfileCache.setCurrentUsername(s.info.username)
                    OnAirProfileCache.put(s.info.username, s.info)
                }
            }
        }
    }

    LaunchedEffect(gridState, isLoadingMore, totalPages, state, currentPage) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }.distinctUntilChanged().collect { (total, last) ->
            val s = state
            if (s is VideoLoadingState.Success &&
                total > 4 && last >= total - 4 &&
                !isLoadingMore && currentPage < totalPages &&
                pendingPage == 0
            ) {
                pendingPage = currentPage + 1
            }
        }
    }

    LaunchedEffect(pendingPage) {
        if (pendingPage <= 0) return@LaunchedEffect
        isLoadingMore = true
        OnAirNetworkRepo.getUserProfile(username, pendingPage).collect { ns ->
            if (ns is VideoLoadingState.Success) {
                val incoming = ns.info
                videos = (videos + incoming.videos).distinctBy { it.videoId }
                totalPages = incoming.pageInfo?.total ?: totalPages
                currentPage = pendingPage
                if (incoming.isCurrentUser) {
                    val merged = incoming.copy(videos = videos)
                    OnAirProfileCache.put(merged.username, merged)
                    state = VideoLoadingState.Success(merged)
                } else {
                    val s = state
                    if (s is VideoLoadingState.Success) {
                        state = VideoLoadingState.Success(s.info.copy(videos = videos))
                    }
                }
            }
            isLoadingMore = false
            pendingPage = 0
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is VideoLoadingState.Error) {
            onLoadFailure(s.throwable.message ?: "User profile failed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@$username", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            when (val s = state) {
                is VideoLoadingState.Loading -> LoadingContent()
                is VideoLoadingState.Error -> ErrorContent(
                    message = s.throwable.message ?: "Failed",
                    onRetry = { reloadTick++ },
                )
                is VideoLoadingState.NoContent -> ErrorContent(
                    message = "User not found",
                    onRetry = { reloadTick++ },
                )
                is VideoLoadingState.Success -> {
                    val profile = s.info
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(SpacingNormal),
                        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                        verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                        enableItemAnimation = false,
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            UserHeader(profile = profile)
                        }
                        items(videos, key = { it.videoId }) { card ->
                            OnAirVideoCardItem(
                                card = card,
                                onClick = { onOpenVideo(card.videoId) },
                            )
                        }
                        if (videos.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "No uploads",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                InfiniteScrollFooter(isLoadingMore = isLoadingMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHeader(profile: OnAirUserProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = profile.avatarUrl
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = profile.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = profile.displayName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (profile.isCurrentUser) {
                        Spacer(Modifier.size(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "You",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                val meta = buildList {
                    profile.followerCount?.let { add("$it followers") }
                    profile.followingCount?.let { add("$it following") }
                    profile.videoCount?.let { add("$it videos") }
                    profile.pictureCount?.let { add("$it pictures") }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        profile.bio?.let { bio ->
            if (bio.isNotBlank()) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Uploads",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
