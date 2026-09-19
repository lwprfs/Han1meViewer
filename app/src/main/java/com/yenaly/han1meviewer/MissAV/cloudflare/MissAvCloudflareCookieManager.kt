package com.yenaly.han1meviewer.MissAV.cloudflare
import android.util.Log
import com.yenaly.han1meviewer.Preferences
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object MissAvCloudflareCookieManager {

    private const val TAG = "MissAvCloudflareCookie"
    private const val PREF_PREFIX_COOKIE = "missav_cf_cookie"
    private const val PREF_PREFIX_EXPIRY = "missav_cf_expiry"
    private const val FALLBACK_EXPIRY_MS = 30 * 60 * 1000L

    private val cookieCache = ConcurrentHashMap<String, String>()

    private fun cookieKey(host: String) = "${PREF_PREFIX_COOKIE}_$host"
    private fun expiryKey(host: String) = "${PREF_PREFIX_EXPIRY}_$host"

    fun saveCloudflareCookie(host: String, cookieString: String) {
        val cfClearance = extractCfClearance(cookieString) ?: return
        val expiry = extractCookieExpiry(cookieString)
            ?: (System.currentTimeMillis() + FALLBACK_EXPIRY_MS)

        cookieCache[host] = cfClearance
        Preferences.preferenceSp.edit()
            .putString(cookieKey(host), cfClearance)
            .putLong(expiryKey(host), expiry)
            .apply()

        Log.d(TAG, "Saved cf_clearance for $host")
    }

    fun getCloudflareCookie(host: String): String? {
        cookieCache[host]?.let { return it }

        val cookie = Preferences.preferenceSp.getString(cookieKey(host), null) ?: return null
        val expiry = Preferences.preferenceSp.getLong(expiryKey(host), 0L)

        if (expiry > 0L && System.currentTimeMillis() > expiry) {
            Log.d(TAG, "Cookie for $host expired, clearing")
            clearCloudflareCookie(host)
            return null
        }

        cookieCache[host] = cookie
        return cookie
    }

    fun clearCloudflareCookie(host: String) {
        cookieCache.remove(host)
        Preferences.preferenceSp.edit()
            .remove(cookieKey(host))
            .remove(expiryKey(host))
            .apply()
    }

    fun clearAllCloudflareCookies() {
        cookieCache.clear()
        val prefs = Preferences.preferenceSp
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("${PREF_PREFIX_COOKIE}_") ||
                key.startsWith("${PREF_PREFIX_EXPIRY}_")
            ) {
                editor.remove(key)
            }
        }
        editor.apply()
        Log.d(TAG, "Cleared all Cloudflare cookies")
    }

    fun getOkHttpCookies(host: String): List<Cookie> {
        val cfClearance = getCloudflareCookie(host) ?: return emptyList()
        return try {
            listOf(
                Cookie.Builder()
                    .domain(host)
                    .path("/")
                    .name("cf_clearance")
                    .value(cfClearance)
                    .build()
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to build cookie for $host", e)
            emptyList()
        }
    }

    fun hasValidCookieForHost(host: String): Boolean =
        getCloudflareCookie(host) != null

    fun hasValidCookieForUrl(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return hasValidCookieForHost(host)
    }

    private fun extractCfClearance(cookieString: String): String? =
        Regex("cf_clearance=([^;]+)").find(cookieString)?.groupValues?.get(1)

    private fun extractCookieExpiry(cookieString: String): Long? {
        val expiryStr = Regex("expires=([^;]+)").find(cookieString)?.groupValues?.get(1)
            ?: return null
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                .parse(expiryStr)?.time
        }.getOrNull()
    }
}