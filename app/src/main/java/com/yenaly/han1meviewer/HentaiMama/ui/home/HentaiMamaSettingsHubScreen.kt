package com.yenaly.han1meviewer.HentaiMama.ui.home

import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteIconBadge
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.List

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
    onNavigateToPlaylists: () -> Unit,
) {
    val entries = listOf(
        HubEntry(
            key = "video",
            title = stringResource(R.string.video),
            subtitle = stringResource(R.string.hm_preferred_server_quality_resume_playba),
            icon = Icons.Default.PlayCircleOutline,
            onClick = onNavigateToVideoSettings,
        ),
        HubEntry(
            key = "home_categories",
            title = stringResource(R.string.drawer_home_categories),
            subtitle = stringResource(R.string.site_home_categories_subtitle),
            icon = Icons.Default.Category,
            onClick = onNavigateToHomeCategories,
        ),
        HubEntry(
            key = "history",
            title = stringResource(R.string.watch_history),
            subtitle = stringResource(R.string.hm_history_subtitle),
            icon = Icons.Default.History,
            onClick = onNavigateToHistory,
        ),
        HubEntry(
            key = "playlists",
            title = stringResource(R.string.hm_playlists_title),
            subtitle = stringResource(R.string.hm_playlists_subtitle),
            icon = Icons.AutoMirrored.Filled.List,
            onClick = onNavigateToPlaylists,
        ),
        HubEntry(
            key = "recent_episodes",
            title = "Recent Episodes",
            subtitle = stringResource(R.string.hm_recent_episodes_subtitle),
            icon = Icons.Default.VideoLibrary,
            onClick = onNavigateToRecentEpisodes,
        ),
        HubEntry(
            key = "upcoming",
            title = stringResource(R.string.drawer_upcoming),
            subtitle = stringResource(R.string.hm_upcoming_subtitle),
            icon = Icons.Default.CalendarMonth,
            onClick = onNavigateToUpcoming,
        ),
    )

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("HentaiMama Settings") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
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
            SiteIconBadge(imageVector = entry.icon)
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
