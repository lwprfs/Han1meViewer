package com.yenaly.han1meviewer.RouVideo.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoNetwork
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.network.MirrorLatency
import com.yenaly.han1meviewer.logic.network.MirrorNode
import com.yenaly.han1meviewer.logic.network.MirrorProbeResult
import com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs
import com.yenaly.han1meviewer.logic.network.SiteNetworks
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.screen.settings.MirrorLatencyDialog
import com.yenaly.han1meviewer.ui.screen.settings.MirrorRowUi
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val ADDRESS_PAGE = "https://rdz4.xyz/dizhi"

@Composable
fun RouVideoHostSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val mirrors = SiteNetworks.forSite(SiteType.ROUVIDEO).mirrors
    var selected by remember { mutableStateOf(SiteNetworkPrefs.selectedMirrorUrl(SiteType.ROUVIDEO)) }
    var customActive by remember { mutableStateOf(SiteNetworkPrefs.useCustomMirror(SiteType.ROUVIDEO)) }
    var pending by remember { mutableStateOf<MirrorNode?>(null) }

    var showLatency by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val results = remember { mutableStateMapOf<String, MirrorProbeResult>() }
    val run = remember { AtomicInteger(0) }
    val executor = remember { Executors.newFixedThreadPool(6) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun startTest() {
        val id = run.incrementAndGet()
        results.clear()
        mirrors.forEach { results[it.host] = MirrorProbeResult.Pending }
        testing = mirrors.isNotEmpty()
        val remaining = AtomicInteger(mirrors.size)
        mirrors.forEach { node ->
            executor.execute {
                val result = if (run.get() == id) MirrorLatency.probe(node) else null
                handler.post {
                    if (run.get() == id) {
                        if (result != null) results[node.host] = result
                        if (remaining.decrementAndGet() == 0) testing = false
                    }
                }
            }
        }
    }

    fun stopTest() {
        run.incrementAndGet()
        testing = false
        showLatency = false
    }

    DisposableEffect(Unit) {
        onDispose {
            run.incrementAndGet()
            executor.shutdownNow()
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.drawer_host_mirror)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(R.string.rouvideo_host_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (customActive) {
                Text(
                    text = stringResource(
                        R.string.rouvideo_host_custom_active,
                        SiteNetworkPrefs.customMirror(SiteType.ROUVIDEO),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            OutlinedButton(
                onClick = {
                    showLatency = true
                    startTest()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.mirror_latency_entry)) }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(ADDRESS_PAGE))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.rouvideo_host_open_address_page)) }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(mirrors, key = { it.host }) { node ->
                    val isSelected = !customActive &&
                            node.baseUrl.trimEnd('/') == selected.trimEnd('/')
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = isSelected, onClick = { if (!isSelected) pending = node })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (node == mirrors.first()) {
                                    stringResource(R.string.rouvideo_host_default, node.host)
                                } else node.host,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = node.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLatency) {
        val rows = mirrors.mapIndexed { index, node ->
            MirrorRowUi(
                host = node.host,
                baseUrl = node.baseUrl,
                isDefault = index == 0,
                selected = !customActive && node.baseUrl.trimEnd('/') == selected.trimEnd('/'),
                result = results[node.host] ?: MirrorProbeResult.Pending,
            )
        }
        MirrorLatencyDialog(
            siteName = stringResource(R.string.site_rouvideo_name),
            rows = rows,
            isTesting = testing,
            onUse = { row ->
                mirrors.firstOrNull { it.host == row.host }?.let { node ->
                    stopTest()
                    pending = node
                }
            },
            onUseFastest = {
                val fastest = MirrorLatency.fastestHost(results.toMap())
                mirrors.firstOrNull { it.host == fastest }?.let { node ->
                    stopTest()
                    pending = node
                }
            },
            onRetest = { startTest() },
            onDismiss = { stopTest() },
        )
    }

    pending?.let { target ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.rouvideo_host_switch)) },
            text = { Text(stringResource(R.string.rouvideo_host_switch_message, target.host)) },
            confirmButton = {
                TextButton(onClick = {

                    if (customActive) {
                        SiteNetworkPrefs.setCustomMirror(
                            SiteType.ROUVIDEO,
                            enabled = false,
                            url = SiteNetworkPrefs.customMirror(SiteType.ROUVIDEO),
                            appendPath = SiteNetworkPrefs.appendCustomPath(SiteType.ROUVIDEO),
                        )
                        customActive = false
                    }
                    SiteNetworkPrefs.setSelectedMirrorUrl(SiteType.ROUVIDEO, target.baseUrl)
                    RouVideoNetwork.rebuildNetwork()
                    selected = target.baseUrl
                    pending = null
                }) { Text(stringResource(R.string.missav_switch)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
