package com.yenaly.han1meviewer.logic.network

import androidx.core.content.edit
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.OnAir.common.OnAirSitePreferences
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SiteNetworkPrefs {

    private const val SEP = "__"
    private const val LAST_HANIME_DOMAIN = "last_hanime_domain"

    private val sp
        get() = Preferences.preferenceSp

    private fun key(base: String, site: SiteType): String =
        if (site == SiteType.HANIME) base else base + SEP + site.name.lowercase()

    private fun mirrorKey(site: SiteType): String = when (site) {
        SiteType.HANIME -> SettingsPreferenceKeys.DOMAIN_NAME
        SiteType.MISSAV -> "missav_base_url"
        SiteType.HENTAIMAMA -> "hentaimama_base_url"
        SiteType.JAVCHU -> "javchu_base_url"
        else -> "site_base_url" + SEP + site.name.lowercase()
    }

    fun selectedMirrorUrl(site: SiteType): String {
        if (site == SiteType.ONAIR) return OnAirSitePreferences.selectedBaseUrl
        val profile = SiteNetworks.forSite(site)
        var raw = sp.getString(mirrorKey(site), null)
        if (site == SiteType.HANIME && raw != null &&
            raw.toHttpUrlOrNull()?.host.equals("javchu.com", ignoreCase = true)
        ) {

            raw = sp.getString(LAST_HANIME_DOMAIN, null)
        }
        return if (raw.isNullOrBlank()) profile.defaultBaseUrl else profile.normalizeBase(raw)
    }

    fun setSelectedMirrorUrl(site: SiteType, url: String) {
        if (site == SiteType.ONAIR) {
            val wanted = url.trim().trimEnd('/')
            val index = OnAirConstants.ONAIR_URL.indexOfFirst { it.trimEnd('/') == wanted }
            if (index >= 0) OnAirSitePreferences.setMirrorIndex(index)
            return
        }
        val normalized = SiteNetworks.forSite(site).normalizeBase(url)
        sp.edit(commit = true) {
            putString(mirrorKey(site), normalized)
            if (site == SiteType.HANIME) {
                putString(SettingsPreferenceKeys.SELECTED_BASE_URL, normalized)
            }
        }
    }

    fun useCustomMirror(site: SiteType): Boolean =
        sp.getBoolean(key(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, site), false)

    fun customMirror(site: SiteType): String =
        sp.getString(key(SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, site), "").orEmpty()

    fun appendCustomPath(site: SiteType): Boolean =
        sp.getBoolean(key(SettingsPreferenceKeys.APPEND_CUSTOM_MIRROR_PATH, site), true)

    fun setCustomMirror(site: SiteType, enabled: Boolean, url: String, appendPath: Boolean) {
        sp.edit(commit = true) {
            putBoolean(key(SettingsPreferenceKeys.USE_CUSTOM_MIRROR_SITE, site), enabled)
            putString(key(SettingsPreferenceKeys.CUSTOM_MIRROR_SITE, site), url)
            putBoolean(key(SettingsPreferenceKeys.APPEND_CUSTOM_MIRROR_PATH, site), appendPath)
        }
    }

    fun customMirrorHost(site: SiteType): String? {
        if (!useCustomMirror(site)) return null
        return customMirror(site).trim().toHttpUrlOrNull()?.host
    }

    private fun rootOf(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
    }

    fun baseUrl(site: SiteType): String {
        val custom = customMirror(site).trim()
        if (useCustomMirror(site) && custom.isNotBlank()) {
            val url = if (appendCustomPath(site)) custom else rootOf(custom)
            return SiteNetworks.forSite(site).normalizeBase(url)
        }
        return selectedMirrorUrl(site)
    }

    fun knownHosts(site: SiteType, defaults: List<String>): Array<String> {
        val current = baseUrl(site).toHttpUrlOrNull()?.host
        return (defaults + listOfNotNull(current)).distinct().toTypedArray()
    }

    fun homeUrl(site: SiteType): String {
        val custom = customMirror(site).trim()
        if (useCustomMirror(site) && custom.isNotBlank()) return custom
        val base = baseUrl(site)
        return if (site == SiteType.JAVCHU) base.trimEnd('/') else base
    }

    fun displayUrl(site: SiteType): String {
        val custom = customMirror(site).trim()
        if (useCustomMirror(site) && custom.isNotBlank()) return "$custom (Custom)"
        return baseUrl(site)
    }

    private fun proxyOwned(site: SiteType): Boolean =
        site == SiteType.HANIME || sp.contains(key(SettingsPreferenceKeys.PROXY_TYPE, site))

    private fun proxySite(site: SiteType): SiteType =
        if (proxyOwned(site)) site else SiteType.HANIME

    fun isProxyInherited(site: SiteType): Boolean = !proxyOwned(site)

    fun proxyType(site: SiteType): Int =
        sp.getInt(
            key(SettingsPreferenceKeys.PROXY_TYPE, proxySite(site)),
            HProxySelector.TYPE_SYSTEM
        )

    fun proxyIp(site: SiteType): String =
        sp.getString(key(SettingsPreferenceKeys.PROXY_IP, proxySite(site)), "").orEmpty()

    fun proxyPort(site: SiteType): Int =
        sp.getInt(key(SettingsPreferenceKeys.PROXY_PORT, proxySite(site)), -1)

    fun setProxy(site: SiteType, type: Int, ip: String, port: Int) {
        sp.edit(commit = true) {
            putInt(key(SettingsPreferenceKeys.PROXY_TYPE, site), type)
            putString(key(SettingsPreferenceKeys.PROXY_IP, site), ip)
            putInt(key(SettingsPreferenceKeys.PROXY_PORT, site), port)
        }
    }

    private fun dohOwned(site: SiteType): Boolean =
        site == SiteType.HANIME || sp.contains(key(SettingsPreferenceKeys.USE_DOH, site))

    private fun dohSite(site: SiteType): SiteType =
        if (dohOwned(site)) site else SiteType.HANIME

    fun isDohInherited(site: SiteType): Boolean = !dohOwned(site)

    fun useDoH(site: SiteType): Boolean =
        sp.getBoolean(key(SettingsPreferenceKeys.USE_DOH, dohSite(site)), false)

    fun dohPreset(site: SiteType): String =
        sp.getString(key(SettingsPreferenceKeys.DOH_PRESET, dohSite(site)), "alidns") ?: "alidns"

    fun dohCustomUrl(site: SiteType): String =
        sp.getString(key(SettingsPreferenceKeys.DOH_CUSTOM_URL, dohSite(site)), "").orEmpty()

    fun dohBootstrapIps(site: SiteType): String =
        sp.getString(key(SettingsPreferenceKeys.DOH_BOOTSTRAP_IPS, dohSite(site)), "").orEmpty()

    fun dohTimeoutSeconds(site: SiteType): Int =
        sp.getInt(key(SettingsPreferenceKeys.DOH_TIMEOUT_SECONDS, dohSite(site)), 10)

    fun setDoH(
        site: SiteType,
        enabled: Boolean,
        preset: String,
        customUrl: String,
        bootstrapIps: String,
        timeoutSeconds: Int,
    ) {
        sp.edit(commit = true) {
            putBoolean(key(SettingsPreferenceKeys.USE_DOH, site), enabled)
            putString(key(SettingsPreferenceKeys.DOH_PRESET, site), preset)
            putString(key(SettingsPreferenceKeys.DOH_CUSTOM_URL, site), customUrl)
            putString(key(SettingsPreferenceKeys.DOH_BOOTSTRAP_IPS, site), bootstrapIps)
            putInt(
                key(SettingsPreferenceKeys.DOH_TIMEOUT_SECONDS, site),
                timeoutSeconds.coerceIn(1, 60)
            )
        }
    }

    private fun hostsSite(site: SiteType): SiteType =
        if (site == SiteType.JAVCHU &&
            !sp.contains(key(SettingsPreferenceKeys.USE_BUILT_IN_HOSTS, site))
        ) SiteType.HANIME else site

    fun useBuiltInHosts(site: SiteType): Boolean =
        sp.getBoolean(key(SettingsPreferenceKeys.USE_BUILT_IN_HOSTS, hostsSite(site)), false)

    fun customHostsData(site: SiteType): String =
        sp.getString(key(SettingsPreferenceKeys.CUSTOM_HOSTS_DATA, hostsSite(site)), "").orEmpty()

    fun setUseBuiltInHosts(site: SiteType, enabled: Boolean) {
        sp.edit(commit = true) {
            putBoolean(key(SettingsPreferenceKeys.USE_BUILT_IN_HOSTS, site), enabled)
        }
    }

    fun setCustomHostsData(site: SiteType, data: String) {
        sp.edit(commit = true) {
            putString(key(SettingsPreferenceKeys.CUSTOM_HOSTS_DATA, site), data)
        }
    }
}
