package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class OnAirCookieJar : CookieJar {

    companion object {
        private const val TAG = "OnAirCookieJar"

        private val cookieStore:
                MutableMap<String, MutableMap<String, Cookie>> = ConcurrentHashMap()

        private fun key(c: Cookie): String = "${c.name}@${c.path.ifBlank { "/" }}"

        private fun looksLikeCaptchaSession(c: Cookie): Boolean {
            if (c.name != "session" && c.name != "laravel_session") return false
            val v = c.value
            if (v.length < 24) return false
            val lower = v.lowercase()
            if (lower.contains("captcha")) return true
            if (v.startsWith("eyJjYXB0Y2hh")) return true
            return false
        }

        private fun looksLikeAuthSession(c: Cookie): Boolean {
            if (c.name != "session" && c.name != "laravel_session") return false
            if (c.value.length < 24) return false
            return !looksLikeCaptchaSession(c)
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
        Log.d(
            TAG,
            "loadForRequest: $host path=$requestPath cookies=${applicable.size} " +
                    "names=${applicable.map { "${it.name}@${it.path}" }}",
        )
        return applicable
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val now = System.currentTimeMillis()
        val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
        cookies
            .filter { it.expiresAt == Long.MAX_VALUE || it.expiresAt > now }
            .forEach { c ->
                val k = key(c)
                val existing = bucket[k]
                if (looksLikeCaptchaSession(c) && existing != null &&
                    looksLikeAuthSession(existing)
                ) {
                    Log.w(
                        TAG,
                        "refusing to overwrite auth session with captcha session " +
                                "for ${c.name}@${c.path}",
                    )
                    return@forEach
                }
                bucket[k] = c
                Log.d(
                    TAG,
                    "saveFromResponse: $host <- ${c.name}@${c.path} " +
                            "value=${c.value.take(12)}…",
                )
            }
        Log.d(TAG, "saveFromResponse: $host total=${bucket.size} keys=${bucket.keys}")
    }

    fun seed(cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val host = cookie.domain.ifBlank {
                OnAirNetworkSafe.baseHost().orEmpty()
            }
            val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
            bucket[key(cookie)] = cookie
        }
        Log.d(TAG, "seed: ${cookies.size} cookies")
    }

    fun clearCookies() {
        cookieStore.clear()
        Log.d(TAG, "Cleared all OnAir cookies")
    }

    fun snapshotJson(): String {
        val array = JSONArray()
        val now = System.currentTimeMillis()
        cookieStore.forEach { (host, bucket) ->
            bucket.values.forEach { c ->
                if (c.expiresAt != Long.MAX_VALUE && c.expiresAt <= now) return@forEach
                val obj = JSONObject()
                obj.put("domain", c.domain.ifBlank { host })
                obj.put("path", c.path.ifBlank { "/" })
                obj.put("name", c.name)
                obj.put("value", c.value)
                obj.put("secure", c.secure)
                obj.put("httpOnly", c.httpOnly)
                obj.put(
                    "expiresAt",
                    if (c.expiresAt == Long.MAX_VALUE) 0L else c.expiresAt,
                )
                array.put(obj)
            }
        }
        return array.toString()
    }

    fun currentCookieNames(): Set<String> {
        val names = mutableSetOf<String>()
        cookieStore.values.forEach { bucket ->
            bucket.values.forEach { names.add(it.name) }
        }
        return names
    }

    private object OnAirNetworkSafe {
        fun baseHost(): String? =
            runCatching {
                OnAirNetwork.baseUrl
                    .substringAfter("://")
                    .substringBefore("/")
                    .substringBefore(":")
            }.getOrNull()
    }
}
