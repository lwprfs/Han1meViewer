package com.yenaly.han1meviewer.FapFolder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryRepo
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSearchHistoryRepo
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import kotlinx.coroutines.launch

private data class FapFolderHubEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderSettingsHubScreen(
    onBack: () -> Unit,
    onOpenCookies: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSearchHistory: () -> Unit = onOpenSearch,
    onOpenFollowedCommunities: () -> Unit = {},
    isLoggedIn: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val entries: List<FapFolderHubEntry> = buildList {
        add(
            FapFolderHubEntry(
                key = "cookies",
                title = "Cookies & Session",
                subtitle = if (isLoggedIn) {
                    "Signed in · manage cookies or sign out"
                } else {
                    "Not signed in · paste cookies to log in"
                },
                icon = Icons.Filled.Cookie,
                onClick = onOpenCookies,
            )
        )

        if (!isLoggedIn) {
            add(
                FapFolderHubEntry(
                    key = "login",
                    title = "Sign in",
                    subtitle = "Log in with username & password",
                    icon = Icons.Filled.Login,
                    onClick = onOpenLogin,
                )
            )
        }

        if (isLoggedIn) {
            add(
                FapFolderHubEntry(
                    key = "followed_communities",
                    title = "Followed Communities",
                    subtitle = "Communities you follow on FapFolder",
                    icon = Icons.Filled.Groups,
                    onClick = onOpenFollowedCommunities,
                )
            )
        }

        add(
            FapFolderHubEntry(
                key = "search_history",
                title = "Search History",
                subtitle = "Browse and re-run previous searches",
                icon = Icons.Filled.Search,
                onClick = onOpenSearchHistory,
            )
        )

        add(
            FapFolderHubEntry(
                key = "clear_search_history",
                title = "Clear Search History",
                subtitle = "Remove local search history",
                icon = Icons.Filled.Search,
                onClick = { showClearSearchDialog = true },
            )
        )

        add(
            FapFolderHubEntry(
                key = "watch_history",
                title = "Watch History",
                subtitle = "View and clear local post history",
                icon = Icons.Filled.History,
                onClick = onOpenHistory,
            )
        )

        add(
            FapFolderHubEntry(
                key = "clear_watch_history",
                title = "Clear Watch History",
                subtitle = "Remove every post in local watch history",
                icon = Icons.Filled.History,
                onClick = { showClearHistoryDialog = true },
            )
        )

        add(
            FapFolderHubEntry(
                key = "search",
                title = "Search",
                subtitle = "Open FapFolder search",
                icon = Icons.Filled.Search,
                onClick = onOpenSearch,
            )
        )

        add(
            FapFolderHubEntry(
                key = "about",
                title = "About FapFolder",
                subtitle = "Site info, disclaimers, and credits",
                icon = Icons.Filled.Info,
                onClick = { showAboutDialog = true },
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FapFolder Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "site_card") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "FapFolder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            text = "https://fapfolder.club",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            items(entries, key = { it.key }) { entry ->
                FapFolderHubRow(entry = entry)
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear watch history?") },
            text = {
                Text(
                    "Every post in your FapFolder watch history will be removed. " +
                            "This does not affect your FapFolder account."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    scope.launch {
                        runCatching { FapFolderHistoryRepo.deleteAll() }
                        GlobalToasts.show(
                            "Watch history cleared",
                            level = GlobalToasts.ToastLevel.SUCCESS,
                        )
                    }
                }) {
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearSearchDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchDialog = false },
            title = { Text("Clear search history?") },
            text = {
                Text(
                    "Every locally saved FapFolder search will be removed from this device."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearSearchDialog = false
                    FapFolderSearchHistoryRepo.clear()
                    GlobalToasts.show(
                        "Search history cleared",
                        level = GlobalToasts.ToastLevel.SUCCESS,
                    )
                }) {
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About FapFolder") },
            text = {
                Text(
                    "FapFolder (fapfolder.club) is an independent content aggregator " +
                            "focused on leaked social media content. This app is a read-only " +
                            "client: it renders the site's public pages, its own local watch " +
                            "history, and nothing else. No data is sent back to any server " +
                            "except what you explicitly post, and even that is disabled in " +
                            "this client.\n\n" +
                            "All content is uploaded by third parties. This app does not host " +
                            "any content itself."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun FapFolderHubRow(entry: FapFolderHubEntry) {
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
