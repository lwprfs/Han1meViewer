package com.yenaly.han1meviewer.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.yenaly.han1meviewer.HanimeConstants
import com.yenaly.han1meviewer.PermissionRequester
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logout
import com.yenaly.han1meviewer.logic.network.HanimeNetwork
import com.yenaly.han1meviewer.ui.bridge.VideoPageHost
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.main.AccountRoute
import com.yenaly.han1meviewer.ui.navigation.main.UnifiedMainNavHost
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.han1meviewer.ui.screen.home.homepage.HomePageViewModel
import com.yenaly.han1meviewer.ui.screen.main.MainActivityContent
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.videoUrlRegex
import com.yenaly.yenaly_libs.base.frame.FrameActivity
import com.yenaly.yenaly_libs.utils.showSnackBar
import com.yenaly.yenaly_libs.utils.textFromClipboard
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

// MissAV imports
import com.yenaly.han1meviewer.MissAV.MissAvConstants
import com.yenaly.han1meviewer.MissAV.MissAvNetwork

// HentaiMama imports
import com.yenaly.han1meviewer.HentaiMama.HentaiMamaConstants
import com.yenaly.han1meviewer.HentaiMama.HentaiMamaNetwork

class MainActivity : FrameActivity(), PermissionRequester {

    val viewModel: HomePageViewModel by viewModels()

    lateinit var navController: NavHostController
    private var showAuthGuard by mutableStateOf(true)
    private val pendingNavigationRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    private var currentVideoHost: VideoPageHost? = null

    // Site switching state
    var showSiteSwitchConfirm by mutableStateOf(false)
        private set
    var showLogoutConfirm by mutableStateOf(false)
        private set
    private var logoutCloseCurrentPage = false

    // Site selection dialog state
    private var showSiteSelectDialog by mutableStateOf(false)

    // Force recomposition on site change
    private val _siteChanged = mutableStateOf(0L)

    // Site switch state for controlled navigation
    private val _siteSwitchState = MutableStateFlow<SiteType?>(null)
    private val siteSwitchState = _siteSwitchState.asStateFlow()

    companion object {
        private const val REQUEST_WRITE_EXTERNAL_STORAGE = 1234
        const val ACTION_TOGGLE_PLAY = "com.yenaly.han1meviewer.ACTION_TOGGLE_PLAY"
    }

    // Login data launcher
    private val loginDataLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.getHomePage()
            }
        }

    private var hasAuthenticated = false

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i("pipmode", "✅ onReceive called with action: ${intent?.action}")
            when (intent?.action) {
                ACTION_TOGGLE_PLAY -> {
                    Log.i("pipmode", "🎬 ACTION_TOGGLE_PLAY triggered")
                    togglePlayPause()
                }
            }
        }
    }

    private fun initData() {
        setContent {
            // Handle site switch navigation after recomposition
            LaunchedEffect(siteSwitchState, _siteChanged) {
                siteSwitchState.collect { siteType ->
                    siteType?.let {
                        // Longer delay to ensure NavHost recomposition completes
                        kotlinx.coroutines.delay(400)
                        Log.d("MainActivity", "LaunchedEffect: navigating to $it")

                        // First, clear everything
                        NavigationManager.clearBackStack()

                        // Then navigate to the appropriate home
                        NavigationManager.switchSite(it)

                        // Clear the state so we don't repeat
                        _siteSwitchState.value = null
                    }
                }
            }

            MainActivityContent(
                activity = this,
                viewModel = viewModel,
                pendingNavigationRequests = pendingNavigationRequests,
                showAuthGuard = showAuthGuard,
                onOpenAccount = { navController.navigateSafely(AccountRoute) },
                onLogoutClick = { requestLogout(false) },
                onRequireLogin = { gotoLoginActivity() },
                onSwitchSiteClick = { requestSiteSwitch() },
                onNavigateControllerReady = { controller ->
                    navController = controller
                    NavigationManager.initialize(controller, Preferences.siteType)
                    Log.d("MainActivity", "NavController initialized with site: ${Preferences.siteType}")
                },
                siteChangeKey = _siteChanged.value,
            )

            // Show site selection dialog if triggered
            if (showSiteSelectDialog) {
                SiteSelectionDialog(
                    onDismiss = { showSiteSelectDialog = false },
                    onSiteSelected = { siteType ->
                        switchToSite(siteType)
                        showSiteSelectDialog = false
                    }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen().apply {
                setKeepOnScreenCondition { !hasAuthenticated }
            }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useLock = prefs.getBoolean("use_lock_screen", false)

        if (useLock && isDeviceSecureCompat(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                authenticate(
                    this,
                    onSuccess = {
                        hasAuthenticated = true
                        showAuthGuard = false
                        initData()
                    },
                    onFailed = {
                        finish()
                    }
                )
            } else {
                GlobalToasts.show(getString(R.string.not_compact_lock_screen), level = GlobalToasts.ToastLevel.WARNING)
                hasAuthenticated = true
                showAuthGuard = false
                initData()
            }
        } else {
            hasAuthenticated = true
            showAuthGuard = false
            initData()
        }
        pendingNavigationRequests.tryEmit(intent)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyAppLocale(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigationRequests.tryEmit(intent)
    }

    // Site Switch Methods
    fun requestSiteSwitch() {
        showSiteSwitchConfirm = true
    }

    fun dismissSiteSwitch() {
        showSiteSwitchConfirm = false
    }

    fun confirmSiteSwitch() {
        showSiteSwitchConfirm = false
        showSiteSelectDialog = true
    }

    // Logout Methods
    fun requestLogout(closeCurrentPageOnConfirm: Boolean) {
        logoutCloseCurrentPage = closeCurrentPageOnConfirm
        showLogoutConfirm = true
    }

    fun dismissLogoutConfirm() {
        showLogoutConfirm = false
    }

    fun confirmLogout() {
        showLogoutConfirm = false
        if (logoutCloseCurrentPage) {
            navController.popBackStack()
        }
        logoutWithRefresh()
    }

    fun logoutWithRefresh() {
        logout()
        viewModel.getHomePage()
        NavigationManager.clearBackStack()
        NavigationManager.navigateToHome()
    }

    // Site Switch Logic - COMPLETE FIX
    private fun switchToSite(siteType: SiteType) {
        Log.d("MainActivity", "switchToSite called: $siteType")
        Log.d("MainActivity", "Current baseUrl before: ${Preferences.baseUrl}")

        // Save preference FIRST
        Preferences.siteType = siteType

        // Update base URL and network based on site type
        when (siteType) {
            SiteType.HANIME -> {
                // Hanime: Restore custom mirror site settings if they exist
                val savedCustomMirror = Preferences.preferenceSp.getString(
                    SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, ""
                ) ?: ""
                val savedUseCustomMirror = Preferences.preferenceSp.getBoolean(
                    SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false
                )

                // If the saved custom mirror is not empty and it's a Hanime mirror,
                // restore it. Otherwise, use default Hanime.
                if (savedCustomMirror.isNotBlank() &&
                    (savedCustomMirror.contains("hanime1.me") ||
                     savedCustomMirror.contains("hanime1.com") ||
                     savedUseCustomMirror)) {
                    Log.d("MainActivity", "Restoring custom mirror: $savedCustomMirror")
                    // Keep the custom mirror settings
                } else {
                    // Reset to default Hanime
                    Preferences.preferenceSp.edit(true) {
                        putString(SettingsPreferenceKeys.DOMAIN_NAME, HanimeConstants.HANIME_URL[0])
                        putString(SettingsPreferenceKeys.SELECTED_BASE_URL, HanimeConstants.HANIME_URL[0])
                        putString(SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, "")
                        putBoolean(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false)
                        putBoolean(SettingsPreferenceKeys.APPEND_CUSTOM_MIRROR_PATH, true)
                    }
                }
                HanimeNetwork.rebuildNetwork()
            }
            SiteType.JAVCHU -> {
                // Javchu: ALWAYS fixed URL, no custom mirror
                Preferences.preferenceSp.edit(true) {
                    putString(SettingsPreferenceKeys.DOMAIN_NAME, "https://javchu.com")
                    putString(SettingsPreferenceKeys.SELECTED_BASE_URL, "https://javchu.com")
                    putString(SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, "")
                    putBoolean(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false)
                    putBoolean(SettingsPreferenceKeys.APPEND_CUSTOM_MIRROR_PATH, true)
                }
                HanimeNetwork.rebuildNetwork()
            }
            SiteType.MISSAV -> {
                Preferences.preferenceSp.edit(true) {
                    putString("missav_base_url", MissAvConstants.MISSAV_URL[0])
                    putBoolean(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false)
                }
                MissAvNetwork.rebuildNetwork()
            }
            SiteType.HENTAIMAMA -> {
                Preferences.preferenceSp.edit(true) {
                    putString("hentaimama_base_url", HentaiMamaConstants.BASE_URL)
                    putBoolean(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false)
                }
                HentaiMamaNetwork.rebuildNetwork()
            }
        }

        Log.d("MainActivity", "New baseUrl after: ${Preferences.baseUrl}")
        Log.d("MainActivity", "New displayUrl: ${Preferences.displayUrl}")

        // Force recomposition of NavHost with a new key
        _siteChanged.value = System.currentTimeMillis()

        // Trigger navigation after recomposition via StateFlow
        _siteSwitchState.value = siteType

        // Show confirmation with the actual URL being used
        Handler(Looper.getMainLooper()).postDelayed({
            val urlDisplay = if (siteType == SiteType.JAVCHU) {
                "Javchu (Fixed: https://javchu.com)"
            } else {
                Preferences.displayUrl
            }
            GlobalToasts.show(
                getString(R.string.switched_to_site, urlDisplay),
                level = GlobalToasts.ToastLevel.SUCCESS
            )
        }, 600)
    }

    fun gotoLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        loginDataLauncher.launch(intent)
    }

    fun showVideoDetailFragment(videoCode: String, fileUri: String? = null) {
        NavigationManager.navigateToVideo(videoCode)
    }

    fun registerCurrentVideoHost(host: VideoPageHost?) {
        currentVideoHost = host
    }

    // Permission Handling
    private var onGranted: (() -> Unit)? = null
    private var onDenied: (() -> Unit)? = null
    private var onPermanentlyDenied: (() -> Unit)? = null

    override fun requestStoragePermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        onPermanentlyDenied: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                onGranted()
            } else {
                this.onGranted = onGranted
                this.onDenied = onDenied
                this.onPermanentlyDenied = onPermanentlyDenied
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    REQUEST_WRITE_EXTERNAL_STORAGE
                )
            }
        } else {
            onGranted() // Android 10+ doesn't need permission
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            val permission = permissions.getOrNull(0)
            val grantResult = grantResults.getOrNull(0)

            if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                when {
                    grantResult == PackageManager.PERMISSION_GRANTED -> {
                        onGranted?.invoke()
                    }
                    shouldShowRequestPermissionRationale(permission) -> {
                        onDenied?.invoke()
                    }
                    else -> {
                        onPermanentlyDenied?.invoke()
                    }
                }
                onGranted = null
                onDenied = null
                onPermanentlyDenied = null
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val currentFragment = currentVideoHost

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val allowPip = prefs.getBoolean("allow_pip_mode", true)

        Log.i("pipmode", "enter pip mode?\n$currentFragment\nallowpip:$allowPip\n")

        if (currentFragment?.shouldEnterPip() == true && allowPip) {
            Log.i("pipmode", "enter pip mode")
            currentFragment.enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        currentVideoHost?.onPipModeChanged(isInPictureInPictureMode)
    }

    fun togglePlayPause() {
        currentVideoHost?.togglePlayPause()
    }

    // Helper Methods
    private fun isDeviceSecureCompat(context: Context): Boolean {
        val km = context.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }

    private fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationFailed() {}
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_request))
            .setSubtitle(getString(R.string.unlock_method))
            .setDescription(getString(R.string.unlock_desc))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onStart() {
        super.onStart()
        registerPipReceiver()
        window.decorView.post {
            textFromClipboard?.let {
                videoUrlRegex.find(it)?.groupValues?.get(1)?.let { videoCode ->
                    showFindRelatedLinkSnackBar(videoCode)
                }
            }
        }
    }

    private fun registerPipReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAY)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
            Log.i("pipmode", "✅ registerReceiver with RECEIVER_NOT_EXPORTED")
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
            Log.i("pipmode", "✅ registerReceiver (legacy)")
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipActionReceiver)
    }

    private fun applyAppLocale(context: Context): Context {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lang = prefs.getString("app_language", "system") ?: "system"

        val newLocale = when (lang) {
            "zh-rCN" -> Locale.SIMPLIFIED_CHINESE
            "zh" -> Locale.TRADITIONAL_CHINESE
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            else -> Resources.getSystem().configuration.locales.get(0)
        }

        Locale.setDefault(newLocale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(newLocale)
        return context.createConfigurationContext(config)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun showFindRelatedLinkSnackBar(videoCode: String) {
        showSnackBar(R.string.detect_ha1_related_link_in_clipboard, Snackbar.LENGTH_LONG) {
            setAction(R.string.enter) {
                showVideoDetailFragment(videoCode)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Modernized Site Selection Dialog
    // ─────────────────────────────────────────────────────────────────────────

    private data class SiteInfo(
        val name: String,
        val description: String,
        val siteType: SiteType,
        @DrawableRes val logoRes: Int,
        val accentColor: Color,
    )

    @Composable
    private fun SiteSelectionDialog(
        onDismiss: () -> Unit,
        onSiteSelected: (SiteType) -> Unit,
    ) {
        val sites = remember {
            listOf(
                SiteInfo(
                    name = "Hanime",
                    description = "Main site with H-anime",
                    siteType = SiteType.HANIME,
                    logoRes = R.drawable.s_switch_han1me,
                    accentColor = Color(0xFFE53935),
                ),
                SiteInfo(
                    name = "MissAV",
                    description = "JAV streaming platform",
                    siteType = SiteType.MISSAV,
                    logoRes = R.drawable.s_switch_missav,
                    accentColor = Color(0xFF1E88E5),
                ),
                SiteInfo(
                    name = "HentaiMama",
                    description = "Hentai streaming site",
                    siteType = SiteType.HENTAIMAMA,
                    logoRes = R.drawable.s_switch_hentaimama,
                    accentColor = Color(0xFF8E24AA),
                ),
                SiteInfo(
                    name = "Javchu",
                    description = "JAV library (Fixed URL)",
                    siteType = SiteType.JAVCHU,
                    logoRes = R.drawable.s_switch_javchu,
                    accentColor = Color(0xFFFB8C00),
                ),
            )
        }

        val currentSite = Preferences.siteType
        var selectedSite by remember { mutableStateOf(currentSite) }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_switch_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 8.dp),
                    )
                    Text(
                        text = "Switch Site",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Select a source to browse from",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sites.forEach { siteInfo ->
                        val isSelected = selectedSite == siteInfo.siteType

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedSite = siteInfo.siteType },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    siteInfo.accentColor.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ),
                            border = if (isSelected) {
                                BorderStroke(2.dp, siteInfo.accentColor)
                            } else {
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            },
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 4.dp else 0.dp,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Logo container
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) {
                                                siteInfo.accentColor.copy(alpha = 0.2f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(siteInfo.logoRes),
                                        contentDescription = siteInfo.name,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                }

                                // Text info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = siteInfo.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) {
                                            siteInfo.accentColor
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = siteInfo.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // Selection indicator
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                siteInfo.accentColor
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedSite != currentSite) {
                            onSiteSelected(selectedSite)
                        }
                        onDismiss()
                    },
                    enabled = selectedSite != currentSite,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_switch_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (selectedSite != currentSite) {
                            "Switch to ${sites.find { it.siteType == selectedSite }?.name ?: ""}"
                        } else {
                            "Current Site"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}