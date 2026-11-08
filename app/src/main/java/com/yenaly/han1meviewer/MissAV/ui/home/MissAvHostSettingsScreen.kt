package com.yenaly.han1meviewer.MissAV.ui.home
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs
import com.yenaly.han1meviewer.logic.network.SiteNetworks

import com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetwork
private const val PREF_HOST = "missav_host"

data class MissAvHostOption(
    val hostname: String,
    val url: String,
    val label: String,
)

object MissAvHostRepo {

    /** Default mirrors plus every MissAV mirror known to the mirror hub. */
    val options: List<MissAvHostOption>
        get() = SiteNetworks.forSite(SiteType.MISSAV).mirrors.mapIndexed { index, node ->
            MissAvHostOption(
                hostname = node.host,
                url = node.baseUrl,
                label = if (index == 0) "${node.host} (default)" else node.host,
            )
        }

    fun current(context: Context): MissAvHostOption {
        val all = options
        val selected = SiteNetworkPrefs.selectedMirrorUrl(SiteType.MISSAV)
        return all.firstOrNull { it.url.trimEnd('/') == selected.trimEnd('/') } ?: all.first()
    }

    fun setCurrent(context: Context, option: MissAvHostOption) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_HOST, option.hostname)
            .apply()

        SiteNetworkPrefs.setSelectedMirrorUrl(SiteType.MISSAV, option.url)

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
            SiteTopAppBar(
                title = { Text(stringResource(R.string.drawer_host_mirror)) },
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
                text = stringResource(R.string.missav_choose_which_missav_hostname_the_app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
            title = { Text(stringResource(R.string.missav_switch_host)) },
            text = {
                Text(stringResource(R.string.missav_switch_to_the_current_page_will, target.label))
            },
            confirmButton = {
                TextButton(onClick = {
                    MissAvHostRepo.setCurrent(context, target)
                    current = target
                    pendingSwitch = null
                }) {
                    Text(stringResource(R.string.missav_switch))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitch = null }) {
                    Text(stringResource(R.string.cancel))
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
                text = option.url,
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
