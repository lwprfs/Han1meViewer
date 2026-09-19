package com.yenaly.han1meviewer.MissAV.data.remote
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

import com.yenaly.han1meviewer.MissAV.cloudflare.MissAvCloudflareCookieManager
class MissAvCookieJar : CookieJar {

    companion object {
        private const val TAG = "MissAvCookieJar"
        private val cookieStore = mutableMapOf<String, MutableMap<String, Cookie>>()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()

        cookieStore[host]?.values?.let { cookies.addAll(it) }

        val cfCookies = MissAvCloudflareCookieManager.getOkHttpCookies(host)
        cookies.addAll(cfCookies)

        Log.d(TAG, "loadForRequest: $host, cookies: ${cookies.size}")
        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val now = System.currentTimeMillis()

        val validCookies = cookies.filter {
            it.expiresAt == Long.MAX_VALUE || it.expiresAt > now
        }
        if (validCookies.isEmpty()) return

        val hostMap = cookieStore.getOrPut(host) { mutableMapOf() }

        validCookies.forEach { cookie ->
            hostMap[cookie.name] = cookie
        }

        Log.d(TAG, "saveFromResponse: $host, merged ${validCookies.size}, total ${hostMap.size}")

        validCookies.firstOrNull { it.name == "cf_clearance" }?.let { cf ->
            MissAvCloudflareCookieManager.saveCloudflareCookie(
                host,
                "${cf.name}=${cf.value}"
            )
            Log.d(TAG, "Saved cf_clearance from response for $host")
        }
    }

    fun clearCookies() {
        cookieStore.clear()
        MissAvCloudflareCookieManager.clearAllCloudflareCookies()
        Log.d(TAG, "Cleared all MissAV cookies")
    }
}