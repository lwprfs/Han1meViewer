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
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork
import com.yenaly.han1meviewer.HanimeConstants
import com.yenaly.han1meviewer.OnAir.common.OnAirSitePreferences
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.PermissionRequester
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logout
import com.yenaly.han1meviewer.logic.network.HanimeNetwork
import com.yenaly.han1meviewer.ui.component.SiteSwitchDialog
import com.yenaly.han1meviewer.ui.bridge.VideoPageHost
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import com.yenaly.han1meviewer.MissAV.common.MissAvConstants
import com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetwork
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork

class MainActivity : FrameActivity(), PermissionRequester {

    val viewModel: HomePageViewModel by viewModels()

    lateinit var navController: NavHostController
    private var showAuthGuard by mutableStateOf(true)

    private val pendingNavigationRequests = Channel<Intent>(Channel.BUFFERED)
    private var currentVideoHost: VideoPageHost? = null

    var showSiteSwitchConfirm by mutableStateOf(false)
        private set
    var showLogoutConfirm by mutableStateOf(false)
        private set
    private var logoutCloseCurrentPage = false

    private var showSiteSelectDialog by mutableStateOf(false)

    var crossSiteRequest by mutableStateOf<PendingVideoOpen?>(null)
        private set

    private var pendingOpenAfterSwitch: PendingVideoOpen? = null

    private val _siteChanged = mutableStateOf(0L)

    private val _siteSwitchState = MutableStateFlow<SiteType?>(null)
    private val siteSwitchState = _siteSwitchState.asStateFlow()

    companion object {
        private const val REQUEST_WRITE_EXTERNAL_STORAGE = 1234
        const val ACTION_TOGGLE_PLAY = "com.yenaly.han1meviewer.ACTION_TOGGLE_PLAY"

        private const val TAG = "MainActivity"

        private const val SITE_SWITCH_SETTLE_DELAY_MS = 450L
        private const val SITE_SWITCH_NAVIGATION_DELAY_MS = 50L
        private const val SITE_SWITCH_TOAST_DELAY_MS = 650L
    }

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

            LaunchedEffect(siteSwitchState, _siteChanged) {
                siteSwitchState.collect { siteType ->
                    siteType?.let {
                        kotlinx.coroutines.delay(SITE_SWITCH_SETTLE_DELAY_MS)
                        Log.d(TAG, "LaunchedEffect: navigating to $it")

                        NavigationManager.clearBackStack()
                        val pendingOpen = pendingOpenAfterSwitch
                        pendingOpenAfterSwitch = null
                        NavigationManager.switchSite(it, pendingOpen)

                        _siteSwitchState.value = null
                    }
                }
            }

            MainActivityContent(
                activity = this,
                viewModel = viewModel,
                pendingNavigationRequests = remember { pendingNavigationRequests.receiveAsFlow() },
                showAuthGuard = showAuthGuard,
                onOpenAccount = { navController.navigateSafely(AccountRoute) },
                onLogoutClick = { requestLogout(false) },
                onRequireLogin = { gotoLoginActivity() },
                onSwitchSiteClick = { requestSiteSwitch() },
                onNavigateControllerReady = { controller ->
                    navController = controller
                    NavigationManager.initialize(controller, Preferences.siteType)
                    Log.d(TAG, "NavController initialized with site: ${Preferences.siteType}")
                },
                siteChangeKey = _siteChanged.value,
            )

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
                GlobalToasts.show(
                    getString(R.string.not_compact_lock_screen),
                    level = GlobalToasts.ToastLevel.WARNING
                )
                hasAuthenticated = true
                showAuthGuard = false
                initData()
            }
        } else {
            hasAuthenticated = true
            showAuthGuard = false
            initData()
        }
        if (savedInstanceState == null) {
            pendingNavigationRequests.trySend(intent)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyAppLocale(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigationRequests.trySend(intent)
    }

    fun requestSiteSwitch() {
        showSiteSwitchConfirm = true
    }

    fun requestCrossSiteOpen(request: PendingVideoOpen) {
        if (CrossSiteNavigator.needsSwitch(request.site)) {
            crossSiteRequest = request
        } else {
            NavigationManager.navigateToVideo(
                request.videoCode,
                request.path,
                request.resumePosition,
                request.autoPlay,
            )
        }
    }

    fun dismissCrossSiteRequest() {
        crossSiteRequest = null
    }

    fun confirmCrossSiteOpen(request: PendingVideoOpen) {
        crossSiteRequest = null
        switchToSite(request.site, request)
    }

    fun dismissSiteSwitch() {
        showSiteSwitchConfirm = false
    }

    fun confirmSiteSwitch() {
        showSiteSwitchConfirm = false
        showSiteSelectDialog = true
    }

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

    fun switchToSite(siteType: SiteType, thenOpen: PendingVideoOpen? = null) {
        Log.d(TAG, "switchToSite called: $siteType")
        pendingOpenAfterSwitch = thenOpen
        Log.d(TAG, "Current baseUrl before: ${Preferences.baseUrl}")

        Preferences.siteType = siteType

        when (siteType) {
            SiteType.HANIME -> {
                val savedCustomMirror = Preferences.preferenceSp.getString(
                    SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, ""
                ) ?: ""
                val savedUseCustomMirror = Preferences.preferenceSp.getBoolean(
                    SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false
                )

                if (savedCustomMirror.isNotBlank() &&
                    (savedCustomMirror.contains("hanime1.me") ||
                     savedCustomMirror.contains("hanime1.com") ||
                     savedUseCustomMirror)
                ) {
                    Log.d(TAG, "Restoring custom mirror: $savedCustomMirror")
                } else {

                    val hanimeDomains = HanimeConstants.HANIME_URL.take(3)
                    val currentDomain = Preferences.preferenceSp
                        .getString(SettingsPreferenceKeys.DOMAIN_NAME, "").orEmpty()
                    val lastDomain = Preferences.preferenceSp
                        .getString("last_hanime_domain", "").orEmpty()
                    val restoredDomain = hanimeDomains.firstOrNull { it == currentDomain }
                        ?: hanimeDomains.firstOrNull { it == lastDomain }
                        ?: HanimeConstants.HANIME_URL[0]
                    Preferences.preferenceSp.edit(true) {
                        putString(SettingsPreferenceKeys.DOMAIN_NAME, restoredDomain)
                        putString(SettingsPreferenceKeys.SELECTED_BASE_URL, restoredDomain)
                        putString(SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, "")
                        putBoolean(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false)
                        putBoolean(SettingsPreferenceKeys.APPEND_CUSTOM_MIRROR_PATH, true)
                    }
                }
                HanimeNetwork.rebuildNetwork()
            }

            SiteType.JAVCHU -> {
                val currentDomain = Preferences.preferenceSp
                    .getString(SettingsPreferenceKeys.DOMAIN_NAME, "").orEmpty()
                if (HanimeConstants.HANIME_URL.take(3).contains(currentDomain)) {
                    Preferences.preferenceSp.edit(true) { putString("last_hanime_domain", currentDomain) }
                }
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

            SiteType.ONAIR -> {
                OnAirNetwork.rebuildNetwork()
            }

            SiteType.FAPFOLDER -> {
                FapFolderNetwork.init(applicationContext)
                FapFolderNetwork.rebuildNetwork()
            }

            SiteType.SUPJAV -> {
                SupJavNetwork.rebuildNetwork()
            }
        }

        Log.d(TAG, "New baseUrl after: ${Preferences.baseUrl}")
        Log.d(TAG, "New displayUrl: ${Preferences.displayUrl}")

        _siteChanged.value = System.currentTimeMillis()

        if (siteType == SiteType.HANIME || siteType == SiteType.JAVCHU) {
            viewModel.getHomePage(force = true)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (Preferences.siteType == siteType) {
                Log.d(TAG, "Emitting siteSwitchState=$siteType after settle delay")
                _siteSwitchState.value = siteType
            } else {
                Log.d(TAG, "Skipping stale site switch to $siteType")
            }
        }, SITE_SWITCH_NAVIGATION_DELAY_MS)

        Handler(Looper.getMainLooper()).postDelayed({
            val urlDisplay = when (siteType) {
                SiteType.JAVCHU -> getString(R.string.site_label_javchu)
                SiteType.ONAIR -> getString(R.string.site_label_onair, OnAirSitePreferences.currentHostname())
                SiteType.FAPFOLDER -> getString(R.string.site_label_fapfolder, Preferences.displayUrl)
                else -> Preferences.displayUrl
            }
            GlobalToasts.show(
                getString(R.string.switched_to_site, urlDisplay),
                level = GlobalToasts.ToastLevel.SUCCESS
            )
        }, SITE_SWITCH_TOAST_DELAY_MS)
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
            onGranted()
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
        runCatching { unregisterReceiver(pipActionReceiver) }
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

    @Composable
    private fun SiteSelectionDialog(
        onDismiss: () -> Unit,
        onSiteSelected: (SiteType) -> Unit,
    ) {
        SiteSwitchDialog(
            currentSite = Preferences.siteType,
            onDismiss = onDismiss,
            onSiteSelected = onSiteSelected,
        )
    }
}
