package com.yenaly.han1meviewer.HentaiMama.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HubEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaSettingsHubScreen(
    onBack: () -> Unit,
    onNavigateToVideoSettings: () -> Unit,
    onNavigateToHomeCategories: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToUpcoming: () -> Unit,
    onNavigateToRecentEpisodes: () -> Unit,
) {
    val entries = listOf(
        HubEntry(
            key = "video",
            title = "Video",
            subtitle = "Preferred server & quality, resume playback",
            icon = Icons.Default.PlayCircleOutline,
            onClick = onNavigateToVideoSettings,
        ),
        HubEntry(
            key = "home_categories",
            title = "Home Categories",
            subtitle = "Reorder, hide, edit, add or delete home sections",
            icon = Icons.Default.Category,
            onClick = onNavigateToHomeCategories,
        ),
        HubEntry(
            key = "history",
            title = "Watch History",
            subtitle = "Series you've watched, resume from where you left off",
            icon = Icons.Default.History,
            onClick = onNavigateToHistory,
        ),
        HubEntry(
            key = "recent_episodes",
            title = "Recent Episodes",
            subtitle = "Newest episode releases across all series",
            icon = Icons.Default.VideoLibrary,
            onClick = onNavigateToRecentEpisodes,
        ),
        HubEntry(
            key = "upcoming",
            title = "Upcoming",
            subtitle = "Browse upcoming episodes by month",
            icon = Icons.Default.CalendarMonth,
            onClick = onNavigateToUpcoming,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HentaiMama Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(entries, key = { it.key }) { entry ->
                HubRow(entry = entry)
            }
        }
    }
}

@Composable
private fun HubRow(entry: HubEntry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = entry.onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
