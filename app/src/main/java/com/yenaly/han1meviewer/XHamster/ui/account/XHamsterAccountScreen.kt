package com.yenaly.han1meviewer.XHamster.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterAuth
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import kotlinx.coroutines.launch

@Composable
fun XHamsterAccountScreen(
    onBack: () -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(XHamsterAuth.cachedUser()) }
    var username by rememberSaveable { mutableStateOf("") }

    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (user != null) user = XHamsterAuth.refresh()
    }

    var playlists by remember { mutableStateOf<List<XHamsterLink>>(emptyList()) }
    LaunchedEffect(user?.id) {
        playlists = if (user != null) {
            XHamsterRepo.playlists().getOrDefault(emptyList())
        } else emptyList()
    }

    val subscriptionsTitle = stringResource(R.string.xhamster_my_subscriptions)

    fun submit() {
        if (busy || username.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            XHamsterAuth.login(username.trim(), password)
                .onSuccess {
                    user = it
                    password = ""
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.xhamster_account_title)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = user
            if (current != null) {
                Text(
                    text = stringResource(R.string.xhamster_logged_in_as, current.name),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (playlists.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.xhamster_my_favorites),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    playlists.forEach { playlist ->
                        Button(
                            onClick = { onOpenPath(playlist.url, playlist.title) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(playlist.title) }
                    }
                }
                Button(
                    onClick = { onOpenPath("/my/subscriptions", subscriptionsTitle) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(subscriptionsTitle) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            XHamsterAuth.logout()
                            user = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.xhamster_logout)) }
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.xhamster_username)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.xhamster_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { submit() },
                        enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                    ) { Text(stringResource(R.string.xhamster_login)) }
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                Text(
                    text = stringResource(R.string.xhamster_login_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
