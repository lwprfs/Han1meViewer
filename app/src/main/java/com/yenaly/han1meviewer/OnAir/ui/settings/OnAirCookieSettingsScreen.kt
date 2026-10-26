package com.yenaly.han1meviewer.OnAir.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.OnAir.data.local.OnAirProfileCache
import com.yenaly.han1meviewer.OnAir.data.model.OnAirUserProfile
import com.yenaly.han1meviewer.OnAir.data.remote.CookieFormat
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirAuthRepo
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirCookieManager
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirTorChecker
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirTorStatus
import com.yenaly.han1meviewer.OnAir.ui.gate.OnAirGateState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TorBubbleState {
    Checking,
    Passed,
    Failed,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirCookieSettingsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onSwitchSite: () -> Unit = {},
    onCookiesSaved: () -> Unit = onBack,
    onLoggedOut: (() -> Unit)? = null,
    gateState: OnAirGateState? = null,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var cookieInput by remember { mutableStateOf(OnAirCookieManager.cookiesRaw) }
    var isSaving by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }

    var torState by remember { mutableStateOf(TorBubbleState.Checking) }
    var torStatus by remember { mutableStateOf<OnAirTorStatus?>(null) }
    var reloadProgress by remember { mutableFloatStateOf(0f) }
    var showProgressBar by remember { mutableStateOf(false) }

    val hasCookies = OnAirCookieManager.hasCookies
    var profile by remember { mutableStateOf<OnAirUserProfile?>(OnAirProfileCache.current()) }
    var profileLoading by remember { mutableIntStateOf(0) }
    val usernameHint = remember(hasCookies) { OnAirAuthRepo.currentUsernameHint() }

    val detectedFormat = remember(cookieInput) { OnAirCookieManager.detectFormat(cookieInput) }
    val formatLabel = when (detectedFormat) {
        CookieFormat.JSON -> "JSON"
        CookieFormat.NETSCAPE -> "Netscape"
        CookieFormat.RAW_STRING -> "Raw Cookie String"
        CookieFormat.UNKNOWN -> "Unknown"
    }

    fun refreshProfile() {
        val name = profile?.username
            ?: OnAirProfileCache.getCurrentUsername()
            ?: usernameHint
            ?: return
        profileLoading += 1
        scope.launch {
            OnAirNetworkRepo.getUserProfile(name).collect { s ->
                when (s) {
                    is VideoLoadingState.Success -> {
                        profile = s.info
                        OnAirProfileCache.put(s.info.username, s.info)
                    }
                    else -> Unit
                }
                profileLoading = (profileLoading - 1).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(hasCookies) {
        if (hasCookies && profile == null) refreshProfile()
    }

    fun runTorAndReload() {
        scope.launch {
            torState = TorBubbleState.Checking
            showProgressBar = false
            reloadProgress = 0f
            val status = OnAirTorChecker.check()
            torStatus = status
            if (status.isTorExit) {
                torState = TorBubbleState.Passed
                delay(1_000L)
                showProgressBar = true
                reloadProgress = 0f
                runCatching {
                    OnAirNetwork.rebuildNetwork()
                    val url = OnAirNetwork.baseUrl.trimEnd('/') + "/"
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .header("Accept", "text/html")
                        .build()
                    val call = OnAirNetwork.sharedOkHttpClient.newCall(req)
                    val total = 20
                    for (i in 1..total) {
                        reloadProgress = i.toFloat() / total
                        delay(80L)
                        if (i == total) {
                            runCatching {
                                call.execute().use { resp ->
                                    resp.body?.string()
                                }
                            }
                        }
                    }
                }
                reloadProgress = 1f
                delay(400L)
                showProgressBar = false
                reloadProgress = 0f
            } else {
                torState = TorBubbleState.Failed
                showProgressBar = false
                reloadProgress = 0f
            }
        }
    }

    fun performLogout() {
        loggingOut = true
        scope.launch {
            val result = OnAirAuthRepo.performLogout()
            loggingOut = false
            showLogoutDialog = false
            OnAirProfileCache.clearAll()
            profile = null
            when (result) {
                is OnAirAuthRepo.AuthResult.Success -> {
                    GlobalToasts.show("Logged out", level = GlobalToasts.ToastLevel.SUCCESS)
                    onLoggedOut?.invoke()
                }
                is OnAirAuthRepo.AuthResult.FormError,
                is OnAirAuthRepo.AuthResult.NetworkError,
                is OnAirAuthRepo.AuthResult.LoginRequired -> {
                    GlobalToasts.show(
                        "Session cleared locally",
                        level = GlobalToasts.ToastLevel.WARNING,
                    )
                    onLoggedOut?.invoke()
                }
            }
        }
    }

    LaunchedEffect(Unit) { runTorAndReload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cookies") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { runTorAndReload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recheck Tor and reload")
                    }
                    IconButton(onClick = onSwitchSite) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch site")
                    }
                    if (hasCookies) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(OnAirCookieManager.cookiesRaw))
                            GlobalToasts.show(
                                "Cookies copied",
                                level = GlobalToasts.ToastLevel.SUCCESS,
                            )
                        }) { Text("Copy") }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SignedInCard(
                hasCookies = hasCookies,
                profile = profile,
                usernameHint = usernameHint,
                profileLoading = profileLoading,
                onRefresh = { refreshProfile() },
                onLogout = { showLogoutDialog = true },
                onOpenLogin = onOpenLogin,
                onOpenRegister = onOpenRegister,
            )

            when (val gs = gateState) {
                is OnAirGateState.ContentError -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "OnAir needs attention",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = gs.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = onCookiesSaved) {
                                    Text("Retry")
                                }
                                if (gs.suggestSwitchSite) {
                                    Button(onClick = onSwitchSite) {
                                        Icon(
                                            Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Switch site")
                                    }
                                }
                                if (gs.suggestRelogin) {
                                    Button(onClick = onOpenLogin) {
                                        Text("Sign in")
                                    }
                                }
                            }
                        }
                    }
                }

                is OnAirGateState.TorBlocked -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Tor is not connected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = gs.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                else -> Unit
            }

            TorStatusBlock(
                torState = torState,
                torStatus = torStatus,
                showProgressBar = showProgressBar,
                reloadProgress = reloadProgress,
                onClick = { runTorAndReload() },
            )

            val mirrorStatusVm: OnAirMirrorStatusViewModel = viewModel()
            OnAirMirrorStatusCard(
                viewModel = mirrorStatusVm,
                onSwitchSite = onSwitchSite,
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Supported formats",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• JSON (exported from browser dev tools)\n" +
                                "• Netscape (cookies.txt)\n" +
                                "• Raw string (name=value; name2=value2)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Detected format: $formatLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OutlinedTextField(
                value = cookieInput,
                onValueChange = { cookieInput = it },
                label = { Text("Cookies") },
                placeholder = { Text("Paste cookies here…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                singleLine = false,
                maxLines = 20,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val result = OnAirCookieManager.validate(cookieInput)
                            isSaving = false
                            if (result.valid) {
                                OnAirCookieManager.saveCookies(cookieInput)
                                showSavedDialog = true
                            } else {
                                GlobalToasts.show(
                                    result.message,
                                    level = GlobalToasts.ToastLevel.ERROR,
                                )
                            }
                        }
                    },
                    enabled = cookieInput.isNotBlank() && !isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text("Validate & Save")
                }
                TextButton(
                    onClick = { showClearDialog = true },
                    enabled = hasCookies,
                ) { Text("Clear") }
            }
        }
    }

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { showSavedDialog = false },
            title = { Text("Cookies saved") },
            text = { Text("Cookies have been applied. OnAir will now load using your session.") },
            confirmButton = {
                TextButton(onClick = {
                    showSavedDialog = false
                    onCookiesSaved()
                }) { Text("OK") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear cookies") },
            text = { Text("This will remove the saved OnAir cookies. You will need to log in again.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    OnAirCookieManager.clearCookies()
                    OnAirProfileCache.clearAll()
                    profile = null
                    cookieInput = ""
                    GlobalToasts.show("Cookies cleared", level = GlobalToasts.ToastLevel.INFO)
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!loggingOut) showLogoutDialog = false },
            title = { Text("Log out of OnAir?") },
            text = { Text("Your session cookies will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = { performLogout() },
                    enabled = !loggingOut,
                ) {
                    if (loggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Log out", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !loggingOut,
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SignedInCard(
    hasCookies: Boolean,
    profile: OnAirUserProfile?,
    usernameHint: String?,
    profileLoading: Int,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasCookies) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatar = profile?.avatarUrl
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = profile?.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = if (hasCookies) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val label = profile?.displayName?.firstOrNull()?.uppercase()
                                    ?: usernameHint?.firstOrNull()?.uppercase()
                                    ?: if (hasCookies) "?" else "×"
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (hasCookies) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                profile != null -> profile.displayName
                                !usernameHint.isNullOrBlank() -> usernameHint
                                hasCookies -> "Signed in"
                                else -> "Not signed in"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (profileLoading > 0) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    Text(
                        text = if (hasCookies) {
                            "Session cookies active on this device"
                        } else {
                            "No session cookies on this device"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasCookies) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasCookies) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh profile")
                    }
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Log out")
                    }
                } else {
                    Button(
                        onClick = onOpenLogin,
                        modifier = Modifier.weight(1f),
                    ) { Text("Log in") }
                    OutlinedButton(
                        onClick = onOpenRegister,
                        modifier = Modifier.weight(1f),
                    ) { Text("Create account") }
                }
            }
        }
    }
}

@Composable
private fun TorStatusBlock(
    torState: TorBubbleState,
    torStatus: OnAirTorStatus?,
    showProgressBar: Boolean,
    reloadProgress: Float,
    onClick: () -> Unit,
) {
    val targetBubbleColor = when (torState) {
        TorBubbleState.Checking -> MaterialTheme.colorScheme.primary
        TorBubbleState.Passed -> Color(0xFF2E7D32)
        TorBubbleState.Failed -> MaterialTheme.colorScheme.error
    }
    val bubbleColor by animateColorAsState(
        targetValue = targetBubbleColor,
        label = "tor_bubble_color",
    )

    val infinite = rememberInfiniteTransition(label = "tor_bubble_pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tor_bubble_scale",
    )

    val bubbleScale = if (torState == TorBubbleState.Checking) pulseScale else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                        }
                        .clip(CircleShape)
                        .background(bubbleColor),
                )
            }
            Spacer(Modifier.width(8.dp))
            val label = when (torState) {
                TorBubbleState.Checking -> "Checking Tor…"
                TorBubbleState.Passed -> "Tor connected" +
                        (torStatus?.ip?.let { " · $it" } ?: "")
                TorBubbleState.Failed -> "Tor not detected — tap to retry"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }

        if (showProgressBar) {
            LinearProgressIndicator(
                progress = reloadProgress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                trackColor = Color.Transparent,
            )
        } else if (torState == TorBubbleState.Failed) {
            Text(
                text = "You must be connected through Tor to load OnAir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
