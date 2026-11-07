package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.VideoItemType
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.preview.fakeVideosItem
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.util.DisplayTextLocalizer

@Composable
fun VideoCardItem(
    modifier: Modifier = Modifier,
    videoItem: VideoItemType,
    isHorizontalCard: Boolean = true,
    isWatched: Boolean = false,
    isPlaying: Boolean = false,
    onClickVideosItem: (String) -> Unit,
    onLongClickVideosItem: (String, String) -> Unit,
) {
    val iconSize = dimensionResource(id = R.dimen.view_view_and_time_icon_size)
    val imageAspectRatio = if (isHorizontalCard) 16f / 9f else 3f / 4f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isPlaying,
                onClick = { onClickVideosItem(videoItem.videoCode) },
                onLongClick = { onLongClickVideosItem(videoItem.videoCode, videoItem.title) },
            ),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio),
            ) {
                RetryableImage(
                    model = videoItem.coverUrl,
                    contentDescription = videoItem.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.FillWidth,
                )

                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.played),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceVariant
                                ),
                            ),
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    videoItem.views?.let {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_play_circle_outline_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                        Text(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            text = DisplayTextLocalizer.localizeViews(it),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    videoItem.duration?.let {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_access_time_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                        Text(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            text = it,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_play_circle_outline_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.now_playing),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Text(
                text = videoItem.title,
                maxLines = 2,
                minLines = 2,
                style = MaterialTheme.typography.titleSmall,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = videoItem.currentArtist ?: stringResource(R.string.video_card_unknown_artist),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
            ) {
                videoItem.reviews?.takeIf { it.isNotEmpty() }?.let { reviewsText ->
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_thumb_up_alt_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = reviewsText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!videoItem.uploadTime.isNullOrEmpty()) {
                    Text(
                        text = DisplayTextLocalizer.localizeRelativeTime(videoItem.uploadTime!!),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoCardItemPreview() {
    ComponentPreview {
        VideoCardItem(
            videoItem = fakeVideosItem,
            onClickVideosItem = {},
            onLongClickVideosItem = { _, _ -> },
            isWatched = true,
            isPlaying = true
        )
    }
}
