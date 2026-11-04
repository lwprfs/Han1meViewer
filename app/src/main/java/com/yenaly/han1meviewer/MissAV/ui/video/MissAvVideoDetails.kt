package com.yenaly.han1meviewer.MissAV.ui.video

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.MissAV.data.model.MissAvVideoInfo
import com.yenaly.han1meviewer.MissAV.data.remote.MissAvVideoUtils
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.component.ExpandableRichText
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.han1meviewer.ui.theme.VideoNormalCardMinWidth
import com.yenaly.yenaly_libs.utils.applicationContext
import java.util.regex.Pattern

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissAvVideoDetails(
    info: MissAvVideoInfo,
    onNavigateToSearch: (String?) -> Unit,
    onSubtitleDownloaded: (Uri) -> Unit,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val releaseDate = MissAvVideoUtils.extractReleaseDate(info.description)
    val (code, extra) = MissAvVideoUtils.parseCodeAndExtra(info.videoCode)
    val lowercaseCode = code.lowercase()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        if (!info.title.isNullOrBlank() || !info.jpTitle.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!info.title.isNullOrBlank()) {
                    SelectionContainer {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (!info.jpTitle.isNullOrBlank()) {
                    SelectionContainer {
                        Text(
                            text = info.jpTitle.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (code.isNotBlank() || releaseDate != null) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (code.isNotBlank()) {
                    MissAvTagChip(
                        text = lowercaseCode.uppercase(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { onNavigateToSearch(lowercaseCode) },
                        onLongClick = {
                            MissAvVideoUtils.copyToClipboard(context, "Code", lowercaseCode)
                        },
                    )
                    if (extra != null) {
                        val isLeak = extra.uppercase() == "UNCENSORED-LEAK"
                        MissAvTagChip(
                            text = extra.lowercase(),
                            containerColor = if (isLeak) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (isLeak) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            onClick = { onNavigateToSearch(extra.lowercase()) },
                        )
                    }
                }
                if (releaseDate != null) {
                    MissAvMetaInfoItem(text = releaseDate)
                }
            }
        }

        if (!info.description.isNullOrBlank()) {
            var cleanDescription = info.description
            val releaseDatePattern = Pattern.compile("Release date:\\s*\\d{4}-\\d{2}-\\d{2}\\s*", Pattern.CASE_INSENSITIVE)
            cleanDescription = releaseDatePattern.matcher(cleanDescription).replaceAll("")
            val codePattern = Pattern.compile("Code:\\s*\\S+\\s*", Pattern.CASE_INSENSITIVE)
            cleanDescription = codePattern.matcher(cleanDescription).replaceAll("")
            cleanDescription = cleanDescription.trim()

            if (cleanDescription.isNotBlank()) {
                ExpandableRichText(
                    text = cleanDescription,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val infoItems = listOf(
            "Directors" to info.directors,
            "Label" to info.label,
            stringResource(R.string.hm_series) to info.series,
            "Makers" to info.makers
        ).filter { it.second.isNullOrBlank().not() }

        if (infoItems.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                ) {
                    infoItems.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.widthIn(min = 72.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val isSeries = label == "Series"
                            MissAvTagChip(
                                text = value?.lowercase() ?: "",
                                containerColor = if (isSeries) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                contentColor = if (isSeries) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                onClick = { onNavigateToSearch(value?.lowercase() ?: "") },
                                onLongClick = if (isSeries) {
                                    {
                                        MissAvVideoUtils.copyToClipboard(
                                            context,
                                            label,
                                            value?.lowercase() ?: ""
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
            }
        }

        if (!info.genres.isNullOrBlank()) {
            val genreList = info.genres.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (genreList.isNotEmpty()) {
                val palette = missAvTagPalette()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genreList.forEachIndexed { index, genre ->
                        val (container, content) = palette[index % palette.size]
                        MissAvTagChip(
                            text = genre,
                            containerColor = container,
                            contentColor = content,
                            onClick = { onNavigateToSearch(genre) },
                            onLongClick = {
                                MissAvVideoUtils.copyToClipboard(context, applicationContext.getString(R.string.site_genre), genre)
                            },
                        )
                    }
                }
            }
        }

        MissAvSubtitleSection(
            videoCode = lowercaseCode,
            onSubtitleDownloaded = onSubtitleDownloaded
        )
    }
}

@Composable
private fun MissAvMetaInfoItem(text: String) {
    Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MissAvTagChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun missAvTagPalette(): List<Pair<Color, Color>> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.surfaceContainerHigh to scheme.onSurface,
            scheme.surfaceContainerHighest to scheme.onSurface,
        )
    }
}
