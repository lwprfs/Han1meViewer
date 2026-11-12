package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.material3.HorizontalDivider
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
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
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

private fun specIcon(id: String): Int = when (id) {
    SitePlayerSettings.ID_DEFAULT_SPEED, SitePlayerSettings.ID_SHOW_SPEED_BUTTON -> R.drawable.baseline_speed_24
    SitePlayerSettings.ID_LONG_PRESS_SPEED -> R.drawable.baseline_speed2_24
    SitePlayerSettings.ID_SLIDE_SENSITIVITY -> R.drawable.ic_settings_tune_24
    SitePlayerSettings.ID_BOTTOM_PROGRESS -> R.drawable.ic_settings_linear_scale_24
    SitePlayerSettings.ID_DOUBLE_TAP_SEEK, SitePlayerSettings.ID_GESTURE_SEEK -> R.drawable.ic_settings_fast_forward_24
    SitePlayerSettings.ID_HIDE_DELAY, SitePlayerSettings.ID_AUTO_HIDE,
    SitePlayerSettings.ID_SHOW_REMAINING -> R.drawable.ic_settings_timer_24
    SitePlayerSettings.ID_GESTURES, SitePlayerSettings.ID_DOUBLE_TAP_ENABLED,
    SitePlayerSettings.ID_LONG_PRESS_ENABLED -> R.drawable.ic_settings_touch_app_24
    SitePlayerSettings.ID_RESUME -> R.drawable.ic_settings_replay_24
    SitePlayerSettings.ID_AUTO_PLAY -> R.drawable.ic_baseline_play_arrow_24
    SitePlayerSettings.ID_STRETCH, SitePlayerSettings.ID_SHOW_STRETCH_BUTTON -> R.drawable.ic_settings_aspect_ratio_24
    SitePlayerSettings.ID_DEFAULT_QUALITY, SitePlayerSettings.ID_SHOW_QUALITY_BUTTON -> R.drawable.ic_settings_hd_24
    SitePlayerSettings.ID_KEEP_SCREEN_ON, SitePlayerSettings.ID_GESTURE_BRIGHTNESS -> R.drawable.ic_settings_brightness_24
    SitePlayerSettings.ID_GESTURE_VOLUME -> R.drawable.ic_settings_volume_up_24
    SitePlayerSettings.ID_DOUBLE_TAP_CENTER_PAUSE -> R.drawable.ic_settings_pause_24
    SitePlayerSettings.ID_CONTROLS_OPACITY -> R.drawable.ic_settings_opacity_24
    SitePlayerSettings.ID_SHOW_TITLE -> R.drawable.ic_settings_title_24
    SitePlayerSettings.ID_SHOW_CLOCK -> R.drawable.ic_settings_schedule_24
    SitePlayerSettings.ID_SHOW_LOCK_BUTTON -> R.drawable.ic_settings_lock_24
    SitePlayerSettings.ID_SHOW_FULLSCREEN_BUTTON -> R.drawable.ic_settings_fullscreen_24
    SitePlayerSettings.ID_SHOW_SKIP_BUTTONS -> R.drawable.ic_settings_skip_next_24
    else -> R.drawable.ic_baseline_settings_24
}

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
                    SitePlayerSettings.specsFor(site).forEach { spec ->
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

    val listedSpecs = SitePlayerSettings.specsFor(site)
        .filter { SitePlayerSettings.isListed(site, it.id) }
        .associateBy { it.id }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "sync") {
            SettingSwitchItem(
                title = stringResource(R.string.site_use_in_app_player_settings),
                summary = if (synced) {
                    stringResource(R.string.site_follows_the_hanime1_javchu_player_sett, SitePlayerSettings.label(site))
                } else {
                    stringResource(R.string.site_keeps_its_own_player_settings, SitePlayerSettings.label(site))
                },
                iconRes = R.drawable.ic_settings_sync_24,
                checked = synced,
                onCheckedChange = { on ->
                    if (on) SitePlayerSettings.resetToApp(site) else SitePlayerSettings.separate(site)
                },
            )
        }
        item(key = "bulk") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { SitePlayerSettings.setAllSwitches(site, true) }) {
                    Text(stringResource(R.string.ui_site_enable_all))
                }
                OutlinedButton(onClick = { SitePlayerSettings.setAllSwitches(site, false) }) {
                    Text(stringResource(R.string.ui_site_disable_all))
                }
            }
        }
        SitePlayerSettings.groups.forEach { (groupTitle, ids) ->
            val rows = ids.mapNotNull { listedSpecs[it] }
            if (rows.isEmpty()) return@forEach
            item(key = "group_$groupTitle") {
                com.yenaly.han1meviewer.ui.component.SettingSectionHeader(groupTitle)
            }
            items(rows, key = { it.id }) { spec ->
                val enabled = SitePlayerSettings.isEnabled(site, spec.id)
                when (spec) {
                    is SitePlayerSettings.Spec.Switch -> SettingSwitchItem(
                        title = spec.title,
                        summary = spec.summary,
                        iconRes = specIcon(spec.id),
                        checked = SitePlayerSettings.getString(site, spec.id).toBooleanStrictOrNull() ?: true,
                        enabled = enabled,
                        onCheckedChange = { SitePlayerSettings.setString(site, spec.id, it.toString()) },
                    )

                    is SitePlayerSettings.Spec.Choice -> {
                        val value = SitePlayerSettings.getString(site, spec.id)
                        SettingNavigationItem(
                            title = spec.title,
                            summary = spec.summary,
                            valueText = spec.options.firstOrNull { it.second == value }?.first ?: value,
                            iconRes = specIcon(spec.id),
                            enabled = enabled,
                            onClick = { dialogSpec = spec.id },
                        )
                    }

                    is SitePlayerSettings.Spec.Slider -> {
                        val value = SitePlayerSettings.getString(site, spec.id).toIntOrNull()
                            ?: spec.range.first
                        Box(modifier = Modifier.alpha(if (enabled) 1f else 0.38f)) {
                            SettingSliderItem(
                                title = spec.title,
                                summary = (spec.summary?.plus(" · ") ?: "") + value + spec.unit,
                                iconRes = specIcon(spec.id),
                                value = value,
                                valueRange = spec.range,
                                onValueChange = { SitePlayerSettings.setString(site, spec.id, it.toString()) },
                            )
                            if (!enabled) {

                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "reset") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
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

@Composable
fun SitePlayerSettingsPage(site: SiteType, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("${SitePlayerSettings.label(site)} · ${stringResource(R.string.player_settings)}") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        SitePlayerSettingsScreen(site = site, modifier = Modifier.padding(padding))
    }
}
