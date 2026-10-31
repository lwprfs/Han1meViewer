package com.yenaly.han1meviewer.FapFolder.data.remote

import android.util.Log
import androidx.core.content.edit
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderSecurePrefs
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderHtmlUtils
import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.Preferences
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class FapFolderCookieJar : CookieJar {

    companion object {
        private const val TAG = "FapFolderCookieJar"

        private val PRESERVED_NAMES = setOf(
            FapFolderConstants.COOKIE_PHPSESSID,
            FapFolderConstants.COOKIE_C_USER,
            FapFolderConstants.COOKIE_XS,

            "cf_clearance",
            "__cf_bm",
            "cf_chl_2",
            "cf_chl_prog",
            "cf_chl_rc_ni",
        )

        private val SEED_HOSTS = listOf(
            "fapfolder.club",
            "www.fapfolder.club",
        )

        private val FORBIDDEN_HOSTS = setOf(
            "fap.onl",
            "www.fap.onl",
        )

        private fun isForbiddenHost(host: String?): Boolean {
            if (host.isNullOrBlank()) return true
            val h = host.lowercase()
            if (h in FORBIDDEN_HOSTS) return true
            return h.endsWith(".r2.cloudflarestorage.com") ||
                    h == "r2.cloudflarestorage.com" ||
                    h.endsWith("mc.yandex.ru") ||
                    h.endsWith("yandex.ru") ||
                    h.endsWith("yandex.net") ||
                    h.endsWith("google-analytics.com") ||
                    h.endsWith("googletagmanager.com")
        }

        private val cookieStore:
                MutableMap<String, MutableMap<String, Cookie>> = ConcurrentHashMap()

        private fun key(c: Cookie): String =
            "${c.name}@${c.path.ifBlank { "/" }}"

        fun hasRequiredAuthCookie(): Boolean {
            var hasCUser = false
            var hasXs = false
            cookieStore.values.forEach { bucket ->
                bucket.values.forEach { c ->
                    if (c.name == FapFolderConstants.COOKIE_C_USER) hasCUser = true
                    if (c.name == FapFolderConstants.COOKIE_XS) hasXs = true
                }
            }
            return hasCUser && hasXs
        }

        fun snapshot(): Map<String, String> {
            val result = mutableMapOf<String, String>()
            cookieStore.values.forEach { bucket ->
                bucket.values.forEach { c ->
                    if (c.name in PRESERVED_NAMES && !result.containsKey(c.name)) {
                        result[c.name] = c.value
                    }
                }
            }
            return result
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        if (isForbiddenHost(host)) return emptyList()
        if (!url.isHttps) return emptyList()

        val now = System.currentTimeMillis()
        val bucket = cookieStore[host] ?: return emptyList()

        bucket.entries.removeAll { (_, c) ->
            c.expiresAt != Long.MAX_VALUE && c.expiresAt <= now
        }

        val requestPath = url.encodedPath.ifBlank { "/" }
        return bucket.values.filter { c ->
            val cookiePath = c.path.ifBlank { "/" }
            cookiePath == "/" ||
                    requestPath == cookiePath ||
                    requestPath.startsWith(
                        if (cookiePath.endsWith("/")) cookiePath else "$cookiePath/"
                    )
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        if (isForbiddenHost(host)) return

        val now = System.currentTimeMillis()
        val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }

        cookies.forEach { c ->
            if (c.name !in PRESERVED_NAMES) return@forEach
            val k = key(c)
            val expired = c.expiresAt != Long.MAX_VALUE && c.expiresAt <= now
            if (expired || c.value.isBlank()) {
                bucket.remove(k)
            } else {
                bucket[k] = c
            }
        }
        if (bucket.isEmpty()) cookieStore.remove(host)
    }

    fun seed(cookies: List<Cookie>) {
        val filtered = cookies.filter { it.name in PRESERVED_NAMES && it.value.isNotBlank() }
        if (filtered.isEmpty()) return
        SEED_HOSTS.forEach { host ->
            val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
            filtered.forEach { c ->
                bucket[key(c)] = c
            }
        }
    }

    fun seedFromSnapshot(snapshot: Map<String, String>) {
        if (snapshot.isEmpty()) return
        SEED_HOSTS.forEach { host ->
            val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
            snapshot.forEach { (name, value) ->
                if (name in PRESERVED_NAMES && value.isNotBlank()) {
                    val cookie = runCatching {
                        Cookie.Builder()
                            .domain(host)
                            .path("/")
                            .name(name)
                            .value(value)
                            .secure()
                            .build()
                    }.getOrNull() ?: return@forEach
                    bucket[key(cookie)] = cookie
                }
            }
        }
    }

    fun clearCookies() {
        cookieStore.clear()
    }

    fun persistToPreferences() {
        val snapshot = snapshot()
        val serialized = HJson.encodeToString(
            snapshot.map { (k, v) -> CookieEntry(k, v) }
        )
        FapFolderSecurePrefs.instance.edit(commit = true) {
            putString(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, serialized)
        }
    }

    fun restoreFromPreferences() {
        val encryptedRaw = FapFolderSecurePrefs.instance.getString(
            FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, null
        )
        val legacyRaw = if (encryptedRaw == null) {
            Preferences.preferenceSp.getString(
                FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, null
            )
        } else null

        val raw = encryptedRaw ?: legacyRaw ?: return
        val entries = runCatching {
            HJson.decodeFromString<List<CookieEntry>>(raw)
        }.getOrNull() ?: return
        seedFromSnapshot(entries.associate { it.name to it.value })

        if (encryptedRaw == null && legacyRaw != null) {
            FapFolderSecurePrefs.instance.edit {
                putString(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, legacyRaw)
            }
            Preferences.preferenceSp.edit {
                remove(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW)
            }
        }
    }

    fun clearPersistedPreferences() {
        FapFolderSecurePrefs.instance.edit {
            remove(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW)
        }
        Preferences.preferenceSp.edit {
            remove(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW)
        }
    }

    @Serializable
    private data class CookieEntry(
        val name: String,
        val value: String,
    )

    fun parseRawCookieString(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        raw.split(';').forEach { entry ->
            val trimmed = entry.trim()
            if (trimmed.isBlank()) return@forEach
            val name = trimmed.substringBefore('=').trim()
            val value = trimmed.substringAfter('=', "").trim()
            if (name.isBlank()) return@forEach
            if (name.lowercase() in setOf(
                    "path", "domain", "expires", "max-age", "secure", "httponly", "samesite"
                )
            ) {
                return@forEach
            }
            if (name in PRESERVED_NAMES) {
                result[name] = value
            }
        }
        return result
    }

    fun validateRawCookies(raw: String?): ValidationResult {
        if (raw.isNullOrBlank()) {
            return ValidationResult(false, 0, "No cookies provided")
        }
        val parsed = parseRawCookieString(raw)
        if (parsed.isEmpty()) {
            return ValidationResult(
                false, 0,
                "No recognized cookies. FapFolder needs PHPSESSID, c_user, and xs."
            )
        }
        val hasSession = FapFolderConstants.COOKIE_PHPSESSID in parsed ||
                parsed.keys.any { it.equals(FapFolderConstants.COOKIE_PHPSESSID, true) }
        val hasCUser = FapFolderConstants.COOKIE_C_USER in parsed
        val hasXs = FapFolderConstants.COOKIE_XS in parsed
        return when {
            !hasCUser || !hasXs ->
                ValidationResult(
                    false, parsed.size,
                    "Missing required cookies. Need c_user and xs (from a logged-in session)."
                )
            !hasSession ->
                ValidationResult(
                    true, parsed.size,
                    "Warning: PHPSESSID missing. Session may not persist."
                )
            else -> ValidationResult(true, parsed.size, "OK")
        }
    }

    fun isCloudflareChallengeBody(body: String?): Boolean =
        FapFolderHtmlUtils.looksLikeCloudflareChallenge(body)

    data class ValidationResult(
        val valid: Boolean,
        val cookieCount: Int,
        val message: String,
    )
}
