package com.yenaly.han1meviewer.MissAV

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

object MissAvCloudflareCookieManager {

    private const val TAG = "MissAvCloudflareCookie"
    private const val PREF_MISSAV_CF_COOKIE = "missav_cf_cookie"
    private const val PREF_MISSAV_CF_EXPIRY = "missav_cf_expiry"
    private const val FALLBACK_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes

    @Volatile
    private var cachedCookie: String? = null

    fun saveCloudflareCookie(cookieString: String) {
        Log.d(TAG, "Saving Cloudflare cookie for MissAV")
        cachedCookie = cookieString
        
        val cfClearance = extractCfClearance(cookieString)
        var expiry = extractCookieExpiry(cookieString)
        
        if (expiry == null) {
            expiry = System.currentTimeMillis() + FALLBACK_EXPIRY_MS
            Log.d(TAG, "No expiry found, using fallback: 30 minutes")
        }
        
        if (cfClearance != null) {
            Preferences.preferenceSp.edit()
                .putString(PREF_MISSAV_CF_COOKIE, cfClearance)
                .apply()
            
            Preferences.preferenceSp.edit()
                .putLong(PREF_MISSAV_CF_EXPIRY, expiry)
                .apply()
            
            Log.d(TAG, "Saved cf_clearance: ${cfClearance.take(20)}...")
        }
    }

    fun getCloudflareCookie(): String? {
        if (cachedCookie != null) return cachedCookie
        
        val cookie = Preferences.preferenceSp.getString(PREF_MISSAV_CF_COOKIE, null)
        val expiry = Preferences.preferenceSp.getLong(PREF_MISSAV_CF_EXPIRY, 0)
        
        if (expiry > 0 && System.currentTimeMillis() > expiry) {
            Log.d(TAG, "Cloudflare cookie expired")
            clearCloudflareCookie()
            return null
        }
        
        if (!cookie.isNullOrEmpty()) {
            cachedCookie = cookie
            Log.d(TAG, "Loaded saved Cloudflare cookie")
            return cookie
        }
        
        return null
    }

    fun clearCloudflareCookie() {
        cachedCookie = null
        Preferences.preferenceSp.edit()
            .remove(PREF_MISSAV_CF_COOKIE)
            .remove(PREF_MISSAV_CF_EXPIRY)
            .apply()
        Log.d(TAG, "Cleared Cloudflare cookie")
    }

    fun hasValidCookie(): Boolean {
        val cookie = getCloudflareCookie()
        return !cookie.isNullOrEmpty()
    }

    fun getOkHttpCookies(host: String): List<Cookie> {
        val cookieString = getCloudflareCookie() ?: return emptyList()
        
        return try {
            val httpUrl = "https://$host".toHttpUrl()
            cookieString.split(';').mapNotNull { cookie ->
                val parts = cookie.trim().split('=', limit = 2)
                if (parts.size == 2) {
                    try {
                        Cookie.Builder()
                            .domain(host)
                            .path("/")  // REQUIRED
                            .name(parts[0].trim())
                            .value(parts[1].trim())
                            .build()
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cookie", e)
            emptyList()
        }
    }

    private fun extractCfClearance(cookieString: String): String? {
        val regex = Regex("cf_clearance=([^;]+)")
        return regex.find(cookieString)?.groupValues?.get(1)
    }

    private fun extractCookieExpiry(cookieString: String): Long? {
        val regex = Regex("expires=([^;]+)")
        val expiryStr = regex.find(cookieString)?.groupValues?.get(1) ?: return null
        return try {
            val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            formatter.parse(expiryStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    fun hasValidCookieForHost(host: String): Boolean {
        val cookie = getCloudflareCookie()
        if (cookie.isNullOrEmpty()) return false
        
        // Cookie is valid if it exists and hasn't expired
        val expiry = Preferences.preferenceSp.getLong(PREF_MISSAV_CF_EXPIRY, 0)
        return expiry == 0L || System.currentTimeMillis() < expiry
    }
}