package com.yenaly.han1meviewer.OnAir.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirAuthRepo
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.launch

private val usernamePattern = Regex("^[A-Za-z0-9_]{3,20}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirRegisterScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var challenge by remember { mutableStateOf<OnAirAuthRepo.CaptchaChallenge?>(null) }
    var captchaLoading by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var captchaNonce by remember { mutableStateOf(0L) }
    var preserveErrorOnRefresh by remember { mutableStateOf(false) }

    fun refreshChallenge() {
        captchaLoading = true
        challenge = null
        if (!preserveErrorOnRefresh) {
            errorMessage = null
        }
        preserveErrorOnRefresh = false
        captchaAnswer = ""
        scope.launch {
            try {
                OnAirAuthRepo.fetchRegisterCaptcha().collect { ch ->
                    challenge = ch
                    captchaLoading = false
                }
            } catch (e: Exception) {
                captchaLoading = false
                errorMessage = e.message ?: "Failed to load captcha"
            }
        }
    }

    LaunchedEffect(captchaNonce) { refreshChallenge() }

    val usernameError = when {
        username.isNotBlank() && !usernamePattern.matches(username) ->
            "3-20 letters, numbers or underscore"
        else -> null
    }
    val passwordError = when {
        confirm.isNotBlank() && password != confirm -> "Passwords do not match"
        password.isNotBlank() && password.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }
    val canSubmit = !submitting && !captchaLoading && challenge != null &&
            username.isNotBlank() && usernameError == null &&
            password.isNotBlank() && passwordError == null &&
            confirm.isNotBlank() && captchaAnswer.isNotBlank()

    fun submit() {
        if (!canSubmit) return
        scope.launch {
            submitting = true
            errorMessage = null
            OnAirAuthRepo.register(
                challenge = challenge!!,
                username = username.trim(),
                password = password,
                confirm = confirm,
                captchaAnswer = captchaAnswer,
            ).collect { result ->
                submitting = false
                when (result) {
                    is OnAirAuthRepo.AuthResult.Success -> {
                        GlobalToasts.show(
                            "Account created — you are logged in",
                            level = GlobalToasts.ToastLevel.SUCCESS,
                        )
                        onSuccess()
                    }
                    is OnAirAuthRepo.AuthResult.LoginRequired -> {
                        errorMessage = "Your session was rejected. Try again."
                        preserveErrorOnRefresh = true
                        captchaNonce = System.currentTimeMillis()
                    }
                    is OnAirAuthRepo.AuthResult.FormError -> {
                        errorMessage = result.message
                        preserveErrorOnRefresh = true
                        captchaNonce = System.currentTimeMillis()
                    }
                    is OnAirAuthRepo.AuthResult.NetworkError -> {
                        errorMessage = result.throwable.message ?: "Network error"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign up for OnAir") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text(
                text = "Create a new OnAir account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !submitting,
                isError = usernameError != null,
                supportingText = usernameError?.let { { Text(it) } },
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !submitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirm password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !submitting,
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Quick check — enter the equation result shown below",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { captchaNonce = System.currentTimeMillis() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh captcha")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (captchaLoading || challenge == null) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    RetryableImage(
                        model = "${challenge!!.captchaImageUrl}?v=$captchaNonce",
                        contentDescription = "Captcha",
                        modifier = Modifier
                            .heightIn(max = 120.dp)
                            .fillMaxWidth(),
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            OutlinedTextField(
                value = captchaAnswer,
                onValueChange = { captchaAnswer = it.filter(Char::isDigit) },
                label = { Text("Captcha answer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = ::submit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Create account")
            }

            TextButton(
                onClick = onOpenLogin,
                enabled = !submitting,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Already have an account? Log in")
            }
        }
    }
}
