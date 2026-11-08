package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType

data class DohPreset(
    val key: String,
    val title: String,
    val url: String,
    val bootstrapIps: List<String>,
)

/**
 * DoH settings are stored per site (see [SiteNetworkPrefs]); every accessor takes the site and
 * defaults to the site that is currently open.
 */
object DohConfig {
    val presets = listOf(
        DohPreset(
            key = "alidns",
            title = "AliDNS",
            url = "https://dns.alidns.com/dns-query",
            bootstrapIps = listOf("223.5.5.5", "223.6.6.6"),
        ),
        DohPreset(
            key = "dnspod",
            title = "DNSPod",
            url = "https://doh.pub/dns-query",
            bootstrapIps = listOf("1.12.12.12", "120.53.53.53"),
        ),
        DohPreset(
            key = "cloudflare",
            title = "Cloudflare",
            url = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
        ),
    )

    fun selectedPreset(site: SiteType = Preferences.siteType): DohPreset =
        presets.firstOrNull { it.key == SiteNetworkPrefs.dohPreset(site) } ?: presets.first()

    fun customUrl(site: SiteType = Preferences.siteType): String =
        SiteNetworkPrefs.dohCustomUrl(site).trim()

    fun bootstrapIps(site: SiteType = Preferences.siteType): List<String> {
        val customBootstrapIps = SiteNetworkPrefs.dohBootstrapIps(site)
            .split(',', '\n', ';', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (customBootstrapIps.isNotEmpty()) return customBootstrapIps
        if (SiteNetworkPrefs.dohPreset(site) == "custom") return emptyList()
        return selectedPreset(site).bootstrapIps
    }

    fun timeoutSeconds(site: SiteType = Preferences.siteType): Int =
        SiteNetworkPrefs.dohTimeoutSeconds(site).coerceIn(1, 60)

    fun resolveUrl(site: SiteType = Preferences.siteType): String? {
        if (!SiteNetworkPrefs.useDoH(site)) return null
        return when (SiteNetworkPrefs.dohPreset(site)) {
            "custom" -> customUrl(site).takeIf { it.isNotBlank() }
            else -> selectedPreset(site).url
        }
    }
}
