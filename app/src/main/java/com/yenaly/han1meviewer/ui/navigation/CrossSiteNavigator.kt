package com.yenaly.han1meviewer.ui.navigation

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.HanimeConstants
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.MissAV.common.MissAvConstants
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirTorChecker
import com.yenaly.han1meviewer.OnAir.data.remote.TorFailure
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class PendingVideoOpen(
    val site: SiteType,
    val videoCode: String,
    val path: String? = null,
    val resumePosition: Long = 0L,
    val title: String = "",
    val autoPlay: Boolean = false,
)

object CrossSiteNavigator {

    sealed interface Validation {
        data object Ok : Validation
        data class Failed(val title: String, val message: String) : Validation
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
    }

    fun isSameSiteFamily(a: SiteType, b: SiteType): Boolean {
        if (a == b) return true
        val hanimeFamily = setOf(SiteType.HANIME, SiteType.JAVCHU)
        return a in hanimeFamily && b in hanimeFamily
    }

    fun needsSwitch(target: SiteType): Boolean =
        !isSameSiteFamily(Preferences.siteType, target)

    fun siteLabel(site: SiteType): String = when (site) {
        SiteType.HANIME -> "Hanime"
        SiteType.JAVCHU -> "Javchu"
        SiteType.MISSAV -> "MissAV"
        SiteType.HENTAIMAMA -> "HentaiMama"
        SiteType.ONAIR -> "OnAir"
        SiteType.FAPFOLDER -> "FapFolder"
    }

    suspend fun validate(site: SiteType): Validation = when (site) {
        SiteType.ONAIR -> validateOnAir()
        else -> validateReachable(site)
    }

    private suspend fun validateOnAir(): Validation {
        val tor = OnAirTorChecker.check()
        if (tor.isTorExit) return Validation.Ok
        val message = when (tor.failure) {
            TorFailure.Timeout ->
                "Tor check timed out. Make sure the Tor daemon is running, then retry."

            TorFailure.NetworkUnreachable ->
                "Cannot reach the Tor check service. Connect to Tor before opening OnAir."

            TorFailure.NonTorExit ->
                "This device is not exiting through the Tor network. Enable Tor and try again."

            else -> tor.message.ifBlank { "Tor is required to access OnAir." }
        }
        return Validation.Failed(title = "Tor not connected", message = message)
    }

    private suspend fun validateReachable(site: SiteType): Validation =
        withContext(Dispatchers.IO) {
            val url = targetUrl(site)
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                client.newCall(request).execute().use { Validation.Ok }
            } catch (e: SocketTimeoutException) {
                Validation.Failed(
                    title = "Connection timed out",
                    message = "${siteLabel(site)} ($url) did not answer in time. " +
                            "Check your connection, VPN or DNS settings and retry.",
                )
            } catch (e: UnknownHostException) {
                Validation.Failed(
                    title = "Cannot resolve host",
                    message = "The address of ${siteLabel(site)} ($url) could not be resolved. " +
                            "Check your connection or DNS-over-HTTPS settings and retry.",
                )
            } catch (e: IOException) {
                Validation.Failed(
                    title = "Connection failed",
                    message = "Could not connect to ${siteLabel(site)} ($url): " +
                            (e.message ?: e.javaClass.simpleName),
                )
            } catch (e: IllegalArgumentException) {
                Validation.Failed(
                    title = "Invalid address",
                    message = "The configured address for ${siteLabel(site)} is not valid: $url",
                )
            }
        }

    private fun targetUrl(site: SiteType): String = when (site) {
        SiteType.HANIME -> {
            val sp = Preferences.preferenceSp
            val custom = sp.getString(SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, "").orEmpty()
            val useCustom = sp.getBoolean(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, false)
            val hanimeDomains = HanimeConstants.HANIME_URL.take(3)
            val current = sp.getString(SettingsPreferenceKeys.DOMAIN_NAME, "").orEmpty()
            val last = sp.getString("last_hanime_domain", "").orEmpty()
            when {
                custom.startsWith("http") && (useCustom ||
                        custom.contains("hanime1.me") || custom.contains("hanime1.com")) -> custom

                current in hanimeDomains -> current
                last in hanimeDomains -> last
                else -> HanimeConstants.HANIME_URL[0]
            }
        }

        SiteType.JAVCHU -> "https://javchu.com"
        SiteType.MISSAV -> MissAvConstants.MISSAV_URL[0]
        SiteType.HENTAIMAMA -> HentaiMamaConstants.BASE_URL
        SiteType.ONAIR -> "https://check.torproject.org/"
        SiteType.FAPFOLDER -> FapFolderConstants.BASE_URL
    }
}
