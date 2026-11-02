package com.yenaly.han1meviewer.FapFolder.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.util.FapFolderImageLocation

/** Pick / change / clear the folder used for FapFolder image downloads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderImageFolderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null) {
            FapFolderImageLocation.persist(context, tree)
            FapFolderImageLocation.confirmedThisSession = true
            refresh++
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("Image download folder") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ready = refresh >= 0 && FapFolderImageLocation.isReady(context)
            Text(
                text = "FapFolder images are saved to their own folder, separate from the video " +
                        "download location.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (ready) "Current folder:\n${FapFolderImageLocation.displayName(context)}"
                else if (FapFolderImageLocation.uri != null) "Access to the saved folder was lost. Choose it again."
                else "No folder chosen yet. You will be asked when you download an image.",
                style = MaterialTheme.typography.titleSmall,
            )
            Button(
                onClick = { picker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ready) "Change folder" else "Choose folder") }
            if (FapFolderImageLocation.uri != null) {
                OutlinedButton(
                    onClick = {
                        FapFolderImageLocation.uri = null
                        FapFolderImageLocation.confirmedThisSession = false
                        refresh++
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Forget folder") }
            }
        }
    }
}
