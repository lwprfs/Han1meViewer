package com.yenaly.han1meviewer.HentaiMama.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingInfoItem
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaVideoSettingsScreen(
    onBack: () -> Unit,
) {
    var selectedServer by remember { mutableStateOf(HentaiMamaVideoSettings.preferredServer) }
    var selectedQuality by remember { mutableStateOf(HentaiMamaVideoSettings.preferredQuality) }

    var showServerDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    val serverLabel = SERVER_OPTIONS.firstOrNull { it.second == selectedServer }?.first ?: selectedServer
    val qualityLabel = QUALITY_OPTIONS.firstOrNull { it.second == selectedQuality }?.second
        ?: selectedQuality

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
                    summary = "Choose your default server and quality. The app will use these whenever possible.",
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
