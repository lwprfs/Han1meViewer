package com.yenaly.han1meviewer.SupJav.ui.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvSubtitleSettingsRepo
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
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.SupJav.common.SupJavSettings
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupJavSettingsScreen(onBack: () -> Unit) {
    var quality by remember { mutableStateOf(SupJavSettings.preferredQuality) }
    var order by remember { mutableStateOf(SupJavSettings.serverOrder) }
    var disabled by remember { mutableStateOf(SupJavSettings.disabledServers) }
    var fallback by remember { mutableStateOf(SupJavSettings.autoFallback) }
    var language by remember { mutableStateOf(SupJavSettings.language) }
    var cookiesCleared by remember { mutableStateOf(false) }
    var subSource by remember { mutableStateOf(SupJavSettings.subtitleSource) }
    var subLanguage by remember { mutableStateOf(SupJavSettings.subtitleLanguage) }
    var subTranslate by remember { mutableStateOf(SupJavSettings.subtitleTranslate) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Subtitle language") },
            text = {
                LazyColumn {
                    items(MissAvSubtitleSettingsRepo.subtitleCatLanguages, key = { it.code }) { lang ->
                        RadioRow(
                            title = lang.label,
                            selected = subLanguage == lang.code,
                            onClick = {
                                subLanguage = lang.code
                                SupJavSettings.subtitleLanguage = lang.code
                                showLanguageDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Close") }
            },
        )
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("SupJav settings") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { SectionHeader("Preferred quality") }
            item {
                HintText(
                    "The stream closest to this resolution (never above it if possible) starts " +
                            "first. You can still switch quality from the player."
                )
            }
            SupJavConstants.QUALITIES.forEach { option ->
                item(key = "quality_$option") {
                    RadioRow(
                        title = option,
                        selected = quality == option,
                        onClick = {
                            quality = option
                            SupJavSettings.preferredQuality = option
                        },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Servers") }
            item {
                HintText(
                    "Servers are tried from top to bottom. Turn a server off to skip it " +
                            "automatically; you can still pick it by hand on the video page."
                )
            }
            order.forEachIndexed { index, name ->
                val info = SupJavConstants.SERVERS.firstOrNull { it.name == name }
                item(key = "server_$name") {
                    ServerRow(
                        title = info?.title ?: name,
                        description = info?.description.orEmpty(),
                        enabled = name !in disabled,
                        canMoveUp = index > 0,
                        canMoveDown = index < order.lastIndex,
                        onToggle = { on ->
                            disabled = if (on) disabled - name else disabled + name
                            SupJavSettings.disabledServers = disabled
                        },
                        onMoveUp = {
                            order = order.toMutableList().apply {
                                add(index - 1, removeAt(index))
                            }
                            SupJavSettings.serverOrder = order
                        },
                        onMoveDown = {
                            order = order.toMutableList().apply {
                                add(index + 1, removeAt(index))
                            }
                            SupJavSettings.serverOrder = order
                        },
                    )
                }
            }
            item {
                SwitchRow(
                    title = "Try the next server automatically",
                    subtitle = "If a server cannot be played, move on to the next enabled one",
                    checked = fallback,
                    onChange = {
                        fallback = it
                        SupJavSettings.autoFallback = it
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Site language") }
            item {
                HintText("Language of the SupJav listings and search results.")
            }
            SupJavConstants.LANGUAGES.forEach { (code, title) ->
                item(key = "lang_$code") {
                    RadioRow(
                        title = title,
                        selected = language == code,
                        onClick = {
                            language = code
                            SupJavSettings.language = code
                        },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Subtitles") }
            item {
                HintText(
                    "Subtitles are searched by the video's JAV code from the video page and shown " +
                            "over the player. Pick where they come from and in which language."
                )
            }
            MissAvSubtitleSettingsRepo.sources.forEach { source ->
                item(key = "subsource_${source.value}") {
                    Column {
                        RadioRow(
                            title = source.label,
                            selected = subSource == source.value,
                            onClick = {
                                subSource = source.value
                                SupJavSettings.subtitleSource = source.value
                            },
                        )
                        HintText(source.description)
                    }
                }
            }
            if (subSource == MissAvSubtitleSettingsRepo.SOURCE_SUBTITLECAT) {
                item(key = "sublang") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = false, onClick = { showLanguageDialog = true })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subtitle language",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = MissAvSubtitleSettingsRepo.languageName(subLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "subtranslate") {
                    SwitchRow(
                        title = "Translate when missing",
                        subtitle = "If the chosen language is not available, translate the original " +
                                "subtitle with Google Translate",
                        checked = subTranslate,
                        onChange = {
                            subTranslate = it
                            SupJavSettings.subtitleTranslate = it
                        },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Troubleshooting") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            SupJavNetwork.clearCookies()
                            cookiesCleared = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (cookiesCleared) "Cloudflare cookies cleared"
                            else "Clear Cloudflare cookies"
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            SupJavSettings.resetToDefaults()
                            quality = SupJavSettings.preferredQuality
                            order = SupJavSettings.serverOrder
                            disabled = SupJavSettings.disabledServers
                            fallback = SupJavSettings.autoFallback
                            language = SupJavSettings.language
                            subSource = SupJavSettings.subtitleSource
                            subLanguage = SupJavSettings.subtitleLanguage
                            subTranslate = SupJavSettings.subtitleTranslate
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to defaults")
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
private fun ServerRow(
    title: String,
    description: String,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = null)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
