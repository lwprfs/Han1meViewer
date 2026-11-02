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
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSliderItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings

/** Site selector shown above the player settings. Hanime1/Javchu are the in-app player itself. */
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
    // Reading revision makes this screen recompose after every write.
    @Suppress("UNUSED_VARIABLE") val rev = SitePlayerSettings.revision
    val synced = SitePlayerSettings.isSynced(site)
    var dialogSpec by rememberSaveable { mutableStateOf<String?>(null) }

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

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "sync") {
            SettingSwitchItem(
                title = "Use in-app player settings",
                summary = if (synced) {
                    "${SitePlayerSettings.label(site)} follows the Hanime1 / Javchu player settings. " +
                            "Changing any value below keeps it separate for this site."
                } else {
                    "${SitePlayerSettings.label(site)} keeps its own player settings."
                },
                checked = synced,
                onCheckedChange = { on ->
                    if (on) SitePlayerSettings.resetToApp(site) else SitePlayerSettings.separate(site)
                },
            )
        }
        items(SitePlayerSettings.specs, key = { it.id }) { spec ->
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
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(
                    onClick = { SitePlayerSettings.resetToApp(site) },
                    enabled = !synced,
                ) {
                    Text(
                        "Reset to in-app player settings",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
