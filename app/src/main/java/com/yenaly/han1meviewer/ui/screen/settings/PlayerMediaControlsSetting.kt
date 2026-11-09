package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.player.PlayerMediaControl.Settings
import com.yenaly.han1meviewer.ui.component.ChoiceDialog

@Composable
fun PlayerMediaControlsSetting(onClick: () -> Unit) {
    SettingNavigationItem(
        title = stringResource(R.string.player_media_title),
        summary = stringResource(R.string.player_media_summary),
        iconRes = R.drawable.ic_settings_headset_24,
        onClick = onClick,
    )
}

@Composable
private fun SectionTitle(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
    }
}

@Composable
fun MediaControlsSettingsScreen(onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(Settings.enabled) }
    var playPause by remember { mutableStateOf(Settings.playPause) }
    var volume by remember { mutableStateOf(Settings.volumeKeys) }
    var nextPrev by remember { mutableStateOf(Settings.nextPrevious) }
    var ffRew by remember { mutableStateOf(Settings.fastForwardRewind) }
    var noisy by remember { mutableStateOf(Settings.pauseWhenNoisy) }
    var info by remember { mutableStateOf(Settings.shareInfo) }
    var step by remember { mutableIntStateOf(Settings.seekStepSec) }
    var showStepDialog by rememberSaveable { mutableStateOf(false) }

    ChoiceDialog(
        visible = showStepDialog,
        title = stringResource(R.string.player_media_seek_step),
        options = Settings.SEEK_STEPS.map { stringResource(R.string.player_media_seconds, it) to it.toString() },
        selectedValue = step.toString(),
        onDismiss = { showStepDialog = false },
        onSelect = {
            showStepDialog = false
            step = it.toInt()
            Settings.seekStepSec = step
        },
    )

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.player_media_title)) },
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
                    title = stringResource(R.string.player_media_enable),
                    summary = stringResource(R.string.player_media_enable_desc),
                    iconRes = R.drawable.ic_settings_bluetooth_24,
                    checked = enabled,
                    onCheckedChange = { enabled = it; Settings.enabled = it },
                )
            }

            item { SectionTitle(stringResource(R.string.player_media_section_buttons)) }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.player_media_play_pause),
                    summary = stringResource(R.string.player_media_play_pause_desc),
                    iconRes = R.drawable.ic_baseline_play_arrow_24,
                    checked = playPause, enabled = enabled,
                    onCheckedChange = { playPause = it; Settings.playPause = it },
                )
            }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.player_media_volume),
                    summary = stringResource(R.string.player_media_volume_desc),
                    iconRes = R.drawable.ic_settings_volume_up_24,
                    checked = volume, enabled = enabled,
                    onCheckedChange = { volume = it; Settings.volumeKeys = it },
                )
            }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.player_media_next_prev),
                    summary = stringResource(R.string.player_media_next_prev_desc),
                    iconRes = R.drawable.ic_settings_skip_next_24,
                    checked = nextPrev, enabled = enabled,
                    onCheckedChange = { nextPrev = it; Settings.nextPrevious = it },
                )
            }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.player_media_ff_rew),
                    summary = stringResource(R.string.player_media_ff_rew_desc),
                    iconRes = R.drawable.ic_settings_fast_forward_24,
                    checked = ffRew, enabled = enabled,
                    onCheckedChange = { ffRew = it; Settings.fastForwardRewind = it },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.player_media_seek_step),
                    summary = stringResource(R.string.player_media_seek_step_desc),
                    valueText = stringResource(R.string.player_media_seconds, step),
                    iconRes = R.drawable.ic_settings_timer_24,
                    enabled = enabled,
                    onClick = { showStepDialog = true },
                )
            }

            item { SectionTitle(stringResource(R.string.player_media_section_behavior)) }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.player_media_noisy),
                    summary = stringResource(R.string.player_media_noisy_desc),
                    iconRes = R.drawable.ic_settings_pause_24,
                    checked = noisy, enabled = enabled,
                    onCheckedChange = { noisy = it; Settings.pauseWhenNoisy = it },
                )
            }
            item {
                SettingSwitchItem(
                    title = stringResource(R.string.player_media_info),
                    summary = stringResource(R.string.player_media_info_desc),
                    iconRes = R.drawable.ic_settings_info_24,
                    checked = info, enabled = enabled,
                    onCheckedChange = { info = it; Settings.shareInfo = it },
                )
            }
        }
    }
}
