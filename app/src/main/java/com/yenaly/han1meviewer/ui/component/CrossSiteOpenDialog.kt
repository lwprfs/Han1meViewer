package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface CrossSitePhase {
    data object Confirm : CrossSitePhase
    data object Validating : CrossSitePhase
    data class Failed(val title: String, val message: String) : CrossSitePhase
}

@Composable
fun CrossSiteOpenDialog(
    request: PendingVideoOpen,
    onDismiss: () -> Unit,
    onValidated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember(request) { mutableStateOf<CrossSitePhase>(CrossSitePhase.Confirm) }
    var validationJob by remember(request) { mutableStateOf<Job?>(null) }

    val siteName = CrossSiteNavigator.siteLabel(request.site)
    val isTor = request.site == SiteType.ONAIR

    fun startValidation() {
        validationJob?.cancel()
        phase = CrossSitePhase.Validating
        validationJob = scope.launch {
            when (val result = CrossSiteNavigator.validate(request.site)) {
                CrossSiteNavigator.Validation.Ok -> onValidated()
                is CrossSiteNavigator.Validation.Failed ->
                    phase = CrossSitePhase.Failed(result.title, result.message)
            }
        }
    }

    fun cancel() {
        validationJob?.cancel()
        onDismiss()
    }

    when (val current = phase) {
        CrossSitePhase.Confirm -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text("Switch to $siteName?") },
            text = {
                val name = request.title.ifBlank { request.videoCode }
                Text(
                    "\"$name\" belongs to $siteName. " +
                            if (isTor) {
                                "The app will check your Tor connection, switch site and open the video page."
                            } else {
                                "The app will check the connection, switch site and open the video page."
                            }
                )
            },
            confirmButton = {
                TextButton(onClick = ::startValidation) { Text("Switch & open") }
            },
            dismissButton = {
                TextButton(onClick = ::cancel) { Text("Cancel") }
            },
        )

        CrossSitePhase.Validating -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text(if (isTor) "Checking Tor connection" else "Checking connection") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        if (isTor) "Verifying that this device exits through Tor…"
                        else "Verifying that $siteName can be reached…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = ::cancel) { Text("Cancel") }
            },
        )

        is CrossSitePhase.Failed -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text(current.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.message, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = ::startValidation) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = ::cancel) { Text("Cancel") }
            },
        )
    }
}
