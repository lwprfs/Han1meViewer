package com.yenaly.han1meviewer.FapFolder.ui.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderCommentParser
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderCommentCard
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.LoadMoreFooter

/** Comment block shared by the video post screen and the image post screen. */
internal fun LazyListScope.fapFolderCommentItems(
    post: FapFolderPost,
    uiState: FapFolderPostDetailUiState,
    viewModel: FapFolderPostDetailViewModel,
    onOpenProfile: (String) -> Unit,
) {
    if (!uiState.commentPostError.isNullOrBlank()) {
        item(key = "comment_post_error") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = uiState.commentPostError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Comments",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${post.commentCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Recent" to FapFolderCommentParser.FILTER_MOST_RECENT,
                    "Top" to FapFolderCommentParser.FILTER_TOP,
                    "All" to FapFolderCommentParser.FILTER_ALL,
                ).forEach { (label, filter) ->
                    CommentFilterChip(
                        label = label,
                        selected = uiState.commentsActiveFilter == filter,
                        onClick = { viewModel.changeCommentsFilter(filter) },
                    )
                }
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
                ) { CircularProgressIndicator() }
            }
        }

        uiState.comments.isEmpty() && uiState.commentsError != null -> {
            item(key = "comments_error") {
                PostErrorBlock(
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = "No comments yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                            FapFolderUrlUtils.extractUsername(url)
                        }.getOrNull()
                        if (!username.isNullOrBlank()) onOpenProfile(username)
                    },
                    onViewMoreReplies = { viewModel.toggleReplies(comment.commentId) },
                    repliesLoading = uiState.repliesLoadingFor == comment.commentId,
                    expanded = uiState.expandedReplies.contains(comment.commentId),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Only show the footer while more comments are really coming. When everything the
            // post reports is loaded there is no spinner and no "load more" state at all.
            if (uiState.commentsHasMore || uiState.commentsLoading) {
                item(key = "comments_footer") {
                    LoadMoreFooter(
                        state = if (uiState.commentsLoading) PageLoadingState.Loading
                        else PageLoadingState.Success(Unit),
                        loadedPage = uiState.commentsNextPage - 1,
                        isLoadingMore = uiState.commentsLoading,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
