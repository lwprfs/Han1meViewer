package com.yenaly.han1meviewer.OnAir.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val OnAirAccent = Color(0xFF00897B)

@Composable
fun OnAirGateScreen(
    state: OnAirGateState,
    onSwitchSite: () -> Unit,
    onOpenCookies: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val showProgress = state is OnAirGateState.Initializing ||
            state is OnAirGateState.CheckingTor ||
            state is OnAirGateState.Probing
    val headline = when (state) {
        OnAirGateState.Initializing -> "Getting ready"
        OnAirGateState.CheckingTor -> "Checking Tor"
        OnAirGateState.Probing -> "Validating session"
        OnAirGateState.Passed -> ""
        is OnAirGateState.TorBlocked -> "Tor connection required"
        is OnAirGateState.ContentError -> "OnAir is not ready"
    }
    val subtitle = when (state) {
        OnAirGateState.Initializing -> "Preparing the OnAir workspace…"
        OnAirGateState.CheckingTor -> "Making sure your traffic goes through Tor…"
        OnAirGateState.Probing -> "Confirming your OnAir session is still valid…"
        OnAirGateState.Passed -> ""
        is OnAirGateState.TorBlocked -> "OnAir only works over the Tor network."
        is OnAirGateState.ContentError -> "We could not load the site right now."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GateBadge(
                icon = if (showProgress) null else Icons.Default.Warning,
                tint = if (state is OnAirGateState.TorBlocked) {
                    MaterialTheme.colorScheme.error
                } else if (state is OnAirGateState.ContentError) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    OnAirAccent
                },
            )
            Text(
                text = "OnAir",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = OnAirAccent,
            )
            if (headline.isNotBlank()) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (showProgress) {
                Spacer(Modifier.height(6.dp))
                StepCard(
                    torDone = state is OnAirGateState.Probing,
                    torActive = state is OnAirGateState.CheckingTor,
                    sessionActive = state is OnAirGateState.Probing,
                )
            }

            when (state) {
                is OnAirGateState.TorBlocked -> {
                    ReasonCard(
                        title = "Tor not detected",
                        reason = state.reason,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        ButtonLabel(Icons.Default.Refresh, "Retry")
                    }
                    SecondaryActions(
                        onSwitchSite = onSwitchSite,
                        onOpenCookies = onOpenCookies,
                        onOpenSettings = onOpenSettings,
                        cookiesLabel = "Cookies",
                    )
                }

                is OnAirGateState.ContentError -> {
                    ReasonCard(
                        title = "Cannot load OnAir",
                        reason = state.reason,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        ButtonLabel(
                            Icons.Default.Refresh,
                            if (state.suggestWait) "Retry later" else "Retry",
                        )
                    }
                    SecondaryActions(
                        onSwitchSite = if (state.suggestSwitchSite) onSwitchSite else null,
                        onOpenCookies = onOpenCookies,
                        onOpenSettings = onOpenSettings,
                        cookiesLabel = if (state.suggestRelogin) "Sign in / cookies" else "Cookies",
                    )
                }

                OnAirGateState.Initializing,
                OnAirGateState.CheckingTor,
                OnAirGateState.Probing,
                OnAirGateState.Passed -> Unit
            }
        }
    }
}

@Composable
private fun GateBadge(icon: ImageVector?, tint: Color) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 4.dp,
                color = tint,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun StepCard(torDone: Boolean, torActive: Boolean, sessionActive: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepRow("Tor connection", done = torDone, active = torActive)
            StepRow("Session check", done = false, active = sessionActive)
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = OnAirAccent,
                    modifier = Modifier.size(22.dp),
                )
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = OnAirAccent,
                )
                else -> Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active || done) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ReasonCard(
    title: String,
    reason: String,
    container: Color,
    content: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = content,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
        }
    }
}

@Composable
private fun ButtonLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SecondaryActions(
    onSwitchSite: (() -> Unit)?,
    onOpenCookies: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    cookiesLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onOpenCookies,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
        ) {
            ButtonLabel(Icons.Default.Cookie, cookiesLabel)
        }
        if (onSwitchSite != null) {
            OutlinedButton(
                onClick = onSwitchSite,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                ButtonLabel(Icons.Default.SwapHoriz, "Switch site")
            }
        }
    }
    onOpenSettings?.let { openSettings ->
        TextButton(onClick = openSettings) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("App settings")
        }
    }
}
