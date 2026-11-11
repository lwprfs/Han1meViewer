package com.yenaly.han1meviewer.XHamster.data.remote

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.XHamster.cloudflare.XHamsterCloudflareCookieManager
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class XHamsterCookieJar : CookieJar {

    companion object {
        private const val TAG = "XHamsterCookieJar"
        private const val PREF_KEY = "xhamster_cookies_v1"
        private val AUTH_COOKIES = setOf("UID", "_id")
        private val cookieStore = mutableMapOf<String, MutableMap<String, Cookie>>()
        private var loaded = false

        @Synchronized
        private fun ensureLoaded() {
            if (loaded) return
            loaded = true
            val saved = runCatching {
                Preferences.preferenceSp.getStringSet(PREF_KEY, null)
            }.getOrNull() ?: return
            val now = System.currentTimeMillis()
            saved.forEach { line ->
                val host = line.substringBefore('\n')
                val raw = line.substringAfter('\n', "")
                val url = "https://$host/".toHttpUrlOrNull() ?: return@forEach
                val cookie = Cookie.parse(url, raw) ?: return@forEach
                if (cookie.expiresAt > now) {
                    cookieStore.getOrPut(host) { mutableMapOf() }[cookie.name] = cookie
                }
            }
        }

        @Synchronized
        private fun persist() {
            val lines = HashSet<String>()
            cookieStore.forEach { (host, bucket) ->
                bucket.values.forEach { lines += host + "\n" + it.toString() }
            }
            runCatching {
                Preferences.preferenceSp.edit().putStringSet(PREF_KEY, lines).apply()
            }
        }

        @Synchronized
        fun hasAuthCookies(): Boolean {
            ensureLoaded()
            val now = System.currentTimeMillis()
            return cookieStore.values.any { bucket ->
                bucket.values.any { it.name in AUTH_COOKIES && it.expiresAt > now }
            }
        }

        @Synchronized
        private fun cookiesFor(host: String): List<Cookie> {
            ensureLoaded()
            return cookieStore[host]?.values?.toList().orEmpty()
        }

        @Synchronized
        private fun store(host: String, valid: List<Cookie>, expired: List<Cookie>) {
            ensureLoaded()
            if (expired.isNotEmpty()) {
                cookieStore[host]?.let { bucket -> expired.forEach { bucket.remove(it.name) } }
            }
            if (valid.isNotEmpty()) {
                val bucket = cookieStore.getOrPut(host) { mutableMapOf() }
                valid.forEach { bucket[it.name] = it }
            }
            if (valid.isNotEmpty() || expired.isNotEmpty()) persist()
        }

        @Synchronized
        private fun clearAll() {
            cookieStore.clear()
            persist()
        }

        @Synchronized
        fun clearLoginCookies() {
            ensureLoaded()
            cookieStore.values.forEach { bucket ->
                bucket.keys.removeAll { name ->
                    !(name.startsWith("cf_") || name == "cookie_accept_v2")
                }
            }
            persist()
        }
    }

    private fun isSiteHost(host: String): Boolean =
        XHamsterConstants.HOSTNAME.any { host == it || host.endsWith(".$it") }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()

        cookies.addAll(cookiesFor(host))
        cookies.addAll(XHamsterCloudflareCookieManager.getOkHttpCookies(host))
        if (isSiteHost(host)) {
            cookies.add(
                Cookie.Builder()
                    .name("x_csrf_token")
                    .value("1")
                    .hostOnlyDomain(host)
                    .path("/")
                    .build()
            )
        }

        val deduped = cookies.distinctBy { it.name to it.domain }
        Log.d(TAG, "loadForRequest: $host, cookies=${deduped.size}")
        return deduped
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val now = System.currentTimeMillis()

        val valid = cookies.filter {
            it.expiresAt == Long.MAX_VALUE || it.expiresAt > now
        }

        val expired = cookies.filter { it.expiresAt <= now && it.expiresAt != Long.MAX_VALUE }
        store(host, valid, expired)

        valid.firstOrNull { it.name == "cf_clearance" }?.let { cf ->
            XHamsterCloudflareCookieManager.saveCloudflareCookie(
                host,
                "${cf.name}=${cf.value}"
            )
            Log.d(TAG, "Saved cf_clearance from response for $host")
        }
        Log.d(TAG, "saveFromResponse: $host, merged=${valid.size}")
    }

    fun clearCookies() {
        clearAll()
        XHamsterCloudflareCookieManager.clearAllCloudflareCookies()
        Log.d(TAG, "Cleared all XHamster cookies")
    }
}
