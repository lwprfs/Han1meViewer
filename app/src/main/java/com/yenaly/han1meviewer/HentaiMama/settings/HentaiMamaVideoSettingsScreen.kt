package com.yenaly.han1meviewer.HentaiMama.settings

import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingInfoItem
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.HentaiMama.cloudflare.HentaiMamaCloudflareCookieManager
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaCookieJar
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.yenaly_libs.utils.applicationContext

private val SERVER_OPTIONS: List<Pair<String, String>> = listOf(
    applicationContext.getString(R.string.hm_automatic_first_available) to HentaiMamaVideoSettings.SERVER_AUTO,
    applicationContext.getString(R.string.hm_mirror_1) to "mi-1",
    applicationContext.getString(R.string.hm_mirror_2) to "mi-2",
    applicationContext.getString(R.string.hm_mirror_3) to "mi-3",
)

private val QUALITY_OPTIONS: List<Pair<String, String>> = listOf(
    applicationContext.getString(R.string.hm_automatic_first_source) to HentaiMamaVideoSettings.QUALITY_AUTO,
    "1080p" to "1080p",
    "720p" to "720p",
    "480p" to "480p",
    "360p" to "360p",
    "HLS" to "HLS",
)

enum class HentaiMamaCardSettingsTarget {
    Home,
    Recent,
    Upcoming,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaVideoSettingsScreen(
    onBack: () -> Unit,
    targets: Set<HentaiMamaCardSettingsTarget> = setOf(
        HentaiMamaCardSettingsTarget.Home,
        HentaiMamaCardSettingsTarget.Recent,
        HentaiMamaCardSettingsTarget.Upcoming,
    ),
) {
    var selectedServer by remember { mutableStateOf(HentaiMamaVideoSettings.preferredServer) }
    var selectedQuality by remember { mutableStateOf(HentaiMamaVideoSettings.preferredQuality) }

    var showServerDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    val serverLabel = SERVER_OPTIONS.firstOrNull { it.second == selectedServer }?.first
        ?: selectedServer
    val qualityLabel = QUALITY_OPTIONS.firstOrNull { it.second == selectedQuality }?.second
        ?: selectedQuality

    val showHomeCardSettings = HentaiMamaCardSettingsTarget.Home in targets
    val showRecentCardSettings = HentaiMamaCardSettingsTarget.Recent in targets
    val showUpcomingCardSettings = HentaiMamaCardSettingsTarget.Upcoming in targets

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.video)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                SettingInfoItem(
                    title = stringResource(R.string.hm_playback_preferences),
                    summary = stringResource(R.string.hm_choose_your_default_server_and_quality),
                )
            }

            item {
                SettingNavigationItem(
                    title = stringResource(R.string.hm_preferred_server),
                    summary = stringResource(R.string.hm_pick_the_mirror_the_app_should),
                    valueText = serverLabel,
                    iconRes = R.drawable.ic_baseline_play_arrow_24,
                    onClick = { showServerDialog = true },
                )
            }

            item {
                SettingNavigationItem(
                    title = stringResource(R.string.hm_preferred_quality),
                    summary = stringResource(R.string.hm_pick_the_source_quality_the_app),
                    valueText = qualityLabel,
                    iconRes = R.drawable.baseline_decoder_24,
                    onClick = { showQualityDialog = true },
                )
            }

            item {
                SettingNavigationItem(
                    title = stringResource(R.string.hm_clear_cloudflare_cookie),
                    summary = stringResource(R.string.hm_force_the_app_to_re_solve),
                    iconRes = R.drawable.baseline_hosts_24,
                    onClick = {
                        HentaiMamaCloudflareCookieManager.clearAllCloudflareCookies()
                        HentaiMamaCookieJar().clearCookies()
                        HentaiMamaNetwork.rebuildNetwork()
                    },
                )
            }

            if (showHomeCardSettings) {
                item {
                    SettingInfoItem(
                        title = stringResource(R.string.hm_home_card_size),
                        summary = stringResource(R.string.hm_adjust_how_large_the_video_cards),
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.site_cards_per_row),
                        value = HentaiMamaCardSettings.cardMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_CARD_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_CARD_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.cardMultiplier),
                        note = stringResource(R.string.hm_changing_this_resets_height_and_width),
                        onValueChange = {
                            HentaiMamaCardSettings.applyCardsMultiplier(it)
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_height),
                        value = HentaiMamaCardSettings.heightMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_HEIGHT_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_HEIGHT_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.heightMultiplier),
                        note = stringResource(R.string.hm_1_00_matches_the_standard_han1meviewer),
                        onValueChange = {
                            HentaiMamaCardSettings.heightMultiplier = it
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_width_advanced),
                        value = HentaiMamaCardSettings.widthMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_WIDTH_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_WIDTH_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.widthMultiplier),
                        note = stringResource(R.string.hm_not_recommended_leave_at_1_00),
                        onValueChange = {
                            HentaiMamaCardSettings.widthMultiplier = it
                        },
                    )
                }
                item {
                    TextButton(
                        onClick = { HentaiMamaCardSettings.resetToDefaults() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.hm_reset_home_card_size_to_defaults))
                    }
                }
            }

            if (showRecentCardSettings) {
                item {
                    SettingInfoItem(
                        title = stringResource(R.string.hm_recent_episodes_card_size),
                        summary = stringResource(R.string.hm_controls_how_large_the_cards_appear),
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_recent_cards_per_row),
                        value = HentaiMamaCardSettings.recentCardMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_CARD_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_CARD_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.recentCardMultiplier),
                        onValueChange = {
                            HentaiMamaCardSettings.recentCardMultiplier = it
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_recent_height),
                        value = HentaiMamaCardSettings.recentHeightMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_HEIGHT_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_HEIGHT_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.recentHeightMultiplier),
                        onValueChange = {
                            HentaiMamaCardSettings.recentHeightMultiplier = it
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_recent_width_advanced),
                        value = HentaiMamaCardSettings.recentWidthMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_WIDTH_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_WIDTH_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.recentWidthMultiplier),
                        onValueChange = {
                            HentaiMamaCardSettings.recentWidthMultiplier = it
                        },
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            HentaiMamaCardSettings.recentCardMultiplier =
                                HentaiMamaCardSettings.DEFAULT_RECENT_CARD_MULTIPLIER
                            HentaiMamaCardSettings.recentHeightMultiplier =
                                HentaiMamaCardSettings.DEFAULT_RECENT_HEIGHT_MULTIPLIER
                            HentaiMamaCardSettings.recentWidthMultiplier =
                                HentaiMamaCardSettings.DEFAULT_RECENT_WIDTH_MULTIPLIER
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.hm_reset_recent_card_size_to_defaults))
                    }
                }
            }

            if (showUpcomingCardSettings) {
                item {
                    SettingInfoItem(
                        title = stringResource(R.string.hm_upcoming_card_size),
                        summary = stringResource(R.string.hm_controls_how_large_the_cards_appear_2),
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_upcoming_cards_per_row),
                        value = HentaiMamaCardSettings.upcomingCardMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_CARD_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_CARD_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.upcomingCardMultiplier),
                        onValueChange = {
                            HentaiMamaCardSettings.upcomingCardMultiplier = it
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_upcoming_height),
                        value = HentaiMamaCardSettings.upcomingHeightMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_HEIGHT_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_HEIGHT_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.upcomingHeightMultiplier),
                        onValueChange = {
                            HentaiMamaCardSettings.upcomingHeightMultiplier = it
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = stringResource(R.string.hm_upcoming_width_advanced),
                        value = HentaiMamaCardSettings.upcomingWidthMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_WIDTH_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_WIDTH_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.upcomingWidthMultiplier),
                        onValueChange = {
                            HentaiMamaCardSettings.upcomingWidthMultiplier = it
                        },
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            HentaiMamaCardSettings.upcomingCardMultiplier =
                                HentaiMamaCardSettings.DEFAULT_UPCOMING_CARD_MULTIPLIER
                            HentaiMamaCardSettings.upcomingHeightMultiplier =
                                HentaiMamaCardSettings.DEFAULT_UPCOMING_HEIGHT_MULTIPLIER
                            HentaiMamaCardSettings.upcomingWidthMultiplier =
                                HentaiMamaCardSettings.DEFAULT_UPCOMING_WIDTH_MULTIPLIER
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.hm_reset_upcoming_card_size_to_defaults))
                    }
                }
            }
        }
    }

    ChoiceDialog(
        visible = showServerDialog,
        title = stringResource(R.string.hm_preferred_server),
        options = SERVER_OPTIONS,
        selectedValue = selectedServer,
        onDismiss = { showServerDialog = false },
        onSelect = { value ->
            selectedServer = value
            HentaiMamaVideoSettings.preferredServer = value
            showServerDialog = false
        },
    )

    ChoiceDialog(
        visible = showQualityDialog,
        title = stringResource(R.string.hm_preferred_quality),
        options = QUALITY_OPTIONS,
        selectedValue = selectedQuality,
        onDismiss = { showQualityDialog = false },
        onSelect = { value ->
            selectedQuality = value
            HentaiMamaVideoSettings.preferredQuality = value
            showQualityDialog = false
        },
    )
}

@Composable
private fun CardSettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    note: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
