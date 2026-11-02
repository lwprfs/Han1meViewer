package com.yenaly.han1meviewer.HentaiMama.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaImageLoader
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeInfo
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.ExpandableRichText
import com.yenaly.han1meviewer.ui.component.TagChipGroup
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@Composable
fun HentaiMamaEpisodeHeader(
    info: EpisodeInfo,
    onSeriesClick: () -> Unit,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        val studioName: String = info.studio?.takeIf { it.isNotBlank() } ?: "Series"
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = info.seriesUrl != null, onClick = onSeriesClick)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RetryableImage(
                    model = info.seriesPoster,
                    contentDescription = info.title,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    imageLoader = imageLoader,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = studioName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val rating: Double? = info.rating
                    if (rating != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            val votes: Int? = info.voteCount
                            Text(
                                text = if (votes != null && votes > 0) {
                                    "${formatRating(rating)} ($votes votes)"
                                } else {
                                    formatRating(rating)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val fullSynopsis: String = buildString {
            val short: String? = info.synopsisShort
            if (!short.isNullOrBlank()) append(short)
            val long: String? = info.synopsisLong
            if (!long.isNullOrBlank()) {
                if (isNotEmpty()) append(" ")
                append(long)
            }
        }.trim()
        if (fullSynopsis.isNotEmpty()) {
            ExpandableRichText(
                text = fullSynopsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (info.genres.isNotEmpty()) {
            val genreNames = remember(info.genres) { info.genres.map { it.name } }
            TagChipGroup(
                tags = genreNames,
                collapsible = true,
                collapsedMaxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                onTagClick = onGenreClick,
            )
        }
    }
}

private fun formatRating(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
