package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.update.UpdatePhase
import com.yenaly.han1meviewer.logic.update.UpdateManager
import com.yenaly.han1meviewer.logic.update.UpdateStage
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem

private val historySiteNames = listOf(
    "Hanime1", "MissAV", "HentaiMama", "OnAir", "FapFolder", "SupJav", "RouVideo", "XHamster",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsHistoryAllSitesCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_history_all_sites),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.hist_all_sites_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.hist_all_sites_summary,
                            historySiteNames.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                historySiteNames.forEach { name ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsUpdateStatusEntry(
    idleSummary: String,
    onClick: () -> Unit,
) {
    val ui by UpdateManager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val summary = when (ui.phase) {
        UpdatePhase.DOWNLOADING -> {
            val stage = when (ui.stage) {
                UpdateStage.PREPARING -> R.string.upd_stage_preparing
                UpdateStage.CHECKING_DELTA -> R.string.upd_stage_checking_delta
                UpdateStage.DOWNLOADING_DELTA -> R.string.upd_stage_downloading_delta
                UpdateStage.APPLYING_PATCH -> R.string.upd_stage_applying_patch
                UpdateStage.VERIFYING -> R.string.upd_stage_verifying
                UpdateStage.DOWNLOADING_FULL -> R.string.upd_stage_downloading_full
            }
            context.getString(R.string.upd_home_downloading, ui.progress, context.getString(stage))
        }

        UpdatePhase.DOWNLOADED -> context.getString(R.string.upd_status_downloaded)
        UpdatePhase.INSTALLING -> context.getString(R.string.upd_status_installing)
        UpdatePhase.FAILED -> context.getString(R.string.upd_status_failed)
        UpdatePhase.CANCELLED -> context.getString(R.string.upd_status_cancelled)
        UpdatePhase.IDLE -> idleSummary
    }
    val icon = when (ui.phase) {
        UpdatePhase.DOWNLOADING -> R.drawable.ic_update_download
        UpdatePhase.DOWNLOADED, UpdatePhase.INSTALLING -> R.drawable.ic_update_installer
        UpdatePhase.FAILED -> R.drawable.baseline_error_outline_24
        else -> R.drawable.ic_update_available
    }

    Column {
        SettingNavigationItem(
            title = stringResource(R.string.upd_screen_title),
            summary = summary,
            iconRes = icon,
            onClick = onClick,
        )
        if (ui.phase == UpdatePhase.DOWNLOADING || ui.phase == UpdatePhase.INSTALLING) {
            val determinate = ui.phase == UpdatePhase.DOWNLOADING &&
                    ui.totalBytes > 0 &&
                    (ui.stage == UpdateStage.DOWNLOADING_DELTA || ui.stage == UpdateStage.DOWNLOADING_FULL)
            if (determinate) {
                LinearProgressIndicator(
                    progress = { ui.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}
