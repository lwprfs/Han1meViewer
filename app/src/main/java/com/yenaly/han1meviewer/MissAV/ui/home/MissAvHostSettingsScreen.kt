package com.yenaly.han1meviewer.MissAV.ui.home
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.yenaly.han1meviewer.Preferences

import com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetwork
private const val PREF_HOST = "missav_host"
private const val PREF_BASE_URL = "missav_base_url"

data class MissAvHostOption(
    val hostname: String,
    val url: String,
    val label: String,
)

object MissAvHostRepo {

    val options: List<MissAvHostOption> = listOf(
        MissAvHostOption("missav.ws", "https://missav.ws/", "missav.ws (default)"),
        MissAvHostOption("missav.live", "https://missav.live/", "missav.live (mirror)"),
        MissAvHostOption("missav.ai", "https://missav.ai/", "missav.ai (mirror)"),
    )

    fun current(context: Context): MissAvHostOption {
        val saved = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_HOST, null)
        return options.firstOrNull { it.hostname == saved } ?: options.first()
    }

    fun setCurrent(context: Context, option: MissAvHostOption) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_HOST, option.hostname)
            .apply()

        Preferences.preferenceSp.edit()
            .putString(PREF_BASE_URL, option.url)
            .apply()

        MissAvNetwork.rebuildNetwork()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvHostSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val options = MissAvHostRepo.options
    var current by remember { mutableStateOf(MissAvHostRepo.current(context)) }
    var pendingSwitch by remember { mutableStateOf<MissAvHostOption?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Host / Mirror") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = "Choose which MissAV hostname the app uses. Switching is instant; in-flight requests finish on the current host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(options, key = { it.hostname }) { option ->
                    HostOptionRow(
                        option = option,
                        selected = current.hostname == option.hostname,
                        onClick = {
                            if (current.hostname != option.hostname) {
                                pendingSwitch = option
                            }
                        },
                    )
                }
            }
        }
    }

    pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("Switch host") },
            text = {
                Text("Switch to ${target.label}? The current page will refresh against the new host.")
            },
            confirmButton = {
                TextButton(onClick = {
                    MissAvHostRepo.setCurrent(context, target)
                    current = target
                    pendingSwitch = null
                }) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitch = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun HostOptionRow(
    option: MissAvHostOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = option.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
