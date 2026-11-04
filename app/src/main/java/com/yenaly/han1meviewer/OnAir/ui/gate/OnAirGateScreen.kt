package com.yenaly.han1meviewer.OnAir.ui.gate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        OnAirGateState.Initializing -> stringResource(R.string.onair_getting_ready)
        OnAirGateState.CheckingTor -> stringResource(R.string.onair_checking_tor)
        OnAirGateState.Probing -> stringResource(R.string.onair_validating_session)
        OnAirGateState.Passed -> ""
        is OnAirGateState.TorBlocked -> "Tor connection required"
        is OnAirGateState.ContentError -> stringResource(R.string.onair_onair_is_not_ready)
    }
    val subtitle = when (state) {
        OnAirGateState.Initializing -> stringResource(R.string.onair_preparing_the_onair_workspace)
        OnAirGateState.CheckingTor -> stringResource(R.string.onair_making_sure_your_traffic_goes_through)
        OnAirGateState.Probing -> stringResource(R.string.onair_confirming_your_onair_session_is_still)
        OnAirGateState.Passed -> ""
        is OnAirGateState.TorBlocked -> stringResource(R.string.onair_onair_only_works_over_the_tor)
        is OnAirGateState.ContentError -> stringResource(R.string.onair_we_could_not_load_the_site)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            if (showProgress) {
                LoadingIndicator()
            } else {
                Image(
                    modifier = Modifier.size(150.dp),
                    painter = painterResource(R.drawable.h_chan_sad),
                    contentDescription = headline,
                )
            }
            Text(
                text = "OnAir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (headline.isNotBlank()) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (showProgress) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            if (showProgress) {
                StepCard(
                    torDone = state is OnAirGateState.Probing,
                    torActive = state is OnAirGateState.CheckingTor,
                    sessionActive = state is OnAirGateState.Probing,
                )
            }

            when (state) {
                is OnAirGateState.TorBlocked -> {
                    ReasonCard(
                        title = stringResource(R.string.onair_tor_not_detected),
                        reason = state.reason,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonLabel(Icons.Default.Refresh, stringResource(R.string.retry))
                    }
                    SecondaryActions(
                        onSwitchSite = onSwitchSite,
                        onOpenCookies = onOpenCookies,
                        onOpenSettings = onOpenSettings,
                        cookiesLabel = stringResource(R.string.site_cookies),
                    )
                }

                is OnAirGateState.ContentError -> {
                    ReasonCard(
                        title = stringResource(R.string.onair_cannot_load_onair),
                        reason = state.reason,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonLabel(
                            Icons.Default.Refresh,
                            if (state.suggestWait) stringResource(R.string.site_retry_later) else stringResource(R.string.retry),
                        )
                    }
                    SecondaryActions(
                        onSwitchSite = if (state.suggestSwitchSite) onSwitchSite else null,
                        onOpenCookies = onOpenCookies,
                        onOpenSettings = onOpenSettings,
                        cookiesLabel = if (state.suggestRelogin) "Sign in / cookies" else stringResource(R.string.site_cookies),
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
private fun StepCard(torDone: Boolean, torActive: Boolean, sessionActive: Boolean) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(SpacingLarge),
            verticalArrangement = Arrangement.spacedBy(SpacingNormal),
        ) {
            StepRow(stringResource(R.string.onair_tor_connection), done = torDone, active = torActive)
            StepRow(stringResource(R.string.onair_session_check), done = false, active = sessionActive)
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
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
            style = if (active) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
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
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(SpacingLarge),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
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
    Spacer(Modifier.width(SpacingNormal))
    Text(text, maxLines = 1)
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
        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
    ) {
        FilledTonalButton(
            onClick = onOpenCookies,
            modifier = Modifier.weight(1f),
        ) {
            ButtonLabel(Icons.Default.Cookie, cookiesLabel)
        }
        if (onSwitchSite != null) {
            OutlinedButton(
                onClick = onSwitchSite,
                modifier = Modifier.weight(1f),
            ) {
                ButtonLabel(Icons.Default.SwapHoriz, stringResource(R.string.site_switch_site))
            }
        }
    }
    onOpenSettings?.let { openSettings ->
        TextButton(onClick = openSettings) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(SpacingNormal))
            Text(stringResource(R.string.onair_app_settings))
        }
    }
}
