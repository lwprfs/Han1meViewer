package com.yenaly.han1meviewer.FapFolder.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderComment
import com.yenaly.han1meviewer.FapFolder.data.model.authorAvatar
import com.yenaly.han1meviewer.FapFolder.data.model.authorName
import com.yenaly.han1meviewer.FapFolder.data.model.authorUrl
import com.yenaly.han1meviewer.FapFolder.data.model.isGold
import com.yenaly.han1meviewer.FapFolder.data.model.liked
import com.yenaly.han1meviewer.FapFolder.data.model.reactionCount
import com.yenaly.han1meviewer.FapFolder.data.model.timestampIso
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FapFolderCommentCard(
    comment: FapFolderComment,
    onReply: () -> Unit = {},
    onThumbUp: () -> Unit = {},
    onThumbDown: () -> Unit = {},
    onReport: () -> Unit = {},
    onOpenUser: ((String) -> Unit)? = null,
    onViewMoreReplies: (() -> Unit)? = null,
    repliesLoading: Boolean = false,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showReplies by remember(comment.commentId, expanded) { mutableStateOf(expanded) }

    val avatarClickModifier = if (onOpenUser != null && !comment.authorUrl.isNullOrBlank()) {
        Modifier.combinedClickable(
            onClick = { onOpenUser(comment.authorUrl!!) },
            onLongClick = {},
        )
    } else {
        Modifier
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = {}),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(avatarClickModifier),
                ) {
                    val avatar = comment.authorAvatar
                    if (!avatar.isNullOrBlank()) {
                        RetryableImage(
                            model = avatar,
                            contentDescription = comment.authorName,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            placeholder = painterResource(R.drawable.h_chan_loading_small),
                            error = painterResource(R.drawable.h_chan_load_failed_small),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = comment.authorName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.authorName.ifBlank { "Unknown" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (comment.isGold) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = "GOLD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    if (comment.timestampIso.isNotBlank()) {
                        Text(
                            text = comment.timestampIso,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = onReport, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_report_24),
                        contentDescription = stringResource(R.string.report_reason_hint),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            val body = comment.bodyPlain.ifBlank {
                comment.bodyHtml.replace(Regex("<[^>]+>"), "").trim()
            }
            if (body.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (comment.attachmentUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    comment.attachmentUrls.take(4).forEach { url ->
                        RetryableImage(
                            model = url,
                            contentDescription = "Attachment",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                            placeholder = painterResource(R.drawable.h_chan_loading_small),
                            error = painterResource(R.drawable.h_chan_load_failed_small),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onThumbUp) {
                    Icon(
                        imageVector = if (comment.liked) Icons.Filled.ThumbUp
                        else Icons.Outlined.ThumbUp,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(comment.reactionCount.toString())
                }
                TextButton(onClick = onThumbDown) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_thumb_down_off_alt_24),
                        contentDescription = null,
                    )
                }
                TextButton(onClick = onReply) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_reply_24),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reply))
                }
            }

            if (comment.replyCount > 0) {
                val buttonLabel = when {
                    repliesLoading -> "Loading replies…"
                    showReplies -> "Hide ${comment.replyCount} replies"
                    else -> "View ${comment.replyCount} replies"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (repliesLoading) return@TextButton
                            showReplies = !showReplies
                            if (showReplies) onViewMoreReplies?.invoke()
                        },
                        enabled = !repliesLoading,
                    ) {
                        if (repliesLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(buttonLabel)
                    }
                }
            }

            if (showReplies && comment.replies.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    comment.replies.take(3).forEach { reply ->
                        NestedReplyRow(
                            reply = reply,
                            onOpenUser = onOpenUser,
                        )
                    }
                    if (comment.replies.size > 3) {
                        Text(
                            text = "+${comment.replies.size - 3} more replies",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NestedReplyRow(
    reply: FapFolderComment,
    onOpenUser: ((String) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape),
        ) {
            val avatar = reply.authorAvatar
            if (!avatar.isNullOrBlank()) {
                RetryableImage(
                    model = avatar,
                    contentDescription = reply.authorName,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    placeholder = painterResource(R.drawable.h_chan_loading_small),
                    error = painterResource(R.drawable.h_chan_load_failed_small),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = reply.authorName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.authorName.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reply.isGold) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "GOLD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            val replyBody = reply.bodyPlain.ifBlank {
                reply.bodyHtml.replace(Regex("<[^>]+>"), "").trim()
            }
            if (replyBody.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = replyBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}