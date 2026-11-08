package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.Preferences
import okhttp3.internal.proxy.NullProxySelector
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Picks the proxy per request: hosts that belong to a site use that site's proxy setting,
 * everything else (CDNs, players, ...) follows the site that is currently open.
 */
class HProxySelector : ProxySelector() {

    private val alternative: ProxySelector = getDefault() ?: NullProxySelector

    companion object {
        const val TYPE_DIRECT = 0
        const val TYPE_SYSTEM = 1
        const val TYPE_HTTP = 2
        const val TYPE_SOCKS = 3

        private val ipv4Regex =
            Regex("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")

        fun validateIp(ip: String): Boolean {
            return ipv4Regex.matches(ip)
        }

        fun validatePort(port: Int): Boolean {
            return port in 0..65535
        }

        fun rebuildNetwork() {
            val properties = System.getProperties()
            when (Preferences.proxyType) {
                TYPE_HTTP, TYPE_SOCKS -> {
                    properties["proxySet"] = true.toString()
                    properties["proxyHost"] = Preferences.proxyIp
                    properties["proxyPort"] = Preferences.proxyPort.toString()
                }

                else -> {
                    properties["proxySet"] = false.toString()
                    properties["proxyHost"] = ""
                    properties["proxyPort"] = ""
                }
            }
        }
    }

    override fun select(uri: URI?): MutableList<Proxy> {
        val site = SiteNetworks.siteForHost(uri?.host)?.siteType ?: Preferences.siteType
        val type = SiteNetworkPrefs.proxyType(site)
        if (type == TYPE_HTTP || type == TYPE_SOCKS) {
            val ip = SiteNetworkPrefs.proxyIp(site)
            val port = SiteNetworkPrefs.proxyPort(site)
            if (ip.isNotBlank() && port != -1) {
                val inetAddress = InetAddress.getByName(ip)
                val socketAddress = InetSocketAddress(inetAddress, port)
                return mutableListOf(
                    Proxy(
                        if (type == TYPE_HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS,
                        socketAddress
                    )
                )
            }
        }

        if (type == TYPE_DIRECT) return mutableListOf(Proxy.NO_PROXY)

        return alternative.select(uri)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // Nothing to do: the proxy is re-evaluated on every request.
    }
}
