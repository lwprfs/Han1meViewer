package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.SiteType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One selectable mirror (domain) of a site. */
data class MirrorNode(
    val host: String,
    /** URL in the same style the site's code expects (trailing slash or not). */
    val baseUrl: String,
    /** `https://<host>/status.html` health endpoint when the hub provides one. */
    val statusUrl: String?,
)

/**
 * Everything the network layer needs to know about one site of the app.
 *
 * To add a new site later:
 *  1. add a value to `SiteType`,
 *  2. append a [SiteNetworkProfile] to [SiteNetworks.profiles].
 *
 * Mirrors from [SiteMirrorCatalog] are attached automatically through [catalogKey].
 */
class SiteNetworkProfile(
    val siteType: SiteType,
    val displayName: String,
    /** Key into [SiteMirrorCatalog], or null when the hub has no mirrors for this site. */
    private val catalogKey: String?,
    /** Mirrors the app already shipped with (first one is the default). */
    private val defaultUrls: () -> List<String>,
    /** Whether this site's code expects its base URL to end with `/`. */
    val trailingSlash: Boolean,
    /** Whether the site sits behind Cloudflare (enables the built-in Cloudflare IP mapping). */
    val cloudflareFronted: Boolean,
) {

    val mirrors: List<MirrorNode> by lazy { buildMirrors() }

    val defaultBaseUrl: String
        get() = mirrors.first().baseUrl

    /** IPs used by the "built-in DNS mapping" when the user has not typed custom ones. */
    val builtInIps: List<String>
        get() = if (cloudflareFronted) SiteNetworks.CLOUDFLARE_IPS else emptyList()

    fun normalizeBase(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trailingSlash) "$trimmed/" else trimmed
    }

    fun findMirror(url: String): MirrorNode? {
        val host = url.trim().toHttpUrlOrNull()?.host ?: return null
        return mirrors.firstOrNull { it.host.equals(host, ignoreCase = true) }
    }

    private fun buildMirrors(): List<MirrorNode> {
        val result = LinkedHashMap<String, MirrorNode>()
        defaultUrls().forEach { url ->
            val host = url.toHttpUrlOrNull()?.host ?: return@forEach
            if (host !in result) {
                result[host] = MirrorNode(host, normalizeBase(url), null)
            }
        }
        val hub = catalogKey?.let { SiteMirrorCatalog.find(it) }
        hub?.hosts?.forEach { host ->
            if (host !in result) {
                result[host] = MirrorNode(host, normalizeBase("https://$host"), hub.statusUrl(host))
            }
        }
        return result.values.toList()
    }
}

object SiteNetworks {

    val CLOUDFLARE_IPS = listOf(
        "172.64.229.154", "162.159.0.1", "108.162.192.1", "172.64.33.1", "104.19.0.1",
        "2606:4700:3035::ac43:bb8d", "2606:4700:3030::6815:746", "2606:4700:3030::6815:714"
    )

    val profiles: List<SiteNetworkProfile> = listOf(
        SiteNetworkProfile(
            siteType = SiteType.HANIME,
            displayName = "Hanime1",
            catalogKey = "hanime1",
            defaultUrls = {
                listOf("https://hanime1.me/", "https://hanime1.com/", "https://hanimeone.me/")
            },
            trailingSlash = true,
            cloudflareFronted = true,
        ),
        SiteNetworkProfile(
            siteType = SiteType.JAVCHU,
            displayName = "Javchu",
            catalogKey = null,
            defaultUrls = { listOf("https://javchu.com/") },
            trailingSlash = true,
            cloudflareFronted = true,
        ),
        SiteNetworkProfile(
            siteType = SiteType.MISSAV,
            displayName = "MissAV",
            catalogKey = "missav",
            defaultUrls = {
                listOf("https://missav.ws/", "https://missav.live/", "https://missav.ai/")
            },
            trailingSlash = true,
            cloudflareFronted = true,
        ),
        SiteNetworkProfile(
            siteType = SiteType.HENTAIMAMA,
            displayName = "HentaiMama",
            catalogKey = null,
            defaultUrls = {
                listOf(
                    "https://hentaimama.io",
                    "https://hentaimama.com",
                    "https://hentaimama.net",
                )
            },
            trailingSlash = false,
            cloudflareFronted = true,
        ),
        SiteNetworkProfile(
            siteType = SiteType.ONAIR,
            displayName = "OnAir",
            catalogKey = null,
            defaultUrls = { OnAirConstants.ONAIR_URL.toList() },
            trailingSlash = true,
            cloudflareFronted = false,
        ),
        SiteNetworkProfile(
            siteType = SiteType.FAPFOLDER,
            displayName = "FapFolder",
            catalogKey = null,
            defaultUrls = { listOf("https://fapfolder.club/") },
            trailingSlash = true,
            cloudflareFronted = true,
        ),
        SiteNetworkProfile(
            siteType = SiteType.SUPJAV,
            displayName = "SupJav",
            catalogKey = null,
            defaultUrls = { listOf("https://supjav.com", "https://supjav.fun") },
            trailingSlash = false,
            cloudflareFronted = true,
        ),
    )

    /** Every site the mirror hub knows about, including ones this app has no client for yet. */
    val hubSites: List<HubSite>
        get() = SiteMirrorCatalog.sites

    fun forSite(type: SiteType): SiteNetworkProfile =
        profiles.firstOrNull { it.siteType == type } ?: profiles.first()

    private val hostIndex: Map<String, SiteNetworkProfile> by lazy {
        val map = LinkedHashMap<String, SiteNetworkProfile>()
        profiles.forEach { profile ->
            profile.mirrors.forEach { mirror ->
                val key = normalizeHost(mirror.host)
                if (key !in map) map[key] = profile
            }
        }
        map
    }

    /**
     * Which site a host belongs to.
     *
     * Matches every known mirror (with or without `www.`), the custom mirror the user typed
     * for a site, and - when [allowSubdomains] is set - subdomains of those hosts.
     * Returns null for CDN / third-party hosts.
     */
    fun siteForHost(host: String?, allowSubdomains: Boolean = true): SiteNetworkProfile? {
        if (host.isNullOrBlank()) return null
        val normalized = normalizeHost(host)
        hostIndex[normalized]?.let { return it }
        profiles.forEach { profile ->
            val custom = SiteNetworkPrefs.customMirrorHost(profile.siteType)
            if (custom != null && normalizeHost(custom) == normalized) return profile
        }
        if (allowSubdomains) {
            for ((mirrorHost, profile) in hostIndex) {
                if (normalized.endsWith(".$mirrorHost")) return profile
            }
        }
        return null
    }

    private fun normalizeHost(host: String): String = host.trim().lowercase().removePrefix("www.")
}
