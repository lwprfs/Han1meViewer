package com.yenaly.han1meviewer.FapFolder.ui.login

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAuthRepo
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAuthResult
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderLoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onOpenCookies: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (isSubmitting) return
        val user = username.trim()
        if (user.isEmpty() || password.isEmpty()) {
            errorMessage = "Username and password cannot be empty."
            return
        }
        errorMessage = null
        isSubmitting = true
        keyboard?.hide()

        scope.launch {
            FapFolderAuthRepo.login(
                usernameOrEmail = user,
                password = password,
                remember = remember,
            ).collect { result ->
                when (result) {
                    is FapFolderAuthResult.Success -> {
                        isSubmitting = false
                        onLoginSuccess()
                    }
                    is FapFolderAuthResult.FormError -> {
                        isSubmitting = false
                        errorMessage = result.message
                    }
                    is FapFolderAuthResult.InvalidCredentials -> {
                        isSubmitting = false
                        errorMessage = "Incorrect username or password."
                    }
                    is FapFolderAuthResult.CloudflareChallenge -> {
                        isSubmitting = false
                        errorMessage =
                            "Cloudflare challenge detected. Wait a moment and try again, " +
                                    "or paste fresh cookies."
                    }
                    is FapFolderAuthResult.SessionExpired -> {
                        isSubmitting = false
                        errorMessage = "Session expired. Try again or paste fresh cookies."
                    }
                    is FapFolderAuthResult.NetworkError -> {
                        isSubmitting = false
                        errorMessage = result.throwable.message
                            ?: "Could not reach FapFolder."
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
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
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Sign in to FapFolder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Log in with your fapfolder.club username or email. " +
                                "If login fails with a Cloudflare challenge, paste fresh " +
                                "cookies from a browser session instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("Username or email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { remember = !remember }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Checkbox(
                    checked = remember,
                    onCheckedChange = { remember = it },
                    enabled = !isSubmitting,
                )
                Text(
                    text = "Keep me signed in for 30 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { submit() },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Signing in…")
                } else {
                    Text("Sign in")
                }
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onOpenCookies,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            ) {
                Text("Paste cookies instead")
            }
        }
    }
}
