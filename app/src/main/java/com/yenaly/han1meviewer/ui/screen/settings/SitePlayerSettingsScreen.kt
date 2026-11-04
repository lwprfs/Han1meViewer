package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSliderItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings

@Composable
fun PlayerSiteChips(
    selected: SiteType,
    onSelect: (SiteType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sites = listOf(SiteType.HANIME) + SitePlayerSettings.configurableSites
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sites.forEach { site ->
            val label = if (site == SiteType.HANIME) "Hanime1 / Javchu" else SitePlayerSettings.label(site)
            FilterChip(
                selected = selected == site || (selected == SiteType.JAVCHU && site == SiteType.HANIME),
                onClick = { onSelect(site) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun SitePlayerSettingsScreen(site: SiteType, modifier: Modifier = Modifier) {

    @Suppress("UNUSED_VARIABLE") val rev = SitePlayerSettings.revision
    val synced = SitePlayerSettings.isSynced(site)
    var dialogSpec by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomize by rememberSaveable { mutableStateOf(false) }

    val choiceSpec = dialogSpec?.let { SitePlayerSettings.spec(it) } as? SitePlayerSettings.Spec.Choice
    if (choiceSpec != null) {
        ChoiceDialog(
            title = choiceSpec.title,
            options = choiceSpec.options,
            selectedValue = SitePlayerSettings.getString(site, choiceSpec.id),
            onDismiss = { dialogSpec = null },
            onSelect = {
                SitePlayerSettings.setString(site, choiceSpec.id, it)
                dialogSpec = null
            },
        )
    }

    if (showCustomize) {
        AlertDialog(
            onDismissRequest = { showCustomize = false },
            title = { Text(stringResource(R.string.site_customize_settings, SitePlayerSettings.label(site))) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        stringResource(R.string.site_choose_which_settings_are_listed_hidde),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SitePlayerSettings.specs.forEach { spec ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = SitePlayerSettings.isListed(site, spec.id),
                                onCheckedChange = { SitePlayerSettings.setListed(site, spec.id, it) },
                            )
                            Text(spec.title)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCustomize = false }) { Text(stringResource(R.string.site_done)) } },
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "sync") {
            SettingSwitchItem(
                title = stringResource(R.string.site_use_in_app_player_settings),
                summary = if (synced) {
                    stringResource(R.string.site_follows_the_hanime1_javchu_player_sett, SitePlayerSettings.label(site))
                } else {
                    stringResource(R.string.site_keeps_its_own_player_settings, SitePlayerSettings.label(site))
                },
                checked = synced,
                onCheckedChange = { on ->
                    if (on) SitePlayerSettings.resetToApp(site) else SitePlayerSettings.separate(site)
                },
            )
        }
        items(
            SitePlayerSettings.specs.filter { SitePlayerSettings.isListed(site, it.id) },
            key = { it.id },
        ) { spec ->
            when (spec) {
                is SitePlayerSettings.Spec.Switch -> SettingSwitchItem(
                    title = spec.title,
                    summary = spec.summary,
                    checked = SitePlayerSettings.getString(site, spec.id).toBooleanStrictOrNull() ?: true,
                    onCheckedChange = { SitePlayerSettings.setString(site, spec.id, it.toString()) },
                )

                is SitePlayerSettings.Spec.Choice -> {
                    val value = SitePlayerSettings.getString(site, spec.id)
                    SettingNavigationItem(
                        title = spec.title,
                        summary = spec.summary,
                        valueText = spec.options.firstOrNull { it.second == value }?.first ?: value,
                        onClick = { dialogSpec = spec.id },
                    )
                }

                is SitePlayerSettings.Spec.Slider -> {
                    val value = SitePlayerSettings.getString(site, spec.id).toIntOrNull()
                        ?: spec.range.first
                    SettingSliderItem(
                        title = spec.title,
                        summary = (spec.summary?.plus(" · ") ?: "") + value + spec.unit,
                        value = value,
                        valueRange = spec.range,
                        onValueChange = { SitePlayerSettings.setString(site, spec.id, it.toString()) },
                    )
                }
            }
        }
        item(key = "reset") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { showCustomize = true }) {
                    Text(stringResource(R.string.site_customize_settings_2), style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = { SitePlayerSettings.resetToApp(site) },
                    enabled = !synced,
                ) {
                    Text(
                        stringResource(R.string.site_reset_to_in_app_player_settings),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
