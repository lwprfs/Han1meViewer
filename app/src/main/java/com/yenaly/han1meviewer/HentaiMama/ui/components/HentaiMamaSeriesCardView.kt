package com.yenaly.han1meviewer.HentaiMama.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaImageLoader
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@Composable
fun HentaiMamaSeriesCardView(
    card: SeriesCard,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
    onLongClick: (String, String) -> Unit = { _, _ -> },
) {
    val aspectRatio by HentaiMamaCardSettings.effectiveAspectRatioState
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    SiteVideoCard(
        modifier = modifier,
        title = card.title,
        coverUrl = card.thumbFull.ifBlank { card.thumbSmall.orEmpty() },
        aspectRatio = aspectRatio,
        views = card.views?.takeIf { it > 0L }?.let { formatCompactNumber(it) },
        duration = card.episodeCount?.takeIf { it > 0 }?.let { "$it EP" },
        subtitle = card.studios.take(2).joinToString(", ").ifBlank { null },
        reviews = card.rating?.let { "★ ${formatRatingValue(it)}" }
            ?: card.favorites?.takeIf { it > 0 }?.let { "♥ ${formatCompactNumber(it.toLong())}" },
        uploadTime = card.year?.toString(),
        imageLoader = imageLoader,
        onClick = { onClick(card.slug) },
        onLongClick = { onLongClick(card.slug, card.title) },
    )
}

private fun formatRatingValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatCompactNumber(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1_000_000_000.0)
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}
