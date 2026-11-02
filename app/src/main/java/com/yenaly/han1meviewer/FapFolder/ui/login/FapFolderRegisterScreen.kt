package com.yenaly.han1meviewer.FapFolder.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAccountRepo
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderRegisterScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var email2 by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    var siteKey by remember { mutableStateOf<String?>(null) }
    var captchaToken by remember { mutableStateOf<String?>(null) }
    var widgetNonce by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        siteKey = FapFolderAccountRepo.fetchSignupSitekey()
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun submit() {
        if (isSubmitting) return
        val validation = when {
            username.isBlank() -> "Enter a username."
            !Regex("^[A-Za-z0-9.]+$").matches(username.trim()) ->
                "Username can only contain letters, numbers and periods."
            email.isBlank() || !email.contains('@') -> "Enter a valid email address."
            !email.trim().equals(email2.trim(), ignoreCase = true) ->
                "The two email addresses do not match."
            password.length < 6 -> "Password must be at least 6 characters."
            !agree -> "You must confirm you are 18+ and accept the Terms & Rules."
            captchaToken.isNullOrBlank() -> "Complete the security check first."
            else -> null
        }
        if (validation != null) {
            errorMessage = validation
            return
        }
        errorMessage = null
        infoMessage = null
        isSubmitting = true
        keyboard?.hide()

        scope.launch {
            val result = FapFolderAccountRepo.register(
                username = username,
                email = email,
                email2 = email2,
                password = password,
                captchaToken = captchaToken.orEmpty(),
            )
            isSubmitting = false
            when (result) {
                is FapFolderAccountRepo.RegisterResult.LoggedIn -> {
                    GlobalToasts.show(
                        "Account created",
                        level = GlobalToasts.ToastLevel.SUCCESS,
                    )
                    onRegistered()
                }
                is FapFolderAccountRepo.RegisterResult.Created -> infoMessage = result.message
                is FapFolderAccountRepo.RegisterResult.Error -> {
                    errorMessage = result.message

                    captchaToken = null
                    widgetNonce++
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("Create account") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
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
                        text = "Join FapFolder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Create a free fapfolder.club account. Use a real email " +
                                "address — you may need it to activate the account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("Username") },
                supportingText = { Text("Letters, numbers and periods only") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email (specify carefully)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = email2,
                onValueChange = { email2 = it; errorMessage = null },
                label = { Text("Repeat email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { agree = !agree },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = agree,
                    onCheckedChange = { agree = it },
                    enabled = !isSubmitting,
                )
                Text(
                    text = "I certify that I am 18 years of age or older, and agree to the " +
                            "Terms & Rules",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { openUrl(FapFolderConstants.BASE_URL + "static/terms") }) {
                    Text("Terms")
                }
                TextButton(onClick = { openUrl(FapFolderConstants.BASE_URL + "static/rules") }) {
                    Text("Rules")
                }
            }

            val sk = siteKey
            if (sk != null) {
                androidx.compose.runtime.key(widgetNonce) {
                    TurnstileWidget(
                        siteKey = sk,
                        onToken = { captchaToken = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp),
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp,
                )
            }

            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            infoMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
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
                    Text("Creating account…")
                } else {
                    Text("Sign up")
                }
            }

            TextButton(
                onClick = onOpenLogin,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            ) {
                Text("Already have an account? Sign in")
            }
            TextButton(
                onClick = { openUrl(FapFolderConstants.BASE_URL + FapFolderAccountRepo.PATH_SIGNUP) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open sign-up in browser instead")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileWidget(
    siteKey: String,
    onToken: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnToken by rememberUpdatedState(onToken)
    val html = remember(siteKey) {
        """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=onTsLoad&render=explicit" async defer></script>
        <style>html,body{margin:0;padding:0;background:transparent}body{display:flex;justify-content:center}</style>
        </head><body><div id="w"></div>
        <script>
        function onTsLoad(){
          turnstile.render('#w',{
            sitekey:'$siteKey',theme:'auto',
            callback:function(t){Android.onToken(t)},
            'expired-callback':function(){Android.onToken('')},
            'error-callback':function(){Android.onToken('')}
          });
        }
        </script></body></html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onToken(token: String) {
                            post { currentOnToken(token.takeIf { it.isNotBlank() }) }
                        }
                    },
                    "Android",
                )

                loadDataWithBaseURL(
                    FapFolderConstants.BASE_URL + "signup",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        onRelease = { it.destroy() },
    )
}
