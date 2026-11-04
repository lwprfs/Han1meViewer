package com.yenaly.han1meviewer.HentaiMama.ui.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaImageLoader
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.VideoItemType
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import com.yenaly.han1meviewer.util.DisplayTextLocalizer
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@Composable
fun HentaiMamaVideoCard(
    videoItem: VideoItemType,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
    onLongClick: (String, String) -> Unit,
) {
    val aspectRatio by HentaiMamaCardSettings.effectiveAspectRatioState
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    SiteVideoCard(
        modifier = modifier,
        title = videoItem.title,
        coverUrl = videoItem.coverUrl,
        aspectRatio = aspectRatio,
        views = videoItem.views?.let { DisplayTextLocalizer.localizeViews(it) },
        duration = videoItem.duration,
        subtitle = videoItem.currentArtist,
        reviews = videoItem.reviews,
        uploadTime = videoItem.uploadTime?.let { DisplayTextLocalizer.localizeRelativeTime(it) },
        imageLoader = imageLoader,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        onClick = { onClick(videoItem.videoCode) },
        onLongClick = { onLongClick(videoItem.videoCode, videoItem.title) },
    )
}

@Composable
fun HentaiMamaGenreSeriesCard(
    series: GenreSeries,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
    onLongClick: (String, String) -> Unit = { _, _ -> },
    onGenreClick: (String) -> Unit = {},
    onStudioClick: (String) -> Unit = {},
) {
    val aspectRatio by HentaiMamaCardSettings.effectiveAspectRatioState
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    SiteVideoCard(
        modifier = modifier,
        title = series.displayTitle,
        coverUrl = (series.posterMid ?: series.posterFull).orEmpty(),
        aspectRatio = aspectRatio,
        views = series.views?.takeIf { it > 0L }?.let { formatViews(it) },
        duration = series.episodeCount?.takeIf { it > 0 }?.let { "$it EP" },
        subtitle = series.studios.take(2).joinToString(", ").ifBlank { null },
        reviews = series.rating?.let { "★ ${formatRating(it)}" }
            ?: series.favorites?.takeIf { it > 0 }?.let { "♥ $it" },
        uploadTime = series.year?.toString(),
        imageLoader = imageLoader,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        onClick = { onClick(series.slug) },
        onLongClick = { onLongClick(series.slug, series.displayTitle) },
    )
}

@Composable
fun HentaiMamaGenreSeriesRow(
    series: GenreSeries,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
    onGenreClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(series.slug) },
                onLongClick = {},
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                RetryableImage(
                    model = series.posterMid ?: series.posterFull,
                    contentDescription = series.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    imageLoader = imageLoader,
                )

                series.rating?.let { rating ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = formatRating(rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = 4.dp,
                                vertical = 1.dp,
                            ),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = series.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                series.altTitle
                    ?.takeIf { it.isNotBlank() && it != series.title }
                    ?.let { alt ->
                        Text(
                            text = alt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    series.year?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    series.views?.let {
                        Text(
                            text = formatViews(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    series.episodeCount?.let {
                        Text(
                            text = stringResource(R.string.hm_eps, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                series.favorites?.takeIf { it > 0 }?.let { favs ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "$favs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                series.synopsis
                    ?.takeIf { it.isNotBlank() }
                    ?.let { synopsis ->
                        Text(
                            text = synopsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                if (series.studios.isNotEmpty()) {
                    Text(
                        text = series.studios.take(3).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (series.genres.isNotEmpty()) {
                    Text(
                        text = series.genres.take(4).joinToString(" · "),
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

private fun formatRating(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatViews(views: Long): String = when {
    views >= 1_000_000_000 -> "%.1fB".format(views / 1_000_000_000.0)
    views >= 1_000_000 -> "%.1fM".format(views / 1_000_000.0)
    views >= 1_000 -> "%.1fK".format(views / 1_000.0)
    else -> views.toString()
}
