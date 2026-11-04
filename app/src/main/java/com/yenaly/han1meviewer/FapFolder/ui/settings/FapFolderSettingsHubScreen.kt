package com.yenaly.han1meviewer.FapFolder.ui.settings

import com.yenaly.han1meviewer.ui.component.SiteIconBadge
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Login
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
import com.yenaly.yenaly_libs.utils.applicationContext
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
    onOpenSaved: () -> Unit = {},
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
                title = stringResource(R.string.fap_cookies_session),
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
                    title = stringResource(R.string.site_sign_in),
                    subtitle = stringResource(R.string.fap_log_in_with_username_password),
                    icon = Icons.AutoMirrored.Filled.Login,
                    onClick = onOpenLogin,
                )
            )
        }

        if (isLoggedIn) {
            add(
                FapFolderHubEntry(
                    key = "followed_communities",
                    title = stringResource(R.string.drawer_followed_communities),
                    subtitle = stringResource(R.string.fap_communities_you_follow_on_fapfolder),
                    icon = Icons.Filled.Groups,
                    onClick = onOpenFollowedCommunities,
                )
            )

            add(
                FapFolderHubEntry(
                    key = "saved_posts",
                    title = stringResource(R.string.fap_saved_posts_title),
                    subtitle = stringResource(R.string.fap_posts_you_ve_saved_on_fapfolder),
                    icon = Icons.Filled.Bookmark,
                    onClick = onOpenSaved,
                )
            )
        }

        add(
            FapFolderHubEntry(
                key = "search_history",
                title = "Search History",
                subtitle = stringResource(R.string.fap_browse_and_re_run_previous_searches),
                icon = Icons.Filled.Search,
                onClick = onOpenSearchHistory,
            )
        )

        add(
            FapFolderHubEntry(
                key = "clear_search_history",
                title = stringResource(R.string.fap_clear_search_history_2),
                subtitle = stringResource(R.string.fap_remove_local_search_history),
                icon = Icons.Filled.Search,
                onClick = { showClearSearchDialog = true },
            )
        )

        add(
            FapFolderHubEntry(
                key = "watch_history",
                title = stringResource(R.string.watch_history),
                subtitle = stringResource(R.string.fap_view_and_clear_local_post_history),
                icon = Icons.Filled.History,
                onClick = onOpenHistory,
            )
        )

        add(
            FapFolderHubEntry(
                key = "clear_watch_history",
                title = stringResource(R.string.fap_clear_watch_history_2),
                subtitle = stringResource(R.string.fap_remove_every_post_in_local_watch),
                icon = Icons.Filled.History,
                onClick = { showClearHistoryDialog = true },
            )
        )

        add(
            FapFolderHubEntry(
                key = "search",
                title = stringResource(R.string.search),
                subtitle = stringResource(R.string.fap_open_fapfolder_search),
                icon = Icons.Filled.Search,
                onClick = onOpenSearch,
            )
        )

        add(
            FapFolderHubEntry(
                key = "about",
                title = stringResource(R.string.fap_about_fapfolder),
                subtitle = stringResource(R.string.fap_site_info_disclaimers_and_credits),
                icon = Icons.Filled.Info,
                onClick = { showAboutDialog = true },
            )
        )
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.fap_fapfolder_settings)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
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
            title = { Text(stringResource(R.string.fap_clear_watch_history)) },
            text = {
                Text(
                    stringResource(R.string.fap_every_post_in_your_fapfolder_watch)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    scope.launch {
                        runCatching { FapFolderHistoryRepo.deleteAll() }
                        GlobalToasts.show(
                            applicationContext.getString(R.string.fap_watch_history_cleared),
                            level = GlobalToasts.ToastLevel.SUCCESS,
                        )
                    }
                }) {
                    Text(
                        text = stringResource(R.string.site_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearSearchDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchDialog = false },
            title = { Text(stringResource(R.string.fap_clear_search_history)) },
            text = {
                Text(
                    stringResource(R.string.fap_every_locally_saved_fapfolder_search_w_2)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearSearchDialog = false
                    FapFolderSearchHistoryRepo.clear()
                    GlobalToasts.show(
                        applicationContext.getString(R.string.fap_search_history_cleared),
                        level = GlobalToasts.ToastLevel.SUCCESS,
                    )
                }) {
                    Text(
                        text = stringResource(R.string.site_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.fap_about_fapfolder)) },
            text = {
                Text(
                    stringResource(R.string.fap_fapfolder_fapfolder_club_is_an_indepen)
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.close))
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
