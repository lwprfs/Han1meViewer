package com.yenaly.han1meviewer.FapFolder.ui.settings

import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import androidx.compose.foundation.background
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderCookieManager
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderProfileCache
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import com.yenaly.han1meviewer.FapFolder.ui.gate.FapFolderGateState
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderCookiesScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit = {},
    onSwitchSite: () -> Unit = {},
    onCookiesSaved: () -> Unit = onBack,
    onLoggedOut: (() -> Unit)? = null,
    gateState: FapFolderGateState? = null,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val scope = rememberCoroutineScope()

    var cookieInput by remember { mutableStateOf(FapFolderCookieManager.cookiesRaw) }
    var isSaving by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }

    val hasCookies = FapFolderCookieManager.hasCookies
    var profile by remember { mutableStateOf<FapFolderUserProfile?>(FapFolderProfileCache.current()) }
    var profileLoading by remember { mutableIntStateOf(0) }

    fun performLogout() {
        loggingOut = true
        scope.launch {
            FapFolderCookieManager.clearCookies()
            FapFolderProfileCache.clearAll()
            loggingOut = false
            showLogoutDialog = false
            profile = null
            GlobalToasts.show(applicationContext.getString(R.string.site_logged_out), level = GlobalToasts.ToastLevel.SUCCESS)
            onLoggedOut?.invoke()
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.site_cookies)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = { profile = FapFolderProfileCache.current() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.site_refresh))
                    }
                    IconButton(onClick = onSwitchSite) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.site_switch_site))
                    }
                    if (hasCookies) {
                        TextButton(onClick = {
                            clipboardScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("", FapFolderCookieManager.cookiesRaw))
                )
            }
                            GlobalToasts.show(
                                applicationContext.getString(R.string.cookies_copied_toast),
                                level = GlobalToasts.ToastLevel.SUCCESS,
                            )
                        }) { Text(stringResource(R.string.copy_)) }
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
                profileLoading = profileLoading,
                onRefresh = { profile = FapFolderProfileCache.current() },
                onLogout = { showLogoutDialog = true },
                onOpenLogin = onOpenLogin,
                onOpenRegister = onOpenRegister,
            )

            if (gateState is FapFolderGateState.ContentError) {
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
                            text = stringResource(R.string.fap_fapfolder_needs_attention),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = gateState.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = onCookiesSaved) { Text(stringResource(R.string.retry)) }
                            if (gateState.suggestSwitchSite) {
                                Button(onClick = onSwitchSite) { Text(stringResource(R.string.site_switch_site)) }
                            }
                            if (gateState.suggestRelogin) {
                                Button(onClick = onOpenLogin) { Text(stringResource(R.string.site_sign_in)) }
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.site_supported_formats),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.site_json_exported_from_browser_dev_tools),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val format = FapFolderCookieManager.detectFormat(cookieInput)
                    Text(
                        text = stringResource(R.string.site_detected_format, format.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OutlinedTextField(
                value = cookieInput,
                onValueChange = { cookieInput = it },
                label = { Text(stringResource(R.string.site_cookies)) },
                placeholder = { Text(stringResource(R.string.site_paste_cookies_here)) },
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
                            val result = FapFolderCookieManager.validate(cookieInput)
                            isSaving = false
                            if (result.valid) {
                                FapFolderCookieManager.saveCookies(cookieInput)
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
                                .size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(stringResource(R.string.site_validate_save))
                }
                TextButton(
                    onClick = { showClearDialog = true },
                    enabled = hasCookies,
                ) { Text(stringResource(R.string.site_clear)) }
            }
        }
    }

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { showSavedDialog = false },
            title = { Text(stringResource(R.string.site_cookies_saved)) },
            text = {
                Text(stringResource(R.string.fap_cookies_have_been_applied_fapfolder_wi))
            },
            confirmButton = {
                TextButton(onClick = {
                    showSavedDialog = false
                    onCookiesSaved()
                }) { Text(stringResource(R.string.site_ok)) }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.site_clear_cookies)) },
            text = {
                Text(stringResource(R.string.fap_this_will_remove_the_saved_fapfolder))
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    FapFolderCookieManager.clearCookies()
                    FapFolderProfileCache.clearAll()
                    profile = null
                    cookieInput = ""
                    GlobalToasts.show(applicationContext.getString(R.string.cookies_cleared_toast), level = GlobalToasts.ToastLevel.INFO)
                }) {
                    Text(stringResource(R.string.site_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!loggingOut) showLogoutDialog = false },
            title = { Text(stringResource(R.string.fap_log_out_of_fapfolder)) },
            text = { Text(stringResource(R.string.site_your_session_cookies_will_be_removed)) },
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
                        Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !loggingOut,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SignedInCard(
    hasCookies: Boolean,
    profile: FapFolderUserProfile?,
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
                            contentDescription = profile.displayName,
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
                            text = profile?.displayName
                                ?: if (hasCookies) stringResource(R.string.site_signed_in) else stringResource(R.string.site_not_signed_in),
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
                            stringResource(R.string.cookies_session_active_device)
                        } else {
                            stringResource(R.string.cookies_session_none_device)
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
                        Text(stringResource(R.string.site_refresh))
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
                        Text(stringResource(R.string.logout))
                    }
                } else {
                    Button(
                        onClick = onOpenLogin,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.site_log_in)) }
                    OutlinedButton(
                        onClick = onOpenRegister,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.site_create_account)) }
                }
            }
        }
    }
}
