package com.yenaly.han1meviewer.RouVideo.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation.Kind
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteChipRow
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

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
        ) {
            item {
                Text(
                    text = stringResource(R.string.rouvideo_tr_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                TrSwitchRow(
                    title = stringResource(R.string.rouvideo_tr_enable),
                    subtitle = stringResource(R.string.rouvideo_tr_enable_desc),
                    checked = enabled,
                    onChange = { RouVideoTranslation.enabled = it },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                TrSwitchRow(
                    title = stringResource(R.string.rouvideo_tr_tags),
                    subtitle = stringResource(R.string.rouvideo_tr_tags_desc),
                    checked = RouVideoTranslation.tagsEnabled,
                    enabled = enabled,
                    onChange = { RouVideoTranslation.tagsEnabled = it },
                )
            }
            item {
                TrSwitchRow(
                    title = stringResource(R.string.rouvideo_tr_titles),
                    subtitle = stringResource(R.string.rouvideo_tr_titles_desc),
                    checked = RouVideoTranslation.titlesEnabled,
                    enabled = enabled,
                    onChange = { RouVideoTranslation.titlesEnabled = it },
                )
            }
            item {
                TrSwitchRow(
                    title = stringResource(R.string.rouvideo_tr_descriptions),
                    subtitle = stringResource(R.string.rouvideo_tr_descriptions_desc),
                    checked = RouVideoTranslation.descriptionsEnabled,
                    enabled = enabled,
                    onChange = { RouVideoTranslation.descriptionsEnabled = it },
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

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                Text(
                    text = stringResource(R.string.rouvideo_tr_cache),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.rouvideo_tr_cache_usage,
                            Formatter.formatShortFileSize(context, stats.bytes),
                            stats.total,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.rouvideo_tr_cache_breakdown,
                            stats.tags, stats.titles, stats.descriptions,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onOpenCache, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.rouvideo_tr_view))
                    }
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = stats.total > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.rouvideo_tr_clear))
                    }
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
                                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                Text(
                                    text = labels.getValue(entry.kind),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
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

@Composable
private fun TrSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
