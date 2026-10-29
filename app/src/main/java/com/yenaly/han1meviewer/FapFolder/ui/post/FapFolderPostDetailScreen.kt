package com.yenaly.han1meviewer.FapFolder.ui.post

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderCommentParser
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderCommentCard
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderImageLightboxDialog
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderPostCard
import com.yenaly.han1meviewer.FapFolder.ui.video.FapFolderVideoPlayerScreen
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.LoadMoreFooter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderPostDetailScreen(
    slugOrId: String,
    autoPlay: Boolean,
    onBack: () -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    viewModel: FapFolderPostDetailViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var videoToPlay by remember { mutableStateOf<FapFolderMedia.Video?>(null) }
    var imageViewerPayload by remember {
        mutableStateOf<Pair<List<FapFolderMedia.Image>, Int>?>(null)
    }

    LaunchedEffect(slugOrId) {
        viewModel.load(slugOrId)
    }

    LaunchedEffect(autoPlay, uiState.post) {
        if (!autoPlay) return@LaunchedEffect
        if (videoToPlay != null) return@LaunchedEffect
        val post = uiState.post ?: return@LaunchedEffect
        val video = post.primaryVideo ?: return@LaunchedEffect
        videoToPlay = video
    }

    LaunchedEffect(uiState.refreshToken, uiState.post) {
        val pendingId = viewModel.pendingAutoPlayVideoId() ?: return@LaunchedEffect
        val post = uiState.post ?: return@LaunchedEffect
        val video = post.media.filterIsInstance<FapFolderMedia.Video>()
            .firstOrNull { it.videoId == pendingId || it.onPlayId == pendingId }
            ?: return@LaunchedEffect
        viewModel.clearPendingAutoPlay()
        videoToPlay = video
    }

    LaunchedEffect(listState, uiState.commentsHasMore, uiState.commentsLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .map { (total, last) -> total > 4 && last >= total - 4 }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && uiState.commentsHasMore && !uiState.commentsLoading) {
                    viewModel.loadMoreComments()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Post",
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
            val post = uiState.post
            when {
                uiState.postLoading && post == null -> LoadingBlock("Loading post…")

                post == null && uiState.postError != null -> ErrorBlock(
                    title = "Failed to load post",
                    message = uiState.postError ?: "Unknown error",
                    onRetry = { viewModel.refresh() },
                )

                post == null -> EmptyBlock("Post not found")

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        item(key = "post_card") {
                            FapFolderPostCard(
                                post = post,
                                isSaved = uiState.saved,
                                isReactionPending = uiState.reactionsPending,
                                isPollPending = uiState.pollVotePendingOptionId != null,
                                onClickPost = { },
                                onClickHashtag = onOpenHashtag,
                                onClickAuthor = { author ->
                                    val groupSlug = author.groupSlug
                                    val username = author.username
                                    when {
                                        !groupSlug.isNullOrBlank() -> onOpenGroup(groupSlug)
                                        !username.isNullOrBlank() -> onOpenProfile(username)
                                    }
                                },
                                onClickVideo = { video -> videoToPlay = video },
                                onClickImage = { clicked, all ->
                                    val idx = all.indexOfFirst { it.photoId == clicked.photoId }
                                    imageViewerPayload = all to idx.coerceAtLeast(0)
                                },
                                onClickPoll = { },
                                onClickPollOption = { poll, option ->
                                    viewModel.votePoll(poll, option.id)
                                },
                                onClickLink = { },
                                onClickComment = { },
                                onClickShare = { },
                                onClickLike = { viewModel.react(positive = true) },
                                onClickDislike = { viewModel.react(positive = false) },
                                onClickSave = { viewModel.toggleSave() },
                                onLongClickPost = { },
                            )
                        }

                        if (!uiState.commentPostError.isNullOrBlank()) {
                            item(key = "comment_post_error") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        text = uiState.commentPostError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }
                        }

                        val albumId = post.firstGallery?.albumId
                            ?: post.primaryImage?.photoId?.toString()
                        if (albumId != null) {
                            item(key = "album_link") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    FilledTonalButton(onClick = { onOpenAlbum(albumId) }) {
                                        Text("Open album")
                                    }
                                }
                            }
                        }

                        item(key = "comments_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Comments (${post.commentCount})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CommentFilterChip(
                                        label = "Recent",
                                        selected = uiState.commentsActiveFilter ==
                                                FapFolderCommentParser.FILTER_MOST_RECENT,
                                        onClick = {
                                            viewModel.changeCommentsFilter(
                                                FapFolderCommentParser.FILTER_MOST_RECENT
                                            )
                                        },
                                    )
                                    CommentFilterChip(
                                        label = "Top",
                                        selected = uiState.commentsActiveFilter ==
                                                FapFolderCommentParser.FILTER_TOP,
                                        onClick = {
                                            viewModel.changeCommentsFilter(
                                                FapFolderCommentParser.FILTER_TOP
                                            )
                                        },
                                    )
                                    CommentFilterChip(
                                        label = "All",
                                        selected = uiState.commentsActiveFilter ==
                                                FapFolderCommentParser.FILTER_ALL,
                                        onClick = {
                                            viewModel.changeCommentsFilter(
                                                FapFolderCommentParser.FILTER_ALL
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        when {
                            uiState.comments.isEmpty() && uiState.commentsLoading -> {
                                item(key = "comments_loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            uiState.comments.isEmpty() && uiState.commentsError != null -> {
                                item(key = "comments_error") {
                                    ErrorBlock(
                                        title = "Failed to load comments",
                                        message = uiState.commentsError ?: "Unknown error",
                                        onRetry = { viewModel.loadMoreComments() },
                                    )
                                }
                            }

                            uiState.comments.isEmpty() -> {
                                item(key = "comments_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "No comments yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            else -> {
                                items(
                                    items = uiState.comments,
                                    key = { "comment_${it.commentId}" },
                                ) { comment ->
                                    FapFolderCommentCard(
                                        comment = comment,
                                        onReply = { },
                                        onThumbUp = { viewModel.toggleLiked(comment.commentId) },
                                        onThumbDown = { },
                                        onReport = { },
                                        onOpenUser = { url ->
                                            val username = runCatching {
                                                com.yenaly.han1meviewer.FapFolder.common
                                                    .FapFolderUrlUtils.extractUsername(url)
                                            }.getOrNull()
                                            if (!username.isNullOrBlank()) {
                                                onOpenProfile(username)
                                            }
                                        },
                                        onViewMoreReplies = {
                                            viewModel.toggleReplies(comment.commentId)
                                        },
                                        repliesLoading =
                                            uiState.repliesLoadingFor == comment.commentId,
                                        expanded = uiState.expandedReplies.contains(comment.commentId),
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 4.dp,
                                        ),
                                    )
                                }

                                item(key = "comments_footer") {
                                    LoadMoreFooter(
                                        state = when {
                                            !uiState.commentsHasMore ->
                                                PageLoadingState.NoMoreData
                                            uiState.commentsLoading ->
                                                PageLoadingState.Loading
                                            else ->
                                                PageLoadingState.Success(Unit)
                                        },
                                        loadedPage = uiState.commentsNextPage - 1,
                                        isLoadingMore = uiState.commentsLoading,
                                        modifier = Modifier.padding(vertical = 12.dp),
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
            title = uiState.post?.bodyPlain?.take(60).orEmpty(),
            onDismiss = { videoToPlay = null },
            postId = uiState.post?.postId,
            coverUrl = coverUrlOf(uiState.post),
            postPermalinkForReferer = uiState.post?.permalink,
            onExpired = {
                val pid = video.videoId ?: video.onPlayId
                videoToPlay = null
                if (pid != null) viewModel.requestAutoPlayAfterRefresh(pid)
                viewModel.refresh()
            },
        )
    }

    imageViewerPayload?.let { (images, initialIndex) ->
        FapFolderImageLightboxDialog(
            images = images,
            initialIndex = initialIndex,
            onDismiss = { imageViewerPayload = null },
        )
    }
}

private fun coverUrlOf(post: com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost?): String? {
    if (post == null) return null
    return post.primaryImage?.fullUrl
        ?: post.primaryVideo?.posterUrl
        ?: post.firstGallery?.photos?.firstOrNull()?.fullUrl
}

@Composable
private fun CommentFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun LoadingBlock(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBlock(title: String, message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyBlock(hint: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
