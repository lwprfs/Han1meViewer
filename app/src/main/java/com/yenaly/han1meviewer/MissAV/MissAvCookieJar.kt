package com.yenaly.han1meviewer.MissAV

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class MissAvCookieJar : CookieJar {

    companion object {
        private const val TAG = "MissAvCookieJar"
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()
        
        // Add stored cookies
        cookieStore[host]?.let { cookies.addAll(it) }
        
        // Add Cloudflare cookie from preferences
        val cfCookies = MissAvCloudflareCookieManager.getOkHttpCookies(host)
        cookies.addAll(cfCookies)
        
        Log.d(TAG, "loadForRequest: $host, cookies: ${cookies.size}")
        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        
        // Filter out expired cookies
        val validCookies = cookies.filter { 
            it.expiresAt == Long.MAX_VALUE || it.expiresAt > System.currentTimeMillis()
        }
        
        if (validCookies.isNotEmpty()) {
            cookieStore[host] = validCookies.toMutableList()
            Log.d(TAG, "saveFromResponse: $host, saved ${validCookies.size} cookies")
            
            // Check if we got a cf_clearance cookie
            val cfClearance = validCookies.firstOrNull { it.name == "cf_clearance" }
            if (cfClearance != null) {
                val cookieString = "${cfClearance.name}=${cfClearance.value}"
                MissAvCloudflareCookieManager.saveCloudflareCookie(cookieString)
                Log.d(TAG, "Saved cf_clearance from response")
            }
        }
    }

    fun clearCookies() {
        cookieStore.clear()
        MissAvCloudflareCookieManager.clearCloudflareCookie()
        Log.d(TAG, "Cleared all MissAV cookies")
    }
}