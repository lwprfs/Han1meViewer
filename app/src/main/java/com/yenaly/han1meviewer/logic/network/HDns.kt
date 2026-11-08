package com.yenaly.han1meviewer.logic.network

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class HDns : Dns {

    private data class DohRuntimeConfig(
        val url: String,
        val bootstrapIps: List<String>,
        val timeoutSeconds: Int,
    )

    /** Parsed content of the "custom hosts" field. */
    data class HostsMapping(
        /** Plain IP lists (legacy format): applied to every host of the site. */
        val globalIps: List<String>,
        /** `host=ip,ip` lines: applied to that host only. */
        val hostIps: Map<String, List<String>>,
    )

    companion object {

        private val getchuIps = listOf("210.155.150.166", "210.155.150.145")

        private const val GETCHU_HOSTNAME = "www.getchu.com"

        private val dohCache = ConcurrentHashMap<DohRuntimeConfig, Dns>()

        private val mappingCache = ConcurrentHashMap<String, HostsMapping>()

        fun parseCustomIps(raw: String): List<String> {
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        /**
         * Accepted formats (one entry per line, `;` also separates entries):
         *  - `1.2.3.4, 5.6.7.8`        -> used for every host of the site (legacy format)
         *  - `host.example=1.2.3.4`    -> used for that host only
         */
        fun parseHostsMapping(raw: String): HostsMapping {
            val global = mutableListOf<String>()
            val perHost = LinkedHashMap<String, List<String>>()
            raw.split('\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val eq = line.indexOf('=')
                    if (eq > 0) {
                        val host = line.substring(0, eq).trim().lowercase()
                        val ips = parseCustomIps(line.substring(eq + 1).replace(' ', ','))
                        if (host.isNotEmpty() && ips.isNotEmpty()) perHost[host] = ips
                    } else {
                        global += parseCustomIps(line.replace(' ', ','))
                    }
                }
            return HostsMapping(global, perHost)
        }

        fun validateCustomHosts(raw: String): List<String> {
            val errors = mutableListOf<String>()
            if (raw.isBlank()) return errors
            val lines = raw.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
            var anyIp = false
            lines.forEach { line ->
                val eq = line.indexOf('=')
                val ipPart = if (eq > 0) {
                    if (line.substring(0, eq).isBlank()) {
                        errors.add("Missing host name: \"$line\"")
                    }
                    line.substring(eq + 1)
                } else {
                    line
                }
                val ips = parseCustomIps(ipPart.replace(' ', ','))
                if (ips.isEmpty()) {
                    errors.add("No IP addresses entered: \"$line\"")
                }
                ips.forEach { ip ->
                    anyIp = true
                    if (!isValidIpAddress(ip)) {
                        errors.add("Invalid IP address: \"$ip\"")
                    }
                }
            }
            if (!anyIp && errors.isEmpty()) {
                errors.add("No IP addresses entered")
            }
            return errors
        }

        private fun isValidIpAddress(ip: String): Boolean {
            return runCatching {
                val addr = InetAddress.getByName(ip)
                addr.hostAddress == ip || addr.hostAddress == ip.removePrefix("[")
                    .removeSuffix("]")
            }.getOrDefault(false)
        }

        private fun toAddresses(host: String, ips: List<String>): List<InetAddress> {
            return ips.map { InetAddress.getByAddress(host, InetAddress.getByName(it).address) }
        }

        private fun mappingFor(raw: String): HostsMapping {
            return mappingCache.getOrPut(raw) { parseHostsMapping(raw) }
        }

        /** IPs the user (or the built-in table) wants for [host] on [site], or null. */
        private fun mappedIps(site: SiteType, host: String): List<String>? {
            val owner = SiteNetworks.siteForHost(host, allowSubdomains = false) ?: return null
            if (!SiteNetworkPrefs.useBuiltInHosts(site)) return null
            val mapping = mappingFor(SiteNetworkPrefs.customHostsData(site))
            val lower = host.lowercase()
            val specific = mapping.hostIps[lower] ?: mapping.hostIps[lower.removePrefix("www.")]
            if (!specific.isNullOrEmpty()) return specific
            if (mapping.globalIps.isNotEmpty()) return mapping.globalIps
            return owner.builtInIps.takeIf { it.isNotEmpty() }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname == GETCHU_HOSTNAME) {
            return toAddresses(hostname, getchuIps)
        }

        val owner = SiteNetworks.siteForHost(hostname, allowSubdomains = false)
        val site = owner?.siteType ?: Preferences.siteType

        mappedIps(site, hostname)?.let { ips ->
            return toAddresses(hostname, ips)
        }

        // Never leak onion lookups to a DoH provider.
        if (hostname.endsWith(".onion")) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val dohUrl = DohConfig.resolveUrl(site)
        if (!dohUrl.isNullOrBlank()) {
            return runCatching { lookupByDoH(site, dohUrl, hostname) }
                .getOrElse {
                    Log.w("DOH", "lookup failed for $hostname: ${it.message}")
                    Dns.SYSTEM.lookup(hostname)
                }
        }

        return Dns.SYSTEM.lookup(hostname)
    }

    private fun lookupByDoH(site: SiteType, dohUrl: String, hostname: String): List<InetAddress> {
        val config = DohRuntimeConfig(
            url = dohUrl,
            bootstrapIps = DohConfig.bootstrapIps(site),
            timeoutSeconds = DohConfig.timeoutSeconds(site),
        )
        val dns = getOrCreateDohDns(config)
        return dns.lookup(hostname).also {
            Log.i("DOH", it.toString())
        }
    }

    /** DoH lookup using the DoH settings of [site] (defaults to the current site). */
    fun lookupByDoHOnly(
        hostname: String,
        site: SiteType = Preferences.siteType,
    ): List<InetAddress> {
        val dohUrl = DohConfig.resolveUrl(site) ?: error("DoH is disabled")
        return lookupByDoH(site, dohUrl, hostname)
    }

    private fun getOrCreateDohDns(config: DohRuntimeConfig): Dns {
        dohCache[config]?.let { return it }

        synchronized(dohCache) {
            dohCache[config]?.let { return it }

            val client = OkHttpClient.Builder()
                .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
            val bootstrapHosts = config.bootstrapIps.mapNotNull { ip ->
                runCatching { InetAddress.getByName(ip) }.getOrNull()
            }
            val dnsBuilder = DnsOverHttps.Builder()
                .client(client)
                .url(config.url.toHttpUrl())
                .includeIPv6(true)
                .post(false)
                .resolvePrivateAddresses(true)
                .resolvePublicAddresses(true)
            if (bootstrapHosts.isNotEmpty()) {
                dnsBuilder.bootstrapDnsHosts(bootstrapHosts)
            }
            val dns = dnsBuilder.build()

            dohCache[config] = dns
            return dns
        }
    }

    /**
     * The IPs the app would connect to for [host]. [site] is only used for hosts that do not
     * belong to any site (it defaults to the current site).
     */
    fun getCDNList(host: String, site: SiteType? = null): List<String> {
        if (host == GETCHU_HOSTNAME) {
            return getchuIps.distinct()
        }

        val owner = SiteNetworks.siteForHost(host, allowSubdomains = false)
        val effectiveSite = owner?.siteType ?: site ?: Preferences.siteType

        mappedIps(effectiveSite, host)?.let { ips ->
            return ips.distinct()
        }

        return runCatching {
            Dns.SYSTEM.lookup(host).map { it.hostAddress }.distinct()
        }.getOrElse {
            it.printStackTrace()
            emptyList()
        }
    }

}
