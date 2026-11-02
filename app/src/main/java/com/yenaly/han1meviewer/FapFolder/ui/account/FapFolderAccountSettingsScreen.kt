package com.yenaly.han1meviewer.FapFolder.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAccountRepo
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import kotlinx.coroutines.launch

/** settings → Account Settings (email, username, current password check). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderAccountSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<FapFolderAccountRepo.ActionResult?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        loadError = null
        FapFolderAccountRepo.fetchAccountSettings()
            .onSuccess {
                email = it.email
                username = it.username
            }
            .onFailure { loadError = it.message ?: "Could not load account settings." }
        loading = false
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("Account settings") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            loadError != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = loadError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { reloadTick++ }) { Text("Retry") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; message = null },
                    label = { Text("Email address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; message = null },
                    label = { Text("Username") },
                    supportingText = {
                        Text("Letters, numbers and periods only. Changing it changes your profile link.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = { Text("Current password") },
                    supportingText = { Text("Required as a security check") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (passwordVisible) "Hide" else "Show",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )

                when (val m = message) {
                    is FapFolderAccountRepo.ActionResult.Error -> Text(
                        text = m.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is FapFolderAccountRepo.ActionResult.Success -> Text(
                        text = m.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    null -> Unit
                }

                Button(
                    onClick = {
                        if (saving) return@Button
                        if (password.isEmpty()) {
                            message = FapFolderAccountRepo.ActionResult.Error(
                                "Enter your current password to save changes."
                            )
                            return@Button
                        }
                        saving = true
                        scope.launch {
                            val result = FapFolderAccountRepo.saveAccountSettings(
                                email = email,
                                username = username,
                                currentPassword = password,
                            )
                            saving = false
                            message = result
                            if (result is FapFolderAccountRepo.ActionResult.Success) {
                                password = ""
                                GlobalToasts.show(
                                    result.message,
                                    level = GlobalToasts.ToastLevel.SUCCESS,
                                )
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(if (saving) "Saving…" else "Save changes")
                }
            }
        }
    }
}

/** settings → Privacy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderPrivacySettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(FapFolderAccountRepo.PrivacySettings()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<FapFolderAccountRepo.ActionResult?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        loadError = null
        FapFolderAccountRepo.fetchPrivacy()
            .onSuccess { settings = it }
            .onFailure { loadError = it.message ?: "Could not load privacy settings." }
        loading = false
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("Privacy") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            loadError != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = loadError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { reloadTick++ }) { Text("Retry") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PrivacyChoice("Who can see your gender", settings.gender, !saving) {
                    settings = settings.copy(gender = it); message = null
                }
                PrivacyChoice("Who can see your birthdate", settings.birthdate, !saving) {
                    settings = settings.copy(birthdate = it); message = null
                }
                PrivacyChoice("Who can see your basic info", settings.basic, !saving) {
                    settings = settings.copy(basic = it); message = null
                }
                PrivacyChoice("Who can see your other info", settings.other, !saving) {
                    settings = settings.copy(other = it); message = null
                }
                PrivacyChoice("Who can see your photos", settings.photos, !saving) {
                    settings = settings.copy(photos = it); message = null
                }
                PrivacyChoice("Who can see your joined groups", settings.groups, !saving) {
                    settings = settings.copy(groups = it); message = null
                }

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Receive newsletter emails",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.newsletter,
                        onCheckedChange = {
                            settings = settings.copy(newsletter = it); message = null
                        },
                        enabled = !saving,
                    )
                }

                when (val m = message) {
                    is FapFolderAccountRepo.ActionResult.Error -> Text(
                        text = m.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is FapFolderAccountRepo.ActionResult.Success -> Text(
                        text = m.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    null -> Unit
                }

                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            val result = FapFolderAccountRepo.savePrivacy(settings)
                            saving = false
                            message = result
                            if (result is FapFolderAccountRepo.ActionResult.Success) {
                                GlobalToasts.show(
                                    result.message,
                                    level = GlobalToasts.ToastLevel.SUCCESS,
                                )
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(if (saving) "Saving…" else "Save changes")
                }
            }
        }
    }
}

private fun visibilityLabel(value: String): String = when (value) {
    FapFolderAccountRepo.VISIBILITY_FRIENDS -> "Friends"
    FapFolderAccountRepo.VISIBILITY_ME -> "Just me"
    else -> "Everyone"
}

@Composable
private fun PrivacyChoice(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                Text(visibilityLabel(value))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(
                    FapFolderAccountRepo.VISIBILITY_PUBLIC,
                    FapFolderAccountRepo.VISIBILITY_FRIENDS,
                    FapFolderAccountRepo.VISIBILITY_ME,
                ).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(visibilityLabel(option)) },
                        onClick = {
                            expanded = false
                            onChange(option)
                        },
                    )
                }
            }
        }
    }
}
