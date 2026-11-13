package com.yenaly.han1meviewer.MissAV.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.util.applicationContext

data class MissAvQualityOption(

    val value: String,
    val label: String,
    val description: String,
)

object MissAvQualityRepo {

    val options: List<MissAvQualityOption> = listOf(
        MissAvQualityOption("auto", "Auto", applicationContext.getString(R.string.missav_adapts_to_your_connection_while_playin)),
        MissAvQualityOption("1080", "1080p", applicationContext.getString(R.string.missav_full_hd_highest_data_use)),
        MissAvQualityOption("720", "720p", "HD"),
        MissAvQualityOption("480", "480p", applicationContext.getString(R.string.missav_standard)),
        MissAvQualityOption("360", "360p", applicationContext.getString(R.string.missav_lowest_data_use)),
    )

    fun current(): MissAvQualityOption {
        val saved = Preferences.missAvPreferredQuality
        return options.firstOrNull { it.value == saved } ?: options.first()
    }

    fun setCurrent(option: MissAvQualityOption) {
        Preferences.missAvPreferredQuality = option.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvQualitySettingsScreen(
    onBack: () -> Unit,
) {
    var current by remember { mutableStateOf(MissAvQualityRepo.current()) }
    var locked by remember { mutableStateOf(Preferences.missAvLockQuality) }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.missav_video_resolution)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.missav_resolution_a_missav_video_starts_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.missav_lock_resolution),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.missav_always_play_the_selected_resolution_an),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = locked,
                    onCheckedChange = {
                        locked = it
                        Preferences.missAvLockQuality = it
                    },
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(MissAvQualityRepo.options, key = { it.value }) { option ->
                    QualityOptionRow(
                        option = option,
                        selected = current.value == option.value,
                        onClick = {
                            MissAvQualityRepo.setCurrent(option)
                            current = option
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityOptionRow(
    option: MissAvQualityOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
