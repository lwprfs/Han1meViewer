package com.yenaly.han1meviewer.FapFolder.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.util.FapFolderImageLocation
private enum class ImageGateDialog { None, NeedFolder, ConfirmFolder }

@Composable
fun rememberFapFolderImageGate(): (onReady: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var dialog by remember { mutableStateOf(ImageGateDialog.None) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        val action = pending
        pending = null
        dialog = ImageGateDialog.None
        if (tree != null) {
            FapFolderImageLocation.persist(context, tree)
            FapFolderImageLocation.confirmedThisSession = true
            action?.invoke()
        }
    }

    fun cancel() {
        pending = null
        dialog = ImageGateDialog.None
    }

    when (dialog) {
        ImageGateDialog.None -> Unit
        ImageGateDialog.NeedFolder -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text(stringResource(R.string.fap_choose_image_download_folder)) },
            text = {
                Text(
                    stringResource(R.string.fap_fapfolder_images_are_saved_to_their)
                )
            },
            confirmButton = {
                TextButton(onClick = { picker.launch(null) }) { Text(stringResource(R.string.fap_choose_folder)) }
            },
            dismissButton = { TextButton(onClick = ::cancel) { Text(stringResource(R.string.cancel)) } },
        )

        ImageGateDialog.ConfirmFolder -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text(stringResource(R.string.fap_save_images_here)) },
            text = { Text(stringResource(R.string.fap_images_will_be_saved_to, FapFolderImageLocation.displayName(context))) },
            confirmButton = {
                TextButton(onClick = {
                    val action = pending
                    cancel()
                    FapFolderImageLocation.confirmedThisSession = true
                    action?.invoke()
                }) { Text(stringResource(R.string.fap_download_here)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { picker.launch(null) }) { Text(stringResource(R.string.fap_change_folder)) }
                    TextButton(onClick = ::cancel) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    return remember(context) {
        { onReady: () -> Unit ->
            when {
                !FapFolderImageLocation.isReady(context) -> {
                    pending = onReady
                    dialog = ImageGateDialog.NeedFolder
                }
                !FapFolderImageLocation.confirmedThisSession -> {
                    pending = onReady
                    dialog = ImageGateDialog.ConfirmFolder
                }
                else -> onReady()
            }
        }
    }
}
