package com.yenaly.han1meviewer.OnAir.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.OnAir.data.model.OnAirMirrorStatus
import com.yenaly.han1meviewer.util.formatRelativeTime

@Composable
fun OnAirMirrorStatusCard(
    modifier: Modifier = Modifier,
    onSwitchSite: () -> Unit = {},
    viewModel: OnAirMirrorStatusViewModel = viewModel(),
) {
    val hosts = OnAirConstants.ONAIR_HOSTNAME.toList()

    var showPicker by remember { mutableStateOf(false) }
    var pendingSwitchHost by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mirror status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (viewModel.checkingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { viewModel.checkAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check all")
                    }
                }
            }

            Text(
                text = "Uses TorWatch to check whether each mirror is reachable " +
                        "through the Tor network. This does not expose your OnAir session " +
                        "— TorWatch only sees the .onion address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            hosts.forEach { host ->
                MirrorStatusRow(
                    host = host,
                    status = viewModel.statuses[host],
                    isSelected = host == viewModel.selectedHost,
                    onCheck = { viewModel.checkOne(host) },
                    onSelect = {
                        if (host != viewModel.selectedHost) {
                            pendingSwitchHost = host
                        }
                    },
                )
            }

            TextButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Change website link")
            }

            TextButton(
                onClick = onSwitchSite,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open site switcher")
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Choose OnAir mirror") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hosts.forEach { host ->
                        val selected = host == viewModel.selectedHost
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPicker = false
                                    if (!selected) pendingSwitchHost = host
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Close") }
            },
        )
    }

    pendingSwitchHost?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitchHost = null },
            title = { Text("Switch OnAir mirror?") },
            text = {
                Text(
                    "This will switch the active mirror to:\n\n$target\n\n" +
                            "OnAir network will be rebuilt and you may need to sign in again " +
                            "if the session cookies are not valid on this mirror.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSwitchHost = null
                    viewModel.switchTo(target)
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitchHost = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MirrorStatusRow(
    host: String,
    status: OnAirMirrorStatus?,
    isSelected: Boolean,
    onCheck: () -> Unit,
    onSelect: () -> Unit,
) {
    val dotColor: Color
    val label: String
    val detail: String?
    val isChecking: Boolean

    when (val s = status) {
        null -> {
            dotColor = MaterialTheme.colorScheme.outlineVariant
            label = "Not checked"
            detail = null
            isChecking = false
        }
        is OnAirMirrorStatus.Checking -> {
            dotColor = MaterialTheme.colorScheme.primary
            label = "Checking…"
            detail = null
            isChecking = true
        }
        is OnAirMirrorStatus.Online -> {
            dotColor = Color(0xFF2E7D32)
            label = "Online"
            detail = listOfNotNull(
                s.httpCode?.let { "HTTP $it" },
                s.detail.takeIf { it.isNotBlank() },
                formatRelativeTime(s.checkedAt),
            ).joinToString(" · ")
            isChecking = false
        }
        is OnAirMirrorStatus.Offline -> {
            dotColor = MaterialTheme.colorScheme.error
            label = "Offline / unreachable"
            detail = listOfNotNull(
                s.reason,
                formatRelativeTime(s.checkedAt),
            ).joinToString(" · ")
            isChecking = false
        }
        is OnAirMirrorStatus.Error -> {
            dotColor = MaterialTheme.colorScheme.error
            label = "Check failed"
            detail = listOfNotNull(s.message, formatRelativeTime(s.checkedAt)).joinToString(" · ")
            isChecking = false
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    TextButton(onClick = onCheck) { Text("Check") }
                }
            }
            Row {
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = dotColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!detail.isNullOrBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
