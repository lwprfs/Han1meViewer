package com.yenaly.han1meviewer.RouVideo.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation.Kind
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteChipRow
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator

private fun kindIcon(kind: Kind): Int = when (kind) {
    Kind.TAG -> R.drawable.ic_settings_label_24
    Kind.TITLE -> R.drawable.ic_settings_movie_24
    Kind.DESC -> R.drawable.ic_settings_notes_24
    Kind.COMMENT -> R.drawable.ic_baseline_comment_24
}

@Composable
private fun SettingsSectionTitle(title: String) {
    com.yenaly.han1meviewer.ui.component.SettingSectionHeader(title)
}

@Composable
fun RouVideoTranslationSettingsScreen(
    onBack: () -> Unit,
    onOpenCache: () -> Unit,
) {
    val context = LocalContext.current
    val stats = RouVideoTranslation.stats()
    val paused = RouVideoTranslation.pausedForSeconds()
    val enabled = RouVideoTranslation.enabled
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.rouvideo_tr_title)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.rouvideo_tr_enable),
                    summary = stringResource(R.string.rouvideo_tr_privacy),
                    iconRes = R.drawable.ic_translate_24,
                    checked = enabled,
                    onCheckedChange = { RouVideoTranslation.enabled = it },
                )
            }

            item { SettingsSectionTitle(stringResource(R.string.rouvideo_tr_what)) }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.rouvideo_tr_tags),
                    summary = stringResource(R.string.rouvideo_tr_tags_desc),
                    iconRes = R.drawable.ic_settings_label_24,
                    checked = RouVideoTranslation.tagsEnabled,
                    enabled = enabled,
                    onCheckedChange = { RouVideoTranslation.tagsEnabled = it },
                )
            }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.rouvideo_tr_titles),
                    summary = stringResource(R.string.rouvideo_tr_titles_desc),
                    iconRes = R.drawable.ic_settings_movie_24,
                    checked = RouVideoTranslation.titlesEnabled,
                    enabled = enabled,
                    onCheckedChange = { RouVideoTranslation.titlesEnabled = it },
                )
            }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.rouvideo_tr_descriptions),
                    summary = stringResource(R.string.rouvideo_tr_descriptions_desc),
                    iconRes = R.drawable.ic_settings_notes_24,
                    checked = RouVideoTranslation.descriptionsEnabled,
                    enabled = enabled,
                    onCheckedChange = { RouVideoTranslation.descriptionsEnabled = it },
                )
            }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.rouvideo_tr_comments),
                    summary = stringResource(R.string.rouvideo_tr_comments_desc),
                    iconRes = R.drawable.ic_baseline_comment_24,
                    checked = RouVideoTranslation.commentsEnabled,
                    enabled = enabled,
                    onCheckedChange = { RouVideoTranslation.commentsEnabled = it },
                )
            }

            item { SettingsSectionTitle(stringResource(R.string.rouvideo_tr_sites)) }
            items(RouVideoTranslation.SUPPORTED_SITES, key = { "site_" + it.name }) { site ->
                SettingSwitchItem(
                    title = CrossSiteNavigator.siteLabel(site),
                    summary = stringResource(R.string.rouvideo_tr_sites_desc),
                    iconRes = R.drawable.ic_settings_public_24,
                    checked = RouVideoTranslation.siteEnabled(site),
                    enabled = enabled,
                    onCheckedChange = { RouVideoTranslation.setSiteEnabled(site, it) },
                )
            }

            item { SettingsSectionTitle(stringResource(R.string.rouvideo_tr_cache)) }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.rouvideo_tr_view),
                    summary = stringResource(
                        R.string.rouvideo_tr_cache_breakdown,
                        stats.tags, stats.titles, stats.descriptions, stats.comments,
                    ),
                    valueText = stringResource(
                        R.string.rouvideo_tr_cache_usage,
                        Formatter.formatShortFileSize(context, stats.bytes),
                        stats.total,
                    ),
                    iconRes = R.drawable.ic_settings_list_24,
                    onClick = onOpenCache,
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.rouvideo_tr_clear),
                    iconRes = R.drawable.ic_baseline_delete_24,
                    enabled = stats.total > 0,
                    onClick = { confirmClear = true },
                )
            }
            if (paused > 0) {
                item {
                    Text(
                        text = stringResource(
                            R.string.rouvideo_tr_paused,
                            ((paused + 59) / 60).toInt().coerceAtLeast(1),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.rouvideo_tr_clear_confirm, stats.total)) },
            confirmButton = {
                TextButton(onClick = {
                    RouVideoTranslation.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun RouVideoTranslationCacheScreen(onBack: () -> Unit) {
    var filterName by rememberSaveable { mutableStateOf("") }
    val filter = Kind.entries.firstOrNull { it.name == filterName }
    val items = RouVideoTranslation.snapshot(filter)
    val options = listOf<Kind?>(null) + Kind.entries
    val labels = mapOf<Kind?, String>(
        null to stringResource(R.string.rouvideo_tr_filter_all),
        Kind.TAG to stringResource(R.string.rouvideo_tr_tags),
        Kind.TITLE to stringResource(R.string.rouvideo_tr_titles),
        Kind.DESC to stringResource(R.string.rouvideo_tr_descriptions),
        Kind.COMMENT to stringResource(R.string.rouvideo_tr_comments),
    )

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.rouvideo_tr_view)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SiteChipRow(
                options = options,
                selected = filter,
                label = { labels.getValue(it) },
                onSelect = { filterName = it?.name.orEmpty() },
            )
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.rouvideo_tr_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.kind.key + "\u0001" + it.source }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(kindIcon(entry.kind)),
                                contentDescription = labels.getValue(entry.kind),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.text,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = entry.source,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { RouVideoTranslation.delete(entry) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
