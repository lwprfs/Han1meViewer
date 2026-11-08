package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.network.MirrorProbeResult
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn

data class MirrorRowUi(
    val host: String,
    val baseUrl: String,
    val isDefault: Boolean,
    val selected: Boolean,
    val result: MirrorProbeResult,
)

private fun MirrorRowUi.sortKey(): Int = when (val r = result) {
    is MirrorProbeResult.Ok -> r.millis
    MirrorProbeResult.Pending -> 100_000
    is MirrorProbeResult.Unexpected -> 200_000
    is MirrorProbeResult.Failed -> 300_000
}

@Composable
fun MirrorLatencyDialog(
    siteName: String,
    rows: List<MirrorRowUi>,
    isTesting: Boolean,
    onUse: (MirrorRowUi) -> Unit,
    onUseFastest: () -> Unit,
    onRetest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = rows.sortedBy { it.sortKey() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mirror_latency_title, siteName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.mirror_latency_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isTesting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    enableItemAnimation = false,
                ) {
                    items(sorted, key = { it.host }) { row ->
                        MirrorRow(row = row, onUse = { onUse(row) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rows.any { it.result is MirrorProbeResult.Ok },
                onClick = onUseFastest,
            ) {
                Text(stringResource(R.string.mirror_latency_use_fastest))
            }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !isTesting, onClick = onRetest) {
                    Text(stringResource(R.string.mirror_latency_retest))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun MirrorRow(row: MirrorRowUi, onUse: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (row.isDefault) {
                    "${row.host} (${stringResource(R.string.default_)})"
                } else {
                    row.host
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val (text, color) = when (val r = row.result) {
                MirrorProbeResult.Pending ->
                    stringResource(R.string.mirror_latency_testing) to
                            MaterialTheme.colorScheme.onSurfaceVariant

                is MirrorProbeResult.Ok -> "${r.millis} ms" to when {
                    r.millis < 100 -> Color(0xFF4CAF50)
                    r.millis <= 500 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }

                is MirrorProbeResult.Unexpected ->
                    stringResource(R.string.mirror_latency_abnormal, r.code) to Color(0xFFF44336)

                is MirrorProbeResult.Failed ->
                    stringResource(R.string.network_timeout_text) to Color(0xFFF44336)
            }
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
        }
        TextButton(enabled = !row.selected, onClick = onUse) {
            Text(
                stringResource(
                    if (row.selected) R.string.mirror_latency_in_use else R.string.mirror_latency_use
                )
            )
        }
    }
}
