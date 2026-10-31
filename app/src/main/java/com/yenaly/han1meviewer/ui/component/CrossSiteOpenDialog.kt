package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.yenaly.han1meviewer.R
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

    val dialogShape = RoundedCornerShape(28.dp)
    val dialogColor = MaterialTheme.colorScheme.surfaceContainerHigh

    when (val current = phase) {
        CrossSitePhase.Confirm -> AlertDialog(
            onDismissRequest = ::cancel,
            containerColor = dialogColor,
            shape = dialogShape,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_switch_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    text = "Switch to $siteName?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                val name = request.title.ifBlank { request.videoCode }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    Text(
                        text = "This video belongs to $siteName. " +
                                if (isTor) {
                                    "The app will check your Tor connection, switch site and open the video page."
                                } else {
                                    "The app will check the connection, switch site and open the video page."
                                },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = ::startValidation, shape = RoundedCornerShape(14.dp)) {
                    Text("Switch & open", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = ::cancel) { Text("Cancel") }
            },
        )

        CrossSitePhase.Validating -> AlertDialog(
            onDismissRequest = ::cancel,
            containerColor = dialogColor,
            shape = dialogShape,
            title = {
                Text(
                    text = if (isTor) "Checking Tor connection" else "Checking connection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = dialogColor,
            shape = dialogShape,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = ::startValidation, shape = RoundedCornerShape(14.dp)) {
                    Text("Retry", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = ::cancel) { Text("Cancel") }
            },
        )
    }
}
