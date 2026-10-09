package com.yenaly.han1meviewer.HentaiMama.settings

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
import androidx.compose.material3.TopAppBar
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

private val SERVER_OPTIONS: List<Pair<String, String>> = listOf(
    "Automatic (first available)" to HentaiMamaVideoSettings.SERVER_AUTO,
    "Mirror 1" to "mi-1",
    "Mirror 2" to "mi-2",
    "Mirror 3" to "mi-3",
)

private val QUALITY_OPTIONS: List<Pair<String, String>> = listOf(
    "Automatic (first source)" to HentaiMamaVideoSettings.QUALITY_AUTO,
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
            TopAppBar(
                title = { Text("Video") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                    title = "Playback preferences",
                    summary = "Choose your default server and quality. " +
                            "The app will use these whenever possible.",
                )
            }

            item {
                SettingNavigationItem(
                    title = "Preferred server",
                    summary = "Pick the mirror the app should try first when loading an episode.",
                    valueText = serverLabel,
                    iconRes = R.drawable.ic_baseline_play_arrow_24,
                    onClick = { showServerDialog = true },
                )
            }

            item {
                SettingNavigationItem(
                    title = "Preferred quality",
                    summary = "Pick the source quality the app should auto-select when loading an episode.",
                    valueText = qualityLabel,
                    iconRes = R.drawable.baseline_decoder_24,
                    onClick = { showQualityDialog = true },
                )
            }

            item {
                SettingNavigationItem(
                    title = "Clear Cloudflare cookie",
                    summary = "Force the app to re-solve the Cloudflare challenge " +
                            "on the next request. Use this if HentaiMama starts " +
                            "rejecting a cookie that has not yet expired.",
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
                        title = "Home card size",
                        summary = "Adjust how large the video cards appear on the home and search screens.\n\n" +
                                "• Cards per row — how many cards fit across the screen.\n" +
                                "  Change this FIRST to set your target layout.\n" +
                                "  Doing so resets Height and Width to the Han1meViewer default (1.00× / 1.00×).\n" +
                                "• Height — how tall the thumbnail is. Change AFTER Cards to fine-tune the shape.\n" +
                                "• Width (advanced) — a final width tweak. Only change this if Height\n" +
                                "  and Cards do not give you the look you want. Recommended value: 1.00×.\n\n" +
                                "Recommended workflow:\n" +
                                "  1) Set Cards per row.\n" +
                                "  2) Set Height.\n" +
                                "  3) Leave Width at 1.00× unless absolutely necessary.",
                    )
                }
                item {
                    CardSettingSlider(
                        title = "Cards per row",
                        value = HentaiMamaCardSettings.cardMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_CARD_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_CARD_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.cardMultiplier),
                        note = "Changing this resets Height and Width to their defaults (1.00×).",
                        onValueChange = {
                            HentaiMamaCardSettings.applyCardsMultiplier(it)
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = "Height",
                        value = HentaiMamaCardSettings.heightMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_HEIGHT_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_HEIGHT_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.heightMultiplier),
                        note = "1.00× matches the standard Han1meViewer card height.",
                        onValueChange = {
                            HentaiMamaCardSettings.heightMultiplier = it
                        },
                    )
                }
                item {
                    CardSettingSlider(
                        title = "Width (advanced)",
                        value = HentaiMamaCardSettings.widthMultiplier,
                        valueRange = HentaiMamaCardSettings.MIN_WIDTH_MULTIPLIER..
                                HentaiMamaCardSettings.MAX_WIDTH_MULTIPLIER,
                        valueLabel = "×%.2f".format(HentaiMamaCardSettings.widthMultiplier),
                        note = "⚠ Not recommended. Leave at 1.00× unless you know what you're doing. " +
                                "It stacks on top of Cards and Height and is easy to make look wrong.",
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
                        Text("Reset home card size to defaults")
                    }
                }
            }

            if (showRecentCardSettings) {
                item {
                    SettingInfoItem(
                        title = "Recent Episodes card size",
                        summary = "Controls how large the cards appear on the Recent Episodes page.",
                    )
                }
                item {
                    CardSettingSlider(
                        title = "Recent · Cards per row",
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
                        title = "Recent · Height",
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
                        title = "Recent · Width (advanced)",
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
                        Text("Reset recent card size to defaults")
                    }
                }
            }

            if (showUpcomingCardSettings) {
                item {
                    SettingInfoItem(
                        title = "Upcoming card size",
                        summary = "Controls how large the cards appear on the Upcoming page.",
                    )
                }
                item {
                    CardSettingSlider(
                        title = "Upcoming · Cards per row",
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
                        title = "Upcoming · Height",
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
                        title = "Upcoming · Width (advanced)",
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
                        Text("Reset upcoming card size to defaults")
                    }
                }
            }
        }
    }

    ChoiceDialog(
        visible = showServerDialog,
        title = "Preferred server",
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
        title = "Preferred quality",
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
