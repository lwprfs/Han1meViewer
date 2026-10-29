package com.yenaly.han1meviewer.FapFolder.data.remote

import android.util.Log
import androidx.core.content.edit
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
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
        )

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
        val now = System.currentTimeMillis()
        val bucket = cookieStore[host] ?: return emptyList()

        bucket.entries.removeAll { (_, c) ->
            c.expiresAt != Long.MAX_VALUE && c.expiresAt <= now
        }

        val requestPath = url.encodedPath.ifBlank { "/" }
        val applicable = bucket.values.filter { c ->
            val cookiePath = c.path.ifBlank { "/" }
            cookiePath == "/" ||
                    requestPath == cookiePath ||
                    requestPath.startsWith(
                        if (cookiePath.endsWith("/")) cookiePath else "$cookiePath/"
                    )
        }

        return applicable
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val now = System.currentTimeMillis()

        val filtered = cookies.filter { c ->
            c.name in PRESERVED_NAMES &&
                    (c.expiresAt == Long.MAX_VALUE || c.expiresAt > now)
        }
        if (filtered.isEmpty()) return

        val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
        filtered.forEach { c ->
            bucket[key(c)] = c
        }
    }

    fun seed(cookies: List<Cookie>) {
        val host = "fapfolder.club"
        val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
        cookies.filter { it.name in PRESERVED_NAMES }.forEach { c ->
            bucket[key(c)] = c
        }
    }

    fun seedFromSnapshot(snapshot: Map<String, String>) {
        if (snapshot.isEmpty()) return
        val host = "fapfolder.club"
        val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
        snapshot.forEach { (name, value) ->
            if (name in PRESERVED_NAMES && value.isNotBlank()) {
                val cookie = runCatching {
                    Cookie.Builder()
                        .domain(host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }.getOrNull() ?: return@forEach
                bucket[key(cookie)] = cookie
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
        Preferences.preferenceSp.edit(commit = true) {
            putString(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, serialized)
        }
    }

    fun restoreFromPreferences() {
        val raw = Preferences.preferenceSp.getString(
            FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, null
        ) ?: return
        val entries = runCatching {
            HJson.decodeFromString<List<CookieEntry>>(raw)
        }.getOrNull() ?: return
        seedFromSnapshot(entries.associate { it.name to it.value })
    }

    fun clearPersistedPreferences() {
        Preferences.preferenceSp.edit { remove(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW) }
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
