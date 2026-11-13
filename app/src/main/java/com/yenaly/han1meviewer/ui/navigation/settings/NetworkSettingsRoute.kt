package com.yenaly.han1meviewer.ui.navigation.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetwork
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoNetwork
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterNetwork
import com.yenaly.han1meviewer.logic.Parser
import com.yenaly.han1meviewer.logic.network.DohConfig
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.HanimeNetwork
import com.yenaly.han1meviewer.logic.network.MirrorLatency
import com.yenaly.han1meviewer.logic.network.MirrorProbeResult
import com.yenaly.han1meviewer.logic.network.ServiceCreator
import com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs
import com.yenaly.han1meviewer.logic.network.SiteNetworks
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.logout
import com.yenaly.han1meviewer.ui.component.ConfirmDialog
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.screen.settings.DelayResultUi
import com.yenaly.han1meviewer.ui.screen.settings.DohTestResultUi
import com.yenaly.han1meviewer.ui.screen.runAfterUiSettles
import com.yenaly.han1meviewer.ui.screen.settings.MirrorRowUi
import com.yenaly.han1meviewer.ui.screen.settings.NetworkSettingsScreen
import com.yenaly.han1meviewer.ui.screen.settings.NetworkSettingsUiState
import com.yenaly.han1meviewer.util.ActivityManager
import com.yenaly.han1meviewer.util.applicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val NETWORK_USE_BACKUP_MEDIA_CDN = "use_backup_media_cdn"

private enum class DohConflictTarget {
    EnableDoH,
    EnableBuiltInHosts,
}

private fun SiteType.sharesHanimeLogin(): Boolean =
    this == SiteType.HANIME || this == SiteType.JAVCHU

private fun rebuildAllNetworks() {
    runCatching { HanimeNetwork.rebuildNetwork() }
    runCatching { MissAvNetwork.rebuildNetwork() }
    runCatching { HentaiMamaNetwork.rebuildNetwork() }
    runCatching { OnAirNetwork.rebuildNetwork() }
    runCatching { SupJavNetwork.rebuildNetwork() }
    runCatching { RouVideoNetwork.rebuildNetwork() }
    runCatching { XHamsterNetwork.rebuildNetwork() }
    runCatching { FapFolderNetwork.rebuildNetwork() }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun NetworkSettingsRouteScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }

    var editingSite by remember { mutableStateOf(Preferences.siteType) }
    val profile = SiteNetworks.forSite(editingSite)

    var currentHost by remember { mutableStateOf(SiteNetworkPrefs.baseUrl(editingSite)) }
    var isDelayTesting by remember { mutableStateOf(false) }
    var isDohTesting by remember { mutableStateOf(false) }
    var isCustomMirrorTesting by remember { mutableStateOf(false) }
    var customMirrorTestResult by remember { mutableStateOf<String?>(null) }
    var showDomainRestartConfirm by remember { mutableStateOf(false) }
    var showHostsRestartConfirm by remember { mutableStateOf(false) }
    var showCustomHostsValidationError by remember { mutableStateOf<List<String>?>(null) }
    var showCustomMirrorValidationError by remember { mutableStateOf(false) }
    var showCustomMirrorWarningConfirm by remember { mutableStateOf(false) }
    var showDohConflictConfirm by remember { mutableStateOf(false) }
    var showSocks5Warning by remember { mutableStateOf(false) }
    var showApplyDeepLinksDialog by remember { mutableStateOf(false) }
    var pendingDomainValue by remember { mutableStateOf("") }
    var pendingSite by remember { mutableStateOf(editingSite) }
    var pendingUseCustomMirrorSite by remember { mutableStateOf(false) }
    var pendingCustomMirrorSite by remember { mutableStateOf("") }
    var pendingAppendCustomMirrorPath by remember { mutableStateOf(true) }
    var pendingDohConflictTarget by remember { mutableStateOf(DohConflictTarget.EnableDoH) }
    var pendingDohEnabled by remember { mutableStateOf(false) }
    var pendingDohPreset by remember { mutableStateOf("alidns") }
    var pendingDohCustomUrl by remember { mutableStateOf("") }
    var pendingDohBootstrapIps by remember { mutableStateOf("") }
    var pendingDohTimeoutSeconds by remember { mutableIntStateOf(10) }

    var isMirrorDialogOpen by remember { mutableStateOf(false) }
    var isMirrorTesting by remember { mutableStateOf(false) }
    val mirrorResults = remember { mutableStateMapOf<String, MirrorProbeResult>() }
    val mirrorRun = remember { AtomicInteger(0) }

    val delayResults = remember { mutableStateListOf<DelayResultUi>() }
    val dohTestResults = remember { mutableStateListOf<DohTestResultUi>() }
    val delayHandler = remember { Handler(Looper.getMainLooper()) }
    val dohHandler = remember { Handler(Looper.getMainLooper()) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val executor = remember { Executors.newCachedThreadPool() }
    val mirrorExecutor = remember { Executors.newFixedThreadPool(6) }
    val uiState = remember(refreshKey, editingSite, context) {
        buildNetworkSettingsUiState(context, editingSite)
    }
    val networkTimeoutText = stringResource(R.string.network_timeout_text)
    val invalidIpOrPort = stringResource(R.string.invalid_ip_or_port)
    val defaultLabel = stringResource(R.string.default_)

    val actionNotSupport = stringResource(R.string.action_app_open_by_default_settings_not_support)
    fun stopDelayTest() {
        isDelayTesting = false
        delayHandler.removeCallbacksAndMessages(null)
    }

    fun stopDohTest() {
        isDohTesting = false
        dohHandler.removeCallbacksAndMessages(null)
    }

    fun measureDelay(ip: String): Int {
        return try {
            val start = System.currentTimeMillis()
            val address = InetAddress.getByName(ip)
            val reachable = address.isReachable(2000)
            if (reachable) (System.currentTimeMillis() - start).toInt() else -1
        } catch (_: Exception) {
            -1
        }
    }

    fun testIp(ip: String) {
        if (!isDelayTesting) return
        executor.execute {
            val delay = measureDelay(ip)
            delayHandler.post {
                val index = delayResults.indexOfFirst { it.ip == ip }
                if (index >= 0) {
                    delayResults[index] = DelayResultUi(ip, delay)
                }
            }
        }
    }

    fun scheduleNextTest(ipList: List<String>) {
        if (!isDelayTesting) return
        ipList.forEach(::testIp)
        delayHandler.postDelayed({ scheduleNextTest(ipList) }, 2000)
    }

    fun currentSiteHost(): String =
        SiteNetworkPrefs.baseUrl(editingSite).toHttpUrlOrNull()?.host
            ?: applicationContext.getString(R.string.unknow)

    fun runDohTest() {
        if (isDohTesting) return
        val site = editingSite
        val host = currentSiteHost()
        currentHost = SiteNetworkPrefs.baseUrl(site)
        dohTestResults.clear()
        isDohTesting = true
        executor.execute {
            val start = System.currentTimeMillis()
            val result = runCatching { HDns().lookupByDoHOnly(host, site) }
            val delay = (System.currentTimeMillis() - start).toInt()
            dohHandler.post {
                dohTestResults.clear()
                result.onSuccess { list ->
                    dohTestResults.add(
                        DohTestResultUi(
                            host = host,
                            ips = list.mapNotNull { it.hostAddress }.distinct(),
                            delay = delay,
                            message = "",
                        )
                    )
                }.onFailure { throwable ->
                    Log.w("DOH_TEST", "lookup failed for $host: ${throwable.message}")
                    dohTestResults.add(
                        DohTestResultUi(
                            host = host,
                            ips = emptyList(),
                            delay = -1,
                            message = throwable.message?.ifBlank { networkTimeoutText }
                                ?: networkTimeoutText,
                        )
                    )
                }
            }
        }
    }

    fun startMirrorTest() {
        val run = mirrorRun.incrementAndGet()
        val nodes = SiteNetworks.forSite(editingSite).mirrors
        mirrorResults.clear()
        nodes.forEach { mirrorResults[it.host] = MirrorProbeResult.Pending }
        isMirrorTesting = nodes.isNotEmpty()
        val remaining = AtomicInteger(nodes.size)
        nodes.forEach { node ->
            mirrorExecutor.execute {
                val result = if (mirrorRun.get() == run) MirrorLatency.probe(node) else null
                mainHandler.post {
                    if (mirrorRun.get() == run) {
                        if (result != null) mirrorResults[node.host] = result
                        if (remaining.decrementAndGet() == 0) isMirrorTesting = false
                    }
                }
            }
        }
    }

    fun stopMirrorTest() {
        mirrorRun.incrementAndGet()
        isMirrorTesting = false
        isMirrorDialogOpen = false
    }

    fun requestMirrorChange(baseUrl: String) {
        val origin = SiteNetworkPrefs.selectedMirrorUrl(editingSite)
        val changesCustom = SiteNetworkPrefs.useCustomMirror(editingSite)
        if (baseUrl.trimEnd('/') == origin.trimEnd('/') && !changesCustom) return
        pendingSite = editingSite
        pendingDomainValue = baseUrl

        pendingUseCustomMirrorSite = false
        pendingCustomMirrorSite = SiteNetworkPrefs.customMirror(editingSite)
        pendingAppendCustomMirrorPath = SiteNetworkPrefs.appendCustomPath(editingSite)
        showDomainRestartConfirm = true
    }

    DisposableEffect(Unit) {
        onDispose {
            stopDelayTest()
            stopDohTest()
            mirrorRun.incrementAndGet()
            executor.shutdownNow()
            mirrorExecutor.shutdownNow()
        }
    }

    val selectedMirrorUrl = SiteNetworkPrefs.selectedMirrorUrl(editingSite)
    val selectedMirrorHost = selectedMirrorUrl.toHttpUrlOrNull()?.host
    val customMirrorActive = SiteNetworkPrefs.useCustomMirror(editingSite)
    val mirrorRows = profile.mirrors.mapIndexed { index, node ->
        MirrorRowUi(
            host = node.host,
            baseUrl = node.baseUrl,
            isDefault = index == 0,
            selected = !customMirrorActive && node.host == selectedMirrorHost,
            result = mirrorResults[node.host] ?: MirrorProbeResult.Pending,
        )
    }
    val domainOptions = profile.mirrors.mapIndexed { index, node ->
        (if (index == 0) "${node.host} ($defaultLabel)" else node.host) to node.baseUrl
    }

    NetworkSettingsScreen(
        state = uiState,
        domainOptions = domainOptions,
        currentHost = currentHost,
        delayResults = delayResults,
        dohTestResults = dohTestResults,
        isDelayTesting = isDelayTesting,
        isDohTesting = isDohTesting,
        proxyType = SiteNetworkPrefs.proxyType(editingSite),
        proxyIp = SiteNetworkPrefs.proxyIp(editingSite),
        proxyPort = SiteNetworkPrefs.proxyPort(editingSite),
        dohEnabled = SiteNetworkPrefs.useDoH(editingSite),
        dohPreset = SiteNetworkPrefs.dohPreset(editingSite),
        dohCustomUrl = SiteNetworkPrefs.dohCustomUrl(editingSite),
        dohBootstrapIps = SiteNetworkPrefs.dohBootstrapIps(editingSite),
        dohTimeoutSeconds = SiteNetworkPrefs.dohTimeoutSeconds(editingSite),
        useCustomMirrorSite = SiteNetworkPrefs.useCustomMirror(editingSite),
        customMirrorSite = SiteNetworkPrefs.customMirror(editingSite),
        appendCustomMirrorPath = SiteNetworkPrefs.appendCustomPath(editingSite),
        customMirrorTestResult = customMirrorTestResult,
        isCustomMirrorTesting = isCustomMirrorTesting,
        showCustomMirror = true,
        isJavchu = false,
        showBackupMediaCdn = editingSite == SiteType.HANIME,
        siteName = profile.displayName,
        siteOptions = SiteNetworks.profiles.map { it.displayName to it.siteType.name },
        selectedSiteKey = editingSite.name,
        onSiteChange = { key ->
            val next = runCatching { SiteType.valueOf(key) }.getOrNull()
            if (next != null && next != editingSite) {
                stopDelayTest()
                stopDohTest()
                stopMirrorTest()
                mirrorResults.clear()
                customMirrorTestResult = null
                editingSite = next
                currentHost = SiteNetworkPrefs.baseUrl(next)
                refreshKey++
            }
        },
        mirrorRows = mirrorRows,
        isMirrorDialogOpen = isMirrorDialogOpen,
        isMirrorTesting = isMirrorTesting,
        onOpenMirrorLatency = {
            isMirrorDialogOpen = true
            startMirrorTest()
        },
        onUseMirror = { host ->
            profile.mirrors.firstOrNull { it.host == host }?.let { node ->
                stopMirrorTest()
                requestMirrorChange(node.baseUrl)
            }
        },
        onUseFastestMirror = {
            val fastest = MirrorLatency.fastestHost(mirrorResults.toMap())
            val node = profile.mirrors.firstOrNull { it.host == fastest }
            if (node != null) {
                stopMirrorTest()
                requestMirrorChange(node.baseUrl)
            }
        },
        onRetestMirrors = { startMirrorTest() },
        onDismissMirrorLatency = { stopMirrorTest() },
        onDomainChange = { newValue -> requestMirrorChange(newValue) },
        onSaveCustomMirrorSite = { enabled, url, appendPath ->
            val normalizedUrl = normalizeCustomMirrorSite(url)
            if (enabled && normalizedUrl == null) {
                showCustomMirrorValidationError = true
                return@NetworkSettingsScreen
            }
            val customMirrorSite = normalizedUrl.orEmpty()
            if (enabled != SiteNetworkPrefs.useCustomMirror(editingSite) ||
                customMirrorSite != SiteNetworkPrefs.customMirror(editingSite) ||
                appendPath != SiteNetworkPrefs.appendCustomPath(editingSite)
            ) {
                pendingSite = editingSite
                pendingDomainValue = ""
                pendingUseCustomMirrorSite = enabled
                pendingCustomMirrorSite = customMirrorSite
                pendingAppendCustomMirrorPath = appendPath
                if (enabled) {
                    showCustomMirrorWarningConfirm = true
                } else {
                    showDomainRestartConfirm = true
                }
            }
        },
        onTestCustomMirrorSite = { url, appendPath ->
            val normalizedUrl = normalizeCustomMirrorSite(url)
            if (normalizedUrl == null) {
                customMirrorTestResult = context.getString(R.string.custom_mirror_site_invalid)
                return@NetworkSettingsScreen
            }
            if (isCustomMirrorTesting) return@NetworkSettingsScreen
            isCustomMirrorTesting = true
            customMirrorTestResult = context.getString(R.string.custom_mirror_site_testing)
            val site = editingSite
            executor.execute {
                val result = testCustomMirrorSite(context, site, normalizedUrl, appendPath)
                Handler(Looper.getMainLooper()).post {
                    customMirrorTestResult = result
                    isCustomMirrorTesting = false
                }
            }
        },
        useBackupMediaCdn = Preferences.useBackupMediaCdn,
        onUseBackupMediaCdnChange = { value ->
            Preferences.preferenceSp.edit { putBoolean(NETWORK_USE_BACKUP_MEDIA_CDN, value) }
            refreshKey++
            showHostsRestartConfirm = true
        },
        onUseBuiltInHostsChange = { value ->
            if (value && SiteNetworkPrefs.useDoH(editingSite)) {
                showDohConflictConfirm = true
                pendingDohConflictTarget = DohConflictTarget.EnableBuiltInHosts
                return@NetworkSettingsScreen
            }
            SiteNetworkPrefs.setUseBuiltInHosts(editingSite, value)
            refreshKey++
            rebuildAllNetworks()
            showHostsRestartConfirm = true
        },
        onSaveCustomHosts = { data ->
            val errors = HDns.validateCustomHosts(data)
            if (errors.isNotEmpty()) {
                showCustomHostsValidationError = errors
                return@NetworkSettingsScreen
            }
            SiteNetworkPrefs.setCustomHostsData(editingSite, data)
            refreshKey++
            if (SiteNetworkPrefs.useBuiltInHosts(editingSite)) {
                rebuildAllNetworks()
            }
        },
        customHostsData = SiteNetworkPrefs.customHostsData(editingSite),
        onSaveDohSettings = { enabled, preset, url, bootstrapIps, timeoutSeconds ->
            pendingDohEnabled = enabled
            pendingDohPreset = preset
            pendingDohCustomUrl = url
            pendingDohBootstrapIps = bootstrapIps
            pendingDohTimeoutSeconds = timeoutSeconds
            if (enabled && SiteNetworkPrefs.useBuiltInHosts(editingSite)) {
                showDohConflictConfirm = true
                pendingDohConflictTarget = DohConflictTarget.EnableDoH
                return@NetworkSettingsScreen
            }
            SiteNetworkPrefs.setDoH(editingSite, enabled, preset, url, bootstrapIps, timeoutSeconds)
            currentHost = SiteNetworkPrefs.baseUrl(editingSite)
            refreshKey++
            rebuildAllNetworks()
        },
        onOpenDelayTest = {
            val site = editingSite
            val host = currentSiteHost()
            currentHost = SiteNetworkPrefs.baseUrl(site)
            delayResults.clear()
            isDelayTesting = true
            executor.execute {
                val ipList = HDns().getCDNList(host, site)
                Handler(Looper.getMainLooper()).post {
                    Log.i("delayTest", ipList.toString())
                    delayResults.clear()
                    delayResults.addAll(ipList.map { DelayResultUi(it, -1) })
                    scheduleNextTest(ipList)
                }
            }
        },
        onOpenDohTest = { runDohTest() },
        onDismissDelayTest = { stopDelayTest() },
        onDismissDohTest = { stopDohTest() },
        onApplyProxy = { type, ip, port ->
            val valid = when (type) {
                HProxySelector.TYPE_DIRECT, HProxySelector.TYPE_SYSTEM -> true
                HProxySelector.TYPE_HTTP, HProxySelector.TYPE_SOCKS -> HProxySelector.validateIp(ip) && HProxySelector.validatePort(
                    port
                )

                else -> false
            }
            if (!valid) {
                GlobalToasts.show(invalidIpOrPort, level = GlobalToasts.ToastLevel.WARNING)
                return@NetworkSettingsScreen
            }
            if (type == HProxySelector.TYPE_SOCKS) {
                showSocks5Warning = true
            }
            SiteNetworkPrefs.setProxy(editingSite, type, ip, port)
            HProxySelector.rebuildNetwork()
            rebuildAllNetworks()
            refreshKey++
        },
        onOpenApplyDeepLinks = { showApplyDeepLinksDialog = true },
    )

    ConfirmDialog(
        visible = showDomainRestartConfirm,
        title = stringResource(R.string.attention),
        message = stringResource(R.string.domain_change_tips).trimIndent(),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        cancelable = false,
        onConfirm = {
            showDomainRestartConfirm = false
            if (pendingDomainValue.isNotEmpty()) {
                SiteNetworkPrefs.setSelectedMirrorUrl(pendingSite, pendingDomainValue)
            }
            SiteNetworkPrefs.setCustomMirror(
                pendingSite,
                pendingUseCustomMirrorSite,
                pendingCustomMirrorSite,
                pendingAppendCustomMirrorPath,
            )
            if (pendingSite.sharesHanimeLogin()) {
                logout()
            }
            coroutineScope.runAfterUiSettles {
                ActivityManager.restart(killProcess = true)
            }
        },
        onDismiss = {
            pendingDomainValue = ""
            showDomainRestartConfirm = false
        },
    )

    if (showCustomMirrorValidationError) {
        AlertDialog(
            onDismissRequest = { showCustomMirrorValidationError = false },
            title = { Text(stringResource(R.string.attention)) },
            text = { Text(stringResource(R.string.custom_mirror_site_invalid)) },
            confirmButton = {
                TextButton(onClick = { showCustomMirrorValidationError = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }

    ConfirmDialog(
        visible = showHostsRestartConfirm,
        title = stringResource(R.string.attention),
        message = stringResource(R.string.restart_or_not_working, EMPTY_STRING),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        cancelable = false,
        onConfirm = {
            showHostsRestartConfirm = false
            coroutineScope.runAfterUiSettles {
                ActivityManager.restart(killProcess = true)
            }
        },
        onDismiss = { showHostsRestartConfirm = false },
    )

    val validationErrors = showCustomHostsValidationError
    if (validationErrors != null) {
        AlertDialog(
            onDismissRequest = { showCustomHostsValidationError = null },
            title = { Text(stringResource(R.string.attention)) },
            text = { Text(validationErrors.joinToString("\n")) },
            confirmButton = {
                TextButton(onClick = { showCustomHostsValidationError = null }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }

    ConfirmDialog(
        visible = showCustomMirrorWarningConfirm,
        title = stringResource(R.string.attention),
        message = stringResource(R.string.custom_mirror_site_warning),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        cancelable = false,
        onConfirm = {
            showCustomMirrorWarningConfirm = false
            showDomainRestartConfirm = true
        },
        onDismiss = {
            showCustomMirrorWarningConfirm = false
        },
    )

    ConfirmDialog(
        visible = showDohConflictConfirm,
        title = stringResource(R.string.attention),
        message = stringResource(R.string.doh_conflict_message),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        cancelable = false,
        onConfirm = {
            when (pendingDohConflictTarget) {
                DohConflictTarget.EnableDoH -> {
                    SiteNetworkPrefs.setUseBuiltInHosts(editingSite, false)
                    SiteNetworkPrefs.setDoH(
                        editingSite,
                        pendingDohEnabled,
                        pendingDohPreset,
                        pendingDohCustomUrl,
                        pendingDohBootstrapIps,
                        pendingDohTimeoutSeconds,
                    )
                }

                DohConflictTarget.EnableBuiltInHosts -> {
                    SiteNetworkPrefs.setDoH(
                        editingSite,
                        false,
                        SiteNetworkPrefs.dohPreset(editingSite),
                        SiteNetworkPrefs.dohCustomUrl(editingSite),
                        SiteNetworkPrefs.dohBootstrapIps(editingSite),
                        SiteNetworkPrefs.dohTimeoutSeconds(editingSite),
                    )
                    SiteNetworkPrefs.setUseBuiltInHosts(editingSite, true)
                }
            }
            showDohConflictConfirm = false
            refreshKey++
            rebuildAllNetworks()
        },
        onDismiss = { showDohConflictConfirm = false },
    )

    if (showSocks5Warning) {
        AlertDialog(
            onDismissRequest = { showSocks5Warning = false },
            title = { Text(stringResource(R.string.warning)) },
            text = { Text(stringResource(R.string.mpv_socks5_warning)) },
            confirmButton = {
                TextButton(onClick = { showSocks5Warning = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }

    if (showApplyDeepLinksDialog) {
        AlertDialog(
            onDismissRequest = { showApplyDeepLinksDialog = false },
            title = { Text(stringResource(R.string.apply_deep_links)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.apply_deep_links_summary))
                    Text(stringResource(R.string.apply_deep_links_tips))
                    Image(
                        painter = painterResource(R.drawable.apply_deep_links),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showApplyDeepLinksDialog = false
                    try {
                        val intent = Intent().apply {
                            action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
                            } else {
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            }
                            addCategory(Intent.CATEGORY_DEFAULT)
                            data = "package:${context.packageName}".toUri()
                            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        GlobalToasts.show(actionNotSupport, level = GlobalToasts.ToastLevel.WARNING)
                        e.printStackTrace()
                    }
                }) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyDeepLinksDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun buildNetworkSettingsUiState(context: Context, site: SiteType): NetworkSettingsUiState {
    val profile = SiteNetworks.forSite(site)
    val selected = SiteNetworkPrefs.selectedMirrorUrl(site)
    val inheritedSuffix = " " + context.getString(R.string.network_inherited_suffix)
    val proxyInherited = site != SiteType.HANIME && SiteNetworkPrefs.isProxyInherited(site)
    val proxyText = when (SiteNetworkPrefs.proxyType(site)) {
        HProxySelector.TYPE_DIRECT -> context.getString(R.string.direct)
        HProxySelector.TYPE_SYSTEM -> context.getString(R.string.system_proxy)
        HProxySelector.TYPE_HTTP -> context.getString(
            R.string.http_proxy,
            SiteNetworkPrefs.proxyIp(site),
            SiteNetworkPrefs.proxyPort(site)
        )

        HProxySelector.TYPE_SOCKS -> context.getString(
            R.string.socks_proxy,
            SiteNetworkPrefs.proxyIp(site),
            SiteNetworkPrefs.proxyPort(site)
        )

        else -> context.getString(R.string.direct)
    }
    return NetworkSettingsUiState(
        domainName = selected,
        domainDisplay = profile.mirrors.firstOrNull { it.baseUrl == selected }?.host ?: selected,
        proxySummary = if (proxyInherited) proxyText + inheritedSuffix else proxyText,
        useBuiltInHosts = SiteNetworkPrefs.useBuiltInHosts(site),
        useBackupMediaCdn = Preferences.useBackupMediaCdn,
        useCustomMirrorSite = SiteNetworkPrefs.useCustomMirror(site),
        customMirrorSite = SiteNetworkPrefs.customMirror(site),
        appendCustomMirrorPath = SiteNetworkPrefs.appendCustomPath(site),
        useDoH = SiteNetworkPrefs.useDoH(site),
        dohSummary = buildDohSummary(context, site),
        delaySummary = context.getString(R.string.node_latency_sum),
    )
}

private fun normalizeCustomMirrorSite(url: String): String? {
    val trimmed = url.trim().trimEnd('/')
    val uri = runCatching { trimmed.toUri() }.getOrNull() ?: return null
    val host = uri.host
    if (host.isNullOrBlank()) return null
    val schemeOk = uri.scheme == "https" || (uri.scheme == "http" && host.endsWith(".onion"))
    if (!schemeOk) return null
    if (!uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank()) return null
    return url.trim()
}

private fun testCustomMirrorSite(
    context: Context,
    site: SiteType,
    homeUrl: String,
    appendPath: Boolean,
): String {
    if (site != SiteType.HANIME && site != SiteType.JAVCHU) {
        return testGenericMirrorSite(context, homeUrl, appendPath)
    }
    return runCatching {
        val request = Request.Builder().url(homeUrl).get().build()
        ServiceCreator.hClient.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val body = response.body.string()
            if (!response.isSuccessful) {
                return context.getString(
                    R.string.custom_mirror_site_test_failed_http,
                    response.code,
                    finalUrl,
                )
            }

            val apiBaseUrl = buildCustomMirrorApiBaseUrl(homeUrl, appendPath)
            val watchTestResult = testCustomMirrorWatchUrl(context, apiBaseUrl)
            when (val parseResult = Parser.homePageVer2(body)) {
                is WebsiteState.Success -> if (watchTestResult == null) {
                    context.getString(
                        R.string.custom_mirror_site_test_success,
                        finalUrl,
                        apiBaseUrl,
                    )
                } else {
                    context.getString(
                        R.string.custom_mirror_site_test_partial_success,
                        finalUrl,
                        apiBaseUrl,
                        watchTestResult,
                    )
                }

                is WebsiteState.Error -> context.getString(
                    R.string.custom_mirror_site_test_parse_failed,
                    finalUrl,
                    parseResult.throwable.message ?: parseResult.throwable::class.java.simpleName,
                )

                WebsiteState.Loading -> context.getString(
                    R.string.custom_mirror_site_test_parse_failed,
                    finalUrl,
                    context.getString(R.string.loading),
                )
            }
        }
    }.getOrElse { throwable ->
        context.getString(
            R.string.custom_mirror_site_test_failed,
            throwable.message ?: throwable::class.java.simpleName,
        )
    }
}

private fun testGenericMirrorSite(context: Context, homeUrl: String, appendPath: Boolean): String {
    return runCatching {
        val request = Request.Builder().url(homeUrl).get().build()
        MirrorLatency.httpClient.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val code = response.code
            if (code >= 500 || (code in 400..499 && code != 403)) {
                context.getString(R.string.custom_mirror_site_test_failed_http, code, finalUrl)
            } else {
                context.getString(
                    R.string.custom_mirror_site_test_success,
                    finalUrl,
                    buildCustomMirrorApiBaseUrl(homeUrl, appendPath),
                )
            }
        }
    }.getOrElse { throwable ->
        context.getString(
            R.string.custom_mirror_site_test_failed,
            throwable.message ?: throwable::class.java.simpleName,
        )
    }
}

private fun testCustomMirrorWatchUrl(context: Context, apiBaseUrl: String): String? {
    return runCatching {
        val url = apiBaseUrl + "search"
        val request = Request.Builder().url(url).get().build()
        ServiceCreator.hClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                null
            } else {
                context.getString(
                    R.string.custom_mirror_site_watch_test_failed_http,
                    response.code,
                    response.request.url.toString(),
                )
            }
        }
    }.getOrElse { throwable ->
        context.getString(
            R.string.custom_mirror_site_watch_test_failed,
            throwable.message ?: throwable::class.java.simpleName,
        )
    }
}

private fun buildCustomMirrorApiBaseUrl(homeUrl: String, appendPath: Boolean): String {
    val url = if (appendPath) homeUrl else {
        val uri = homeUrl.toUri()
        "${uri.scheme}://${uri.encodedAuthority}"
    }
    return if (url.endsWith('/')) url else "$url/"
}

private fun buildDohSummary(context: Context, site: SiteType): String {
    if (!SiteNetworkPrefs.useDoH(site)) return context.getString(R.string.doh_disabled_summary)
    if (SiteNetworkPrefs.useBuiltInHosts(site)) return context.getString(R.string.doh_conflict_message)
    val core = if (SiteNetworkPrefs.dohPreset(site) == "custom") {
        SiteNetworkPrefs.dohCustomUrl(site).ifBlank { context.getString(R.string.custom) }
    } else {
        DohConfig.selectedPreset(site).title
    }
    val bootstrap = DohConfig.bootstrapIps(site).takeIf { it.isNotEmpty() }?.joinToString()
    val text = if (bootstrap != null) "$core\nBootstrap: $bootstrap" else core
    return if (site != SiteType.HANIME && SiteNetworkPrefs.isDohInherited(site)) {
        text + "\n" + context.getString(R.string.network_inherited_suffix)
    } else {
        text
    }
}
