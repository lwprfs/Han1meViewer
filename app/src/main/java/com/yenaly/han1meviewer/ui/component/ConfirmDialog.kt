package com.yenaly.han1meviewer.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yenaly.han1meviewer.ui.preview.ComponentPreview

@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelable: Boolean = true,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConfirmDialogPreview() {
    ComponentPreview {
        ConfirmDialog(
            visible = true,
            title = "删除历史记录",
            message = "确定要删除这条记录吗？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
