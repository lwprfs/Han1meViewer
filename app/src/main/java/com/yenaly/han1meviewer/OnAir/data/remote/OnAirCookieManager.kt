package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

data class OnAirCookieValidationResult(
    val valid: Boolean,
    val cookieCount: Int,
    val message: String,
)

object OnAirCookieManager {

    private const val TAG = "OnAirCookieManager"
    private const val PREF_ONAIR_COOKIES = "onair_cookies_raw"

    private val requiredCookieNames = setOf("session", "laravel_session")

    private val junkCookieNames = setOf(
        "path", "domain", "expires", "max-age",
        "secure", "httponly", "samesite",
    )

    private val _cookiesFlow = MutableStateFlow(readRaw())
    val cookiesFlow: StateFlow<String> = _cookiesFlow.asStateFlow()

    private fun readRaw(): String =
        Preferences.preferenceSp.getString(PREF_ONAIR_COOKIES, "").orEmpty()

    val cookiesRaw: String
        get() = _cookiesFlow.value

    val hasCookies: Boolean
        get() = _cookiesFlow.value.isNotBlank()

    val hasAdPass: Boolean
        get() = _cookiesFlow.value.contains("ad_pass=")

    fun saveCookies(raw: String) {
        Preferences.preferenceSp.edit(commit = true) {
            putString(PREF_ONAIR_COOKIES, raw)
        }
        _cookiesFlow.value = raw
        OnAirNetwork.refreshCookieJar()
        val parsed = parseToCookies(raw, OnAirNetwork.baseUrl)
        Log.d(
            TAG,
            "saveCookies: ${raw.length} chars, parsed=${parsed.size} " +
                    "names=${parsed.map { "${it.name}@${it.path}" }} " +
                    "jarNames=${OnAirNetwork.snapshotCookies().length}",
        )
    }

    fun persistOnly(raw: String) {
        Preferences.preferenceSp.edit(commit = true) {
            putString(PREF_ONAIR_COOKIES, raw)
        }
        _cookiesFlow.value = raw
        Log.d(TAG, "persistOnly: ${raw.length} chars")
    }

    fun replaceWithJarSnapshot() {
        val snapshot = OnAirNetwork.snapshotCookies()
        if (snapshot.isBlank()) {
            Log.w(TAG, "replaceWithJarSnapshot: jar is empty, skipping")
            return
        }
        persistOnly(snapshot)
        Log.d(
            TAG,
            "replaceWithJarSnapshot: wrote ${snapshot.length} chars " +
                    "names=${parseToCookies(snapshot, OnAirNetwork.baseUrl).map { it.name }}",
        )
    }

    fun clearCookies() {
        Preferences.preferenceSp.edit { remove(PREF_ONAIR_COOKIES) }
        OnAirNetwork.clearCookies()
        _cookiesFlow.value = ""
        Log.d(TAG, "clearCookies")
    }

    fun detectFormat(raw: String): CookieFormat {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return CookieFormat.UNKNOWN
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) return CookieFormat.JSON
        if (trimmed.startsWith("#") || trimmed.lines().any { it.contains("\t") }) {
            return CookieFormat.NETSCAPE
        }
        if (trimmed.contains('=')) return CookieFormat.RAW_STRING
        return CookieFormat.UNKNOWN
    }

    fun validate(raw: String): OnAirCookieValidationResult {
        if (raw.isBlank()) {
            return OnAirCookieValidationResult(false, 0, "No cookies provided")
        }
        val cookies = parseToCookies(raw, OnAirNetwork.baseUrl)
        if (cookies.isEmpty()) {
            return OnAirCookieValidationResult(
                false, 0, "Could not parse any cookies from the input"
            )
        }
        val names = cookies.map { it.name }.toSet()
        val hasSession = requiredCookieNames.any { it in names }
        if (!hasSession) {
            return OnAirCookieValidationResult(
                false,
                cookies.size,
                "Missing required session cookie (${requiredCookieNames.joinToString(" / ")})",
            )
        }
        return OnAirCookieValidationResult(true, cookies.size, "OK")
    }

    fun parseToCookies(raw: String, baseUrl: String): List<Cookie> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val host = baseUrl.toHttpUrlOrNull()?.host ?: return emptyList()
        return when (detectFormat(trimmed)) {
            CookieFormat.JSON -> parseJsonCookies(trimmed, host)
            CookieFormat.NETSCAPE -> parseNetscapeCookies(trimmed, host)
            CookieFormat.RAW_STRING -> parseRawCookieString(trimmed, host)
            CookieFormat.UNKNOWN -> emptyList()
        }
    }

    private fun parseJsonCookies(raw: String, host: String): List<Cookie> {
        val result = mutableListOf<Cookie>()
        try {
            val trimmed = raw.trim()
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("cookies") ?: JSONArray().put(obj)
            }
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val value = obj.optString("value")
                val domain = obj.optString("domain").takeIf { it.isNotBlank() } ?: host
                val path = obj.optString("path").takeIf { it.isNotBlank() } ?: "/"
                val secure = obj.optBoolean("secure", false)
                val httpOnly = obj.optBoolean("httpOnly", false)

                val expiry = when {
                    obj.has("expiresAt") -> {
                        val v = obj.optLong("expiresAt", 0L)
                        if (v <= 0L) Long.MAX_VALUE else v
                    }
                    obj.has("expirationDate") -> {
                        val v = obj.optLong("expirationDate", 0L)
                        if (v <= 0L) Long.MAX_VALUE else v * 1000L
                    }
                    else -> Long.MAX_VALUE
                }

                runCatching {
                    Cookie.Builder()
                        .domain(domain.removePrefix("."))
                        .path(path)
                        .name(name)
                        .value(value)
                        .apply {
                            if (secure) secure()
                            if (httpOnly) httpOnly()
                            if (expiry != Long.MAX_VALUE) expiresAt(expiry)
                        }
                        .build()
                }.onSuccess { result.add(it) }
                    .onFailure {
                        Log.w(TAG, "parseJsonCookies skip $name: ${it.message}")
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseJsonCookies failed: ${e.message}")
        }
        return result
    }

    private fun parseNetscapeCookies(raw: String, host: String): List<Cookie> {
        val result = mutableListOf<Cookie>()
        raw.lines().forEach { line ->
            if (line.isBlank() || line.startsWith("# ")) return@forEach
            if (line.startsWith("#HttpOnly_")) {
                processNetscapeLine(line.removePrefix("#HttpOnly_"), result, host)
            } else if (!line.startsWith("#")) {
                processNetscapeLine(line, result, host)
            }
        }
        return result
    }

    private fun processNetscapeLine(line: String, out: MutableList<Cookie>, host: String) {
        val parts = line.split("\t")
        if (parts.size < 7) return
        val domain = parts[0].trim().removePrefix(".").ifBlank { host }
        val path = parts[2].trim().ifBlank { "/" }
        val secure = parts[3].trim().equals("TRUE", ignoreCase = true)
        val expiresAtSec = parts[4].trim().toLongOrNull() ?: 0L
        val name = parts[5].trim()
        val value = parts[6].trim()
        if (name.isBlank()) return
        runCatching {
            Cookie.Builder()
                .domain(domain)
                .path(path)
                .name(name)
                .value(value)
                .apply {
                    if (secure) secure()
                    if (expiresAtSec > 0L) expiresAt(expiresAtSec * 1000L)
                }
                .build()
        }.onSuccess { out.add(it) }
            .onFailure { Log.w(TAG, "processNetscapeLine skip $name: ${it.message}") }
    }

    private fun parseRawCookieString(raw: String, host: String): List<Cookie> {
        val result = mutableListOf<Cookie>()
        raw.split(';').forEach { entry ->
            val trimmed = entry.trim()
            if (trimmed.isBlank()) return@forEach
            val name = trimmed.substringBefore('=').trim()
            val value = trimmed.substringAfter('=', "").trim()
            if (name.isBlank()) return@forEach
            if (name.lowercase() in junkCookieNames) return@forEach
            runCatching {
                Cookie.Builder()
                    .domain(host)
                    .path("/")
                    .name(name)
                    .value(value)
                    .build()
            }.onSuccess { result.add(it) }
                .onFailure { Log.w(TAG, "parseRawCookieString skip $name: ${it.message}") }
        }
        return result
    }
}

enum class CookieFormat {
    JSON,
    NETSCAPE,
    RAW_STRING,
    UNKNOWN,
}
