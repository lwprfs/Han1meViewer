package com.yenaly.han1meviewer.HentaiMama.ui.video

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HentaiMama.data.model.Mirror

@Composable
fun HentaiMamaMirrorSelector(
    mirrors: List<Mirror>,
    selectedMirrorIndex: Int,
    onMirrorSelected: (Int) -> Unit,
    qualities: Map<String, String>,
    selectedQuality: String,
    onQualitySelected: (String) -> Unit,
    speeds: List<Float>,
    selectedSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            mirrors.forEachIndexed { index, mirror ->
                FilterChip(
                    selected = index == selectedMirrorIndex,
                    onClick = { onMirrorSelected(index) },
                    label = { Text(mirror.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Box {
            AssistChip(
                onClick = { showQualityMenu = true },
                label = { Text(selectedQuality.ifBlank { "Auto" }) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            DropdownMenu(
                expanded = showQualityMenu,
                onDismissRequest = { showQualityMenu = false },
            ) {
                if (qualities.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No quality options") },
                        onClick = { showQualityMenu = false },
                        enabled = false,
                    )
                } else {
                    qualities.keys.forEach { quality ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (quality == selectedQuality) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(quality)
                                }
                            },
                            onClick = {
                                onQualitySelected(quality)
                                showQualityMenu = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(6.dp))

        Box {
            IconButton(onClick = { showSpeedMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = "Speed",
                )
            }
            DropdownMenu(
                expanded = showSpeedMenu,
                onDismissRequest = { showSpeedMenu = false },
            ) {
                speeds.forEach { speed ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (speed == selectedSpeed) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("${speed}x")
                            }
                        },
                        onClick = {
                            onSpeedSelected(speed)
                            showSpeedMenu = false
                        },
                    )
                }
            }
        }
    }
}
