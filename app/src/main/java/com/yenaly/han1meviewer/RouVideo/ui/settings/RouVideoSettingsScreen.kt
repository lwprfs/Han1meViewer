package com.yenaly.han1meviewer.RouVideo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.RouVideo.common.RouVideoSettings
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoNetwork
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

@Composable
fun RouVideoSettingsScreen(onBack: () -> Unit) {
    var order by remember { mutableStateOf(RouVideoSettings.tabOrder) }
    var hidden by remember { mutableStateOf(RouVideoSettings.hiddenTabs) }
    var language by remember { mutableStateOf(RouVideoSettings.titleLanguage) }
    var reportPlays by remember { mutableStateOf(RouVideoSettings.reportPlays) }
    var cookiesCleared by remember { mutableStateOf(false) }
    val tabs = remember { RouVideoConstants.DEFAULT_TABS.associateBy { it.key } }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.site_settings_2, "rou.video")) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { SectionHeader(stringResource(R.string.rouvideo_home_tabs)) }
            item { HintText(stringResource(R.string.rouvideo_home_tabs_hint)) }
            order.forEachIndexed { index, key ->
                val tab = tabs[key] ?: return@forEachIndexed
                item(key = "tab_$key") {
                    TabRow(
                        title = tab.title,
                        enabled = key !in hidden,
                        canMoveUp = index > 0,
                        canMoveDown = index < order.lastIndex,
                        onToggle = { on ->
                            hidden = if (on) hidden - key else hidden + key
                            RouVideoSettings.hiddenTabs = hidden
                        },
                        onMoveUp = {
                            order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                            RouVideoSettings.tabOrder = order
                        },
                        onMoveDown = {
                            order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                            RouVideoSettings.tabOrder = order
                        },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.rouvideo_title_language)) }
            item { HintText(stringResource(R.string.rouvideo_title_language_hint)) }
            item {
                RadioRow(
                    title = stringResource(R.string.rouvideo_lang_traditional),
                    selected = language == RouVideoSettings.LANG_TRADITIONAL,
                    onClick = {
                        language = RouVideoSettings.LANG_TRADITIONAL
                        RouVideoSettings.titleLanguage = language
                    },
                )
            }
            item {
                RadioRow(
                    title = stringResource(R.string.rouvideo_lang_simplified),
                    selected = language == RouVideoSettings.LANG_SIMPLIFIED,
                    onClick = {
                        language = RouVideoSettings.LANG_SIMPLIFIED
                        RouVideoSettings.titleLanguage = language
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.rouvideo_playback)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.rouvideo_report_plays),
                    subtitle = stringResource(R.string.rouvideo_report_plays_summary),
                    checked = reportPlays,
                    onChange = {
                        reportPlays = it
                        RouVideoSettings.reportPlays = it
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.rouvideo_troubleshooting)) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            RouVideoNetwork.clearCookies()
                            cookiesCleared = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (cookiesCleared) stringResource(R.string.rouvideo_cf_cleared)
                            else stringResource(R.string.rouvideo_cf_clear)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            RouVideoSettings.resetToDefaults()
                            order = RouVideoSettings.tabOrder
                            hidden = RouVideoSettings.hiddenTabs
                            language = RouVideoSettings.titleLanguage
                            reportPlays = RouVideoSettings.reportPlays
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.rouvideo_reset_defaults))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TabRow(
    title: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = null)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
