package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.yenaly.han1meviewer.R
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.logic.BackupManager
import com.yenaly.han1meviewer.logic.BackupManager.Section

@Composable
fun BackupSectionDialog(
    visible: Boolean,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: (Set<BackupManager.Section>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var selected by remember { mutableStateOf(Section.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selected = Section.entries.toSet() }) { Text(stringResource(R.string.select_all)) }
                    TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.deselect_all)) }
                }
                listOf(
                    Section.Group.DATA to stringResource(R.string.backup_group_data),
                    Section.Group.COOKIES to stringResource(R.string.backup_group_cookies),
                ).forEach { (group, header) ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    Section.entries.filter { it.group == group }.forEach { section ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (section in selected) selected - section
                                    else selected + section
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = section in selected, onCheckedChange = null)
                            Text(
                                text = stringResource(section.labelRes),
                                modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected) }) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
