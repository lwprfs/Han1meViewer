package com.yenaly.han1meviewer.FapFolder.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderGroupRowCard
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderPostCard
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderUserRowCard
import com.yenaly.han1meviewer.R
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderProfileScreen(
    username: String,
    initialTab: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenHashtag: (String) -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    viewModel: FapFolderProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(username, initialTab) {
        viewModel.load(username, initialTab)
    }

    LaunchedEffect(listState, uiState.hasMore, uiState.isLoadingMore, uiState.isLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 &&
                    last >= total - 4 &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.isLoading
                ) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.profile?.displayName ?: "@$username",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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
            when {
                uiState.isLoading && uiState.profile == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                uiState.headerError != null && uiState.profile == null -> ProfileError(
                    title = "Failed to load profile",
                    message = uiState.headerError ?: "",
                    onRetry = { viewModel.refresh() },
                )

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        uiState.profile?.let { profile ->
                            item(key = "profile_header") {
                                ProfileHeader(
                                    profile = profile,
                                    following = uiState.following,
                                    followPending = uiState.followPending,
                                    followError = uiState.followError,
                                    onToggleFollow = { viewModel.toggleFollow() },
                                )
                            }

                            item(key = "profile_tabs") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    uiState.tabs.forEach { tab ->
                                        FilterChip(
                                            selected = uiState.activeTab == tab.param,
                                            onClick = { viewModel.switchTab(tab.param) },
                                            label = {
                                                Text(
                                                    tab.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor =
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor =
                                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        when {
                            uiState.isLoading && uiState.items.isEmpty() -> item("items_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                            }

                            uiState.itemsError != null && uiState.items.isEmpty() ->
                                item("items_error") {
                                    ProfileError(
                                        title = "Failed to load",
                                        message = uiState.itemsError ?: "",
                                        onRetry = { viewModel.loadMore() },
                                        compact = true,
                                    )
                                }

                            uiState.items.isEmpty() -> item("items_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Nothing here yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            else -> {
                                items(uiState.items, key = { it.stableKey }) { item ->
                                    when (item) {
                                        is FapFolderFeedItem.Post -> {
                                            val post = item.post
                                            FapFolderPostCard(
                                                post = post,
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                onClickPost = {
                                                    val slug = post.slug
                                                        ?: post.postId.toString()
                                                    onOpenPost(slug)
                                                },
                                                onClickHashtag = onOpenHashtag,
                                                onClickAuthor = { author ->
                                                    val groupSlug = author.groupSlug
                                                    val user = author.username
                                                    when {
                                                        !groupSlug.isNullOrBlank() ->
                                                            onOpenGroup(groupSlug)
                                                        !user.isNullOrBlank() ->
                                                            onOpenProfile(user)
                                                    }
                                                },
                                                onClickVideo = { },
                                                onClickImage = { img, all ->
                                                    val albumId = img.photoId?.toString()
                                                        ?: all.firstOrNull()?.photoId?.toString()
                                                    if (albumId != null) onOpenAlbum(albumId)
                                                },
                                                onClickPoll = { },
                                                onClickLink = { },
                                                onLongClickPost = { },
                                            )
                                        }
                                        is FapFolderFeedItem.User -> FapFolderUserRowCard(
                                            user = item.user,
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            onClick = {
                                                if (item.user.username.isNotBlank()) {
                                                    onOpenProfile(item.user.username)
                                                }
                                            },
                                        )
                                        is FapFolderFeedItem.Community -> FapFolderGroupRowCard(
                                            group = item.group,
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            onClick = {
                                                if (item.group.slug.isNotBlank()) {
                                                    onOpenGroup(item.group.slug)
                                                }
                                            },
                                        )
                                    }
                                }
                                if (uiState.isLoadingMore) {
                                    item(key = "items_more_loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: FapFolderUserProfile,
    following: Boolean,
    followPending: Boolean,
    followError: String?,
    onToggleFollow: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            profile.coverUrl?.let { cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val stats = buildList {
                    profile.followerCount?.let { add("$it followers") }
                    profile.followingCount?.let { add("$it following") }
                    profile.postCount?.let { add("$it posts") }
                }.joinToString(" · ")
                if (stats.isNotBlank()) {
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!profile.isSelf) {
                if (following) {
                    OutlinedButton(
                        onClick = onToggleFollow,
                        enabled = !followPending,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        if (followPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Following")
                        }
                    }
                } else {
                    Button(
                        onClick = onToggleFollow,
                        enabled = !followPending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        if (followPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Follow")
                        }
                    }
                }
            }
        }

        if (!profile.bio.isNullOrBlank()) {
            Text(
                text = profile.bio ?: "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!profile.joinedLabel.isNullOrBlank()) {
            Text(
                text = "Joined ${profile.joinedLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }

        if (!followError.isNullOrBlank()) {
            Text(
                text = followError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ProfileError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    compact: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}