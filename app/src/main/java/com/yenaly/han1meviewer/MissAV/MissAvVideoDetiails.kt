package com.yenaly.han1meviewer.MissAV

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.regex.Pattern

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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!info.title.isNullOrBlank()) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!info.description.isNullOrBlank()) {
            var cleanDescription = info.description
            val releaseDatePattern = Pattern.compile("Release date:\\s*\\d{4}-\\d{2}-\\d{2}\\s*", Pattern.CASE_INSENSITIVE)
            cleanDescription = releaseDatePattern.matcher(cleanDescription).replaceAll("")
            val codePattern = Pattern.compile("Code:\\s*\\S+\\s*", Pattern.CASE_INSENSITIVE)
            cleanDescription = codePattern.matcher(cleanDescription).replaceAll("")
            cleanDescription = cleanDescription.trim()

            if (cleanDescription.isNotBlank()) {
                Text(
                    text = cleanDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (releaseDate != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Release date:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = releaseDate,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (code.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Code:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                val (bgColor, textColor) = when (extra?.uppercase()) {
                    "UNCENSORED-LEAK" -> Pair(Color.Red, Color.Black)
                    "ENGLISH-SUBTITLE" -> Pair(Color.Blue, Color.Black)
                    "CHINESE-SUBTITLE" -> Pair(Color(0xFF2196F3), Color.Black)
                    else -> Pair(Color.Green, Color.Black)
                }
                Surface(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onNavigateToSearch(lowercaseCode) },
                            onLongClick = {
                                MissAvVideoUtils.copyToClipboard(context, "Code", lowercaseCode)
                            }
                        ),
                    shape = RoundedCornerShape(4.dp),
                    color = bgColor,
                    contentColor = textColor
                ) {
                    Text(
                        text = lowercaseCode,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (extra != null) {
                    Surface(
                        modifier = Modifier.clickable { onNavigateToSearch(extra.lowercase()) },
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = extra.lowercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        val infoItems = listOf(
            "Directors" to info.directors,
            "Label" to info.label,
            "Series" to info.series,
            "Makers" to info.makers
        ).filter { it.second.isNullOrBlank().not() }

        if (infoItems.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                infoItems.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$label:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val (bgColor, textColor) = when (label) {
                            "Series" -> Pair(Color(0xFF4A148C), Color.White)
                            "Actresses" -> Pair(Color(0xFF00695C), Color.White)
                            else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val modifier = if (label == "Series") {
                            Modifier.combinedClickable(
                                onClick = { onNavigateToSearch(value?.lowercase() ?: "") },
                                onLongClick = {
                                    MissAvVideoUtils.copyToClipboard(context, label, value?.lowercase() ?: "")
                                }
                            )
                        } else {
                            Modifier.clickable { onNavigateToSearch(value?.lowercase() ?: "") }
                        }
                        Surface(
                            modifier = modifier,
                            shape = RoundedCornerShape(4.dp),
                            color = bgColor,
                            contentColor = textColor
                        ) {
                            Text(
                                text = value?.lowercase() ?: "",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (!info.genres.isNullOrBlank()) {
            val genreList = info.genres.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (genreList.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Genres",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        genreList.forEach { genre ->
                            Surface(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = { onNavigateToSearch(genre) },
                                        onLongClick = {
                                            MissAvVideoUtils.copyToClipboard(context, "Genre", genre)
                                        }
                                    ),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = genre,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        MissAvSubtitleSection(
            videoCode = lowercaseCode,
            onSubtitleDownloaded = onSubtitleDownloaded
        )

        if (!info.jpTitle.isNullOrBlank()) {
            Text(
                text = "Title: ${info.jpTitle}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}