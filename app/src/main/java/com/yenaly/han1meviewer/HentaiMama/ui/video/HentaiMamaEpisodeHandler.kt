package com.yenaly.han1meviewer.HentaiMama.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeInfo

/**
 * Title and meta block, styled like Hanime's `TitleSection` and `MetaSection`.
 * The synopsis is shown once, by [HentaiMamaEpisodeHeader].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HentaiMamaEpisodeHandler(
    info: EpisodeInfo,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SelectionContainer {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        val views: Long? = info.views
        val studio: String? = info.studio
        val airedOn: String? = info.airedOn
        val addedOn: String? = info.addedOn

        if (views != null || studio != null || airedOn != null || addedOn != null) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                views?.let { MetaInfoItem(icon = Icons.Rounded.PlayArrow, iconSize = 16, text = it.toString()) }
                studio?.let { MetaInfoItem(icon = null, text = it) }
                airedOn?.let { MetaInfoItem(icon = Icons.Rounded.Schedule, text = "Aired $it") }
                addedOn?.let { MetaInfoItem(icon = Icons.Rounded.Schedule, text = "Added $it") }
            }
        }
    }
}

/** Same row as Hanime's `MetaInfoItem`: 32dp high, small icon plus bodyMedium text. */
@Composable
private fun MetaInfoItem(
    icon: ImageVector?,
    text: String,
    iconSize: Int = 14,
) {
    Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
