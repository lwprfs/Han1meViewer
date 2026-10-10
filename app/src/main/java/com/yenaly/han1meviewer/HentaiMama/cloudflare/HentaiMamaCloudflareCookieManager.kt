package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object HentaiMamaCloudflareCookieManager {

    private const val TAG = "HMCloudflareCookie"
    private const val PREF_PREFIX_COOKIE = "hm_cf_cookie"
    private const val PREF_PREFIX_EXPIRY = "hm_cf_expiry"
    private const val FALLBACK_EXPIRY_MS = 30 * 60 * 1000L

    private val cookieCache = ConcurrentHashMap<String, String>()

    private val rejectedCache = ConcurrentHashMap<String, String>()

    private fun cookieKey(host: String) = "${PREF_PREFIX_COOKIE}_$host"
    private fun expiryKey(host: String) = "${PREF_PREFIX_EXPIRY}_$host"

    private fun hostVariants(host: String): List<String> {
        val lower = host.lowercase()
        val bare = lower.removePrefix("www.")
        val www = "www.$bare"
        return listOf(lower, bare, www).distinct()
    }

    fun saveCloudflareCookie(host: String, cookieString: String) {
        val cfClearance = extractCfClearance(cookieString) ?: return
        val expiry = extractCookieExpiry(cookieString)
            ?: (System.currentTimeMillis() + FALLBACK_EXPIRY_MS)

        val variants = hostVariants(host)
        val editor = Preferences.preferenceSp.edit()
        variants.forEach { h ->
            cookieCache[h] = cfClearance
            rejectedCache.remove(h)
            editor.putString(cookieKey(h), cfClearance)
            editor.putLong(expiryKey(h), expiry)
        }
        editor.apply()

        Log.d(TAG, "Saved cf_clearance for variants=$variants")
    }

    fun getCloudflareCookie(host: String): String? {
        val variants = hostVariants(host)

        for (h in variants) {
            val rejected = rejectedCache[h]

            val cached = cookieCache[h]
            if (cached != null && cached != rejected) {
                if (h != host) cookieCache[host] = cached
                return cached
            }

            val cookie = Preferences.preferenceSp.getString(cookieKey(h), null) ?: continue
            if (cookie == rejected) continue

            val expiry = Preferences.preferenceSp.getLong(expiryKey(h), 0L)
            if (expiry > 0L && System.currentTimeMillis() > expiry) {
                Log.d(TAG, "Cookie for $h expired, clearing")
                clearCloudflareCookie(h)
                continue
            }

            cookieCache[h] = cookie
            if (h != host) cookieCache[host] = cookie
            return cookie
        }
        return null
    }

    fun markCurrentCookieRejected(host: String) {
        hostVariants(host).forEach { h ->
            val current = cookieCache[h]
                ?: Preferences.preferenceSp.getString(cookieKey(h), null)
                ?: return@forEach
            rejectedCache[h] = current
        }
        Log.d(TAG, "Marked cf_clearance for ${hostVariants(host)} as rejected")
    }

    fun clearCloudflareCookie(host: String) {
        val editor = Preferences.preferenceSp.edit()
        hostVariants(host).forEach { h ->
            cookieCache.remove(h)
            rejectedCache.remove(h)
            editor.remove(cookieKey(h))
            editor.remove(expiryKey(h))
        }
        editor.apply()
    }

    fun clearAllCloudflareCookies() {
        cookieCache.clear()
        rejectedCache.clear()
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
        val expiryStr = Regex("expires=([^;]+)", RegexOption.IGNORE_CASE)
            .find(cookieString)?.groupValues?.get(1)
            ?: return null
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                .parse(expiryStr.trim())?.time
        }.getOrNull()
    }
}
