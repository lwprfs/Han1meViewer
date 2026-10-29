package com.yenaly.han1meviewer.FapFolder.ui.hashtag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderImageLightboxDialog
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderPostCard
import com.yenaly.han1meviewer.FapFolder.ui.video.FapFolderVideoPlayerScreen
import com.yenaly.han1meviewer.R
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderHashtagScreen(
    tag: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenHashtag: (String) -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    viewModel: FapFolderHashtagViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var videoToPlay by remember { mutableStateOf<FapFolderMedia.Video?>(null) }
    var videoPost by remember { mutableStateOf<FapFolderPost?>(null) }
    var lightboxPayload by remember {
        mutableStateOf<Pair<List<FapFolderMedia.Image>, Int>?>(null)
    }

    LaunchedEffect(tag) {
        viewModel.load(tag)
    }

    LaunchedEffect(
        listState,
        uiState.hasMore,
        uiState.isLoadingMore,
        uiState.isLoading,
        uiState.items.size,
    ) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .filter { it.first > 0 && it.second >= 0 }
            .conflate()
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
                        text = "#${uiState.normalisedTag.ifBlank { tag.removePrefix("#") }}",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Failed to load #${uiState.normalisedTag}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                uiState.empty && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No posts yet for #${uiState.normalisedTag}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.items, key = { "post_${it.postId}" }) { post ->
                            FapFolderPostCard(
                                post = post,
                                onClickPost = {
                                    val slug = post.slug ?: post.postId.toString()
                                    onOpenPost(slug)
                                },
                                onClickHashtag = onOpenHashtag,
                                onClickAuthor = { author ->
                                    val group = author.groupSlug
                                    val user = author.username
                                    when {
                                        !group.isNullOrBlank() -> onOpenGroup(group)
                                        !user.isNullOrBlank() -> onOpenProfile(user)
                                    }
                                },
                                onClickVideo = { video ->
                                    videoPost = post
                                    videoToPlay = video
                                },
                                onClickImage = { clicked, all ->
                                    val idx = all.indexOfFirst {
                                        it.photoId == clicked.photoId
                                    }.coerceAtLeast(0)
                                    lightboxPayload = all to idx
                                },
                                onClickPoll = { },
                                onClickLink = { },
                                onLongClickPost = { },
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item(key = "__loading_more__") {
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
                        if (!uiState.hasMore && uiState.items.isNotEmpty()) {
                            item(key = "__end__") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "There is no more data to show",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    videoToPlay?.let { video ->
        FapFolderVideoPlayerScreen(
            video = video,
            title = videoPost?.bodyPlain?.take(60).orEmpty(),
            onDismiss = {
                videoToPlay = null
                videoPost = null
            },
            postId = videoPost?.postId,
            coverUrl = videoPost?.primaryVideo?.posterUrl,
            postPermalinkForReferer = videoPost?.permalink,
            onExpired = {
                val post = videoPost
                videoToPlay = null
                videoPost = null
                if (post != null) {
                    val slug = post.slug ?: post.postId.toString()
                    onOpenPost(slug)
                }
            },
        )
    }

    lightboxPayload?.let { (images, initialIndex) ->
        FapFolderImageLightboxDialog(
            images = images,
            initialIndex = initialIndex,
            onDismiss = { lightboxPayload = null },
        )
    }
}