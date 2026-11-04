package com.yenaly.han1meviewer.OnAir.ui.user

import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.yenaly_libs.utils.applicationContext
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
            onLoadFailure(s.throwable.message ?: applicationContext.getString(R.string.onair_user_profile_failed))
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("@$username", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (val s = state) {
                is VideoLoadingState.Loading -> SiteLoadingContent()
                is VideoLoadingState.Error -> SiteErrorContent(
                    message = s.throwable.message ?: stringResource(R.string.site_failed),
                    onRetry = { reloadTick++ },
                )
                is VideoLoadingState.NoContent -> SiteErrorContent(
                    message = stringResource(R.string.onair_user_not_found),
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.onair_no_uploads_yet),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
    val stats = buildList {
        profile.followerCount?.let { add("$it" to stringResource(R.string.site_followers)) }
        profile.followingCount?.let { add("$it" to stringResource(R.string.site_following)) }
        profile.videoCount?.let { add("$it" to stringResource(R.string.fap_tab_videos)) }
        profile.pictureCount?.let { add("$it" to stringResource(R.string.drawer_pictures)) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                Color.Transparent,
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(3.dp)
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
                                        text = profile.displayName.firstOrNull()?.uppercase()
                                            ?: "?",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${profile.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (profile.isCurrentUser) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 3.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.onair_you),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                profile.bio?.let { bio ->
                    if (bio.isNotBlank()) {
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (stats.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stats.forEach { (value, label) ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.onair_uploads),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}
