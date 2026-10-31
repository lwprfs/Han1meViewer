package com.yenaly.han1meviewer.FapFolder.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAuthor
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPollOption
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.PostVariant
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FapFolderPostCard(
    post: FapFolderPost,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    isReactionPending: Boolean = false,
    isPollPending: Boolean = false,
    interactive: Boolean = true,
    onClickPost: () -> Unit = {},
    onClickHashtag: (String) -> Unit = {},
    onClickAuthor: (FapFolderAuthor) -> Unit = {},
    onClickVideo: (FapFolderMedia.Video) -> Unit = {},
    onClickImage: (FapFolderMedia.Image, List<FapFolderMedia.Image>) -> Unit = { _, _ -> },
    onClickPoll: (FapFolderMedia.Poll) -> Unit = {},
    onClickPollOption: (FapFolderMedia.Poll, FapFolderPollOption) -> Unit = { _, _ -> },
    onClickLink: (FapFolderMedia.LinkPreview) -> Unit = {},
    onClickComment: () -> Unit = {},
    onClickShare: () -> Unit = {},
    onClickLike: () -> Unit = {},
    onClickDislike: () -> Unit = {},
    onClickSave: () -> Unit = {},
    onLongClickPost: () -> Unit = {},
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClickPost,
                onLongClick = onLongClickPost,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PostHeaderRow(
                author = post.author,
                uploaderUsername = post.uploaderUsername,
                uploaderIsGold = post.uploaderIsGold,
                action = post.action,
                postedAtIso = post.postedAtIso,
                isPremium = post.isPremium,
                onClickAuthor = { onClickAuthor(post.author) },
            )

            if (post.bodyPlain.isNotBlank() || post.bodyHtml.isNotBlank()) {
                PostBodyText(
                    text = post.bodyPlain.ifBlank {
                        post.bodyHtml.replace(Regex("<[^>]+>"), "").trim()
                    },
                    hashtags = post.hashtags,
                    onClickHashtag = onClickHashtag,
                )
            }

            if (post.hashtags.isNotEmpty() && post.bodyPlain.isBlank()) {
                HashtagRow(
                    hashtags = post.hashtags,
                    onClickHashtag = onClickHashtag,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (post.media.isNotEmpty()) {
                PostMediaSection(
                    media = post.media,
                    isPollPending = isPollPending,
                    onClickVideo = onClickVideo,
                    onClickImage = onClickImage,
                    onClickPoll = onClickPoll,
                    onClickPollOption = onClickPollOption,
                    onClickLink = onClickLink,
                )
            }

            if (interactive) {
                PostStatsRow(
                    reactions = post.reactions.total,
                    comments = post.commentCount,
                    shares = post.shareCount,
                    views = post.viewCount,
                )

                PostActionsRow(
                    reactions = post.reactions,
                    saved = isSaved,
                    reactionPending = isReactionPending,
                    onClickLike = onClickLike,
                    onClickDislike = onClickDislike,
                    onClickComment = onClickComment,
                    onClickShare = onClickShare,
                    onClickSave = onClickSave,
                )
            } else if (post.viewCount != null && post.viewCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatItem(
                        icon = Icons.Filled.Visibility,
                        text = post.viewCount.toString(),
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PostHeaderRow(
    author: FapFolderAuthor,
    uploaderUsername: String?,
    uploaderIsGold: Boolean,
    action: String?,
    postedAtIso: String,
    isPremium: Boolean,
    onClickAuthor: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val avatar = author.avatarUrl
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = author.displayName,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClickAuthor),
                contentScale = ContentScale.Crop,
            )
        } else {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClickAuthor),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = author.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = author.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(onClick = onClickAuthor),
                )
                if (author.isGold) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Gold",
                        tint = Color(0xFFE4B036),
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (isPremium) {
                    Spacer(Modifier.width(4.dp))
                    AssistChip(
                        onClick = onClickAuthor,
                        label = {
                            Text(
                                text = "Premium",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFE4B036).copy(alpha = 0.2f),
                            labelColor = Color(0xFF7A5A00),
                        ),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!action.isNullOrBlank()) {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (postedAtIso.isNotBlank()) {
                    Text(
                        text = "· $postedAtIso",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!uploaderUsername.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "by $uploaderUsername",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (uploaderIsGold) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFE4B036),
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostBodyText(
    text: String,
    hashtags: List<String>,
    onClickHashtag: (String) -> Unit,
) {
    if (text.isBlank()) return
    SelectionText(text = text)
    if (hashtags.isNotEmpty()) {
        HashtagRow(
            hashtags = hashtags,
            onClickHashtag = onClickHashtag,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SelectionText(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HashtagRow(
    hashtags: List<String>,
    onClickHashtag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hashtags.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hashtags.take(12).forEach { tag ->
            AssistChip(
                onClick = { onClickHashtag(tag) },
                label = {
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun PostMediaSection(
    media: List<FapFolderMedia>,
    isPollPending: Boolean,
    onClickVideo: (FapFolderMedia.Video) -> Unit,
    onClickImage: (FapFolderMedia.Image, List<FapFolderMedia.Image>) -> Unit,
    onClickPoll: (FapFolderMedia.Poll) -> Unit,
    onClickPollOption: (FapFolderMedia.Poll, FapFolderPollOption) -> Unit,
    onClickLink: (FapFolderMedia.LinkPreview) -> Unit,
) {
    if (media.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        media.forEach { item ->
            when (item) {
                is FapFolderMedia.Video -> VideoMediaBlock(item, onClickVideo)
                is FapFolderMedia.Image -> ImageMediaBlock(item, onClickImage)
                is FapFolderMedia.Gallery -> GalleryMediaBlock(item, onClickImage)
                is FapFolderMedia.Embed -> EmbedMediaBlock(item)
                is FapFolderMedia.LinkPreview -> LinkPreviewBlock(item, onClickLink)
                is FapFolderMedia.Poll -> PollMediaBlock(
                    poll = item,
                    isPending = isPollPending,
                    onClickPoll = onClickPoll,
                    onClickOption = onClickPollOption,
                )
                is FapFolderMedia.FileAttachment -> FileBlock(item)
                is FapFolderMedia.Funding -> FundingBlock(item)
                is FapFolderMedia.Product -> ProductBlock(item)
                is FapFolderMedia.Job -> JobBlock(item)
            }
        }
    }
}

@Composable
private fun VideoMediaBlock(
    video: FapFolderMedia.Video,
    onClickVideo: (FapFolderMedia.Video) -> Unit,
) {
    val aspect = (video.aspectRatio ?: (16.0 / 9.0)).toFloat().takeIf { it > 0f } ?: (16f / 9f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black)
            .clickable { onClickVideo(video) },
        contentAlignment = Alignment.Center,
    ) {
        val poster = video.posterUrl
        if (!poster.isNullOrBlank()) {
            RetryableImage(
                model = poster,
                contentDescription = "Video",
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.h_chan_loading),
                error = painterResource(R.drawable.h_chan_load_failed),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        video.durationSeconds?.let { secs ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = formatDuration(secs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ImageMediaBlock(
    image: FapFolderMedia.Image,
    onClickImage: (FapFolderMedia.Image, List<FapFolderMedia.Image>) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .clickable { onClickImage(image, listOf(image)) },
    ) {
        RetryableImage(
            model = image.fullUrl,
            contentDescription = image.alt,
            modifier = Modifier.fillMaxWidth(),
            placeholder = painterResource(R.drawable.h_chan_loading),
            error = painterResource(R.drawable.h_chan_load_failed),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@Composable
private fun GalleryMediaBlock(
    gallery: FapFolderMedia.Gallery,
    onClickImage: (FapFolderMedia.Image, List<FapFolderMedia.Image>) -> Unit,
) {
    val firstFour = gallery.photos.take(4)
    if (firstFour.isEmpty()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .clickable { firstFour.firstOrNull()?.let { onClickImage(it, gallery.photos) } },
    ) {
        if (firstFour.size == 1) {
            RetryableImage(
                model = firstFour[0].fullUrl,
                contentDescription = firstFour[0].alt,
                modifier = Modifier.fillMaxWidth(),
                placeholder = painterResource(R.drawable.h_chan_loading),
                error = painterResource(R.drawable.h_chan_load_failed),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    firstFour.take(2).forEach { img ->
                        RetryableImage(
                            model = img.previewUrl ?: img.fullUrl,
                            contentDescription = img.alt,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp),
                            placeholder = painterResource(R.drawable.h_chan_loading),
                            error = painterResource(R.drawable.h_chan_load_failed),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                if (firstFour.size > 2) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        firstFour.drop(2).forEach { img ->
                            RetryableImage(
                                model = img.previewUrl ?: img.fullUrl,
                                contentDescription = img.alt,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(1.dp),
                                placeholder = painterResource(R.drawable.h_chan_loading),
                                error = painterResource(R.drawable.h_chan_load_failed),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
            val total = gallery.totalCount ?: gallery.photos.size
            if (total > firstFour.size) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "+${total - firstFour.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbedMediaBlock(embed: FapFolderMedia.Embed) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = embed.provider ?: "Embedded",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = embed.iframeUrl,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LinkPreviewBlock(
    link: FapFolderMedia.LinkPreview,
    onClickLink: (FapFolderMedia.LinkPreview) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable { onClickLink(link) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column {
            val thumb = link.thumbnailUrl
            if (!thumb.isNullOrBlank()) {
                RetryableImage(
                    model = thumb,
                    contentDescription = link.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = link.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!link.description.isNullOrBlank()) {
                    Text(
                        text = link.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = link.host,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PollMediaBlock(
    poll: FapFolderMedia.Poll,
    isPending: Boolean,
    onClickPoll: (FapFolderMedia.Poll) -> Unit,
    onClickOption: (FapFolderMedia.Poll, FapFolderPollOption) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${poll.totalVotes} votes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            poll.options.forEach { option ->
                val fraction = (option.percent / 100f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !isPending) { onClickOption(poll, option) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(36.dp)
                            .background(
                                if (option.isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                    )
                    Text(
                        text = "${option.label} — ${option.votes}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (isPending) {
                Text(
                    text = "Submitting vote…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FileBlock(file: FapFolderMedia.FileAttachment) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = file.name ?: "Attachment",
                style = MaterialTheme.typography.titleSmall,
            )
            if (!file.sizeLabel.isNullOrBlank()) {
                Text(
                    text = file.sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FundingBlock(funding: FapFolderMedia.Funding) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = funding.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (!funding.description.isNullOrBlank()) {
                Text(
                    text = funding.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ProductBlock(product: FapFolderMedia.Product) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!product.price.isNullOrBlank()) {
                Text(
                    text = product.price,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun JobBlock(job: FapFolderMedia.Job) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!job.location.isNullOrBlank()) {
                Text(
                    text = job.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PostStatsRow(
    reactions: Int,
    comments: Int,
    shares: Int,
    views: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (reactions > 0) {
            StatItem(
                icon = Icons.Filled.FavoriteBorder,
                text = reactions.toString(),
            )
        }
        if (comments > 0) {
            StatItem(
                icon = Icons.Outlined.ChatBubbleOutline,
                text = comments.toString(),
            )
        }
        if (shares > 0) {
            StatItem(
                icon = Icons.Outlined.Share,
                text = shares.toString(),
            )
        }
        views?.takeIf { it > 0 }?.let {
            StatItem(
                icon = Icons.Filled.Visibility,
                text = it.toString(),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PostActionsRow(
    reactions: com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount,
    saved: Boolean,
    reactionPending: Boolean,
    onClickLike: () -> Unit,
    onClickDislike: () -> Unit,
    onClickComment: () -> Unit,
    onClickShare: () -> Unit,
    onClickSave: () -> Unit,
) {
    val likeCount = reactions.byKind?.get("like") ?: 0
    val dislikeCount = reactions.byKind?.get("angry") ?: 0
    val liked = likeCount > 0
    val disliked = dislikeCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PostActionButton(
            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = likeCount.takeIf { it > 0 }?.toString() ?: "Like",
            tint = if (liked) Color(0xFFE45B5B) else MaterialTheme.colorScheme.onSurfaceVariant,
            enabled = !reactionPending,
            onClick = onClickLike,
        )
        PostActionButton(
            icon = Icons.Filled.FavoriteBorder,
            label = dislikeCount.takeIf { it > 0 }?.toString() ?: "Dislike",
            tint = if (disliked) Color(0xFF6E9AD8) else MaterialTheme.colorScheme.onSurfaceVariant,
            enabled = !reactionPending,
            onClick = onClickDislike,
        )
        PostActionButton(
            icon = Icons.Outlined.ChatBubbleOutline,
            label = "Comment",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            enabled = true,
            onClick = onClickComment,
        )
        PostActionButton(
            icon = Icons.Outlined.Share,
            label = "Share",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            enabled = true,
            onClick = onClickShare,
        )
        Spacer(Modifier.weight(1f))
        PostActionButton(
            icon = Icons.Filled.Star,
            label = if (saved) "Saved" else "Save",
            tint = if (saved) Color(0xFFE4B036)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            enabled = true,
            onClick = onClickSave,
        )
    }
}

@Composable
private fun PostActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

@Preview(showBackground = true)
@Composable
private fun FapFolderPostCardPreview() {
    ComponentPreview {
        val fakeAuthor = FapFolderAuthor(
            uid = 123L,
            username = "sample_user",
            groupSlug = null,
            displayName = "Sample User",
            profileUrl = "https://fapfolder.club/sample_user",
            avatarUrl = null,
            isGold = true,
            isAnonymous = false,
        )
        val fakePost = FapFolderPost(
            postId = 1L,
            slug = "test",
            permalink = "https://fapfolder.club/posts/test",
            author = fakeAuthor,
            uploaderUsername = "sample_user",
            uploaderUrl = "https://fapfolder.club/sample_user",
            uploaderIsGold = true,
            action = "added photo",
            postedAtIso = "2026-10-28 12:00:00",
            bodyHtml = "",
            bodyPlain = "This is a sample post for preview purposes.",
            hashtags = listOf("sample", "preview"),
            media = emptyList(),
            variants = setOf(PostVariant.TEXT),
            isPremium = false,
            reactions = com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount(12, null),
            commentCount = 3,
            shareCount = 0,
            viewCount = 152,
        )
        FapFolderPostCard(post = fakePost)
    }
}
