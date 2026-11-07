package com.yenaly.han1meviewer.ui.navigation

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.HanimeConstants
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.MissAV.common.MissAvConstants
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirTorChecker
import com.yenaly.han1meviewer.OnAir.data.remote.TorFailure
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.yenaly_libs.utils.applicationContext
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
        SiteType.SUPJAV -> "SupJav"
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
                applicationContext.getString(R.string.cross_tor_timeout)

            TorFailure.NetworkUnreachable ->
                applicationContext.getString(R.string.cross_tor_unreachable)

            TorFailure.NonTorExit ->
                applicationContext.getString(R.string.cross_tor_non_exit)

            else -> tor.message.ifBlank { applicationContext.getString(R.string.cross_tor_required) }
        }
        return Validation.Failed(title = applicationContext.getString(R.string.onair_tor_not_connected), message = message)
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
                    title = applicationContext.getString(R.string.site_connection_timed_out),
                    message = applicationContext.getString(R.string.site_did_not_answer_in_time_check, siteLabel(site), url),
                )
            } catch (e: UnknownHostException) {
                Validation.Failed(
                    title = applicationContext.getString(R.string.site_cannot_resolve_host),
                    message = applicationContext.getString(R.string.site_the_address_of_could_not_be, siteLabel(site), url),
                )
            } catch (e: IOException) {
                Validation.Failed(
                    title = applicationContext.getString(R.string.site_connection_failed),
                    message = applicationContext.getString(R.string.site_could_not_connect_to, siteLabel(site), url) + " " +
                            (e.message ?: e.javaClass.simpleName),
                )
            } catch (e: IllegalArgumentException) {
                Validation.Failed(
                    title = applicationContext.getString(R.string.site_invalid_address),
                    message = applicationContext.getString(R.string.site_the_configured_address_for_is_not, siteLabel(site), url),
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
        SiteType.SUPJAV -> com.yenaly.han1meviewer.SupJav.common.SupJavConstants.BASE_URL
    }
}
