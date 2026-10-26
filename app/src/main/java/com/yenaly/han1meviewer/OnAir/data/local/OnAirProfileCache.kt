package com.yenaly.han1meviewer.OnAir.data.local

import com.yenaly.han1meviewer.OnAir.data.model.OnAirUserProfile
import java.util.concurrent.ConcurrentHashMap

object OnAirProfileCache {

    private const val DEFAULT_TTL_MS = 5 * 60 * 1000L

    data class Entry(val profile: OnAirUserProfile, val fetchedAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var currentUsername: String? = null

    fun put(username: String, profile: OnAirUserProfile) {
        if (username.isBlank()) return
        cache[username.lowercase()] = Entry(profile, System.currentTimeMillis())
    }

    fun get(username: String, ttlMs: Long = DEFAULT_TTL_MS): OnAirUserProfile? {
        if (username.isBlank()) return null
        val entry = cache[username.lowercase()] ?: return null
        if (System.currentTimeMillis() - entry.fetchedAt > ttlMs) {
            cache.remove(username.lowercase())
            return null
        }
        return entry.profile
    }

    fun setCurrentUsername(username: String?) {
        val trimmed = username?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null) {
            currentUsername = null
            return
        }
        currentUsername = trimmed
    }

    fun clearCurrentUsername() {
        currentUsername = null
    }

    fun getCurrentUsername(): String? = currentUsername

    fun current(): OnAirUserProfile? = currentUsername?.let(::get)

    fun invalidate(username: String? = null) {
        if (username == null) cache.clear()
        else cache.remove(username.lowercase())
    }

    fun clearAll() {
        cache.clear()
        currentUsername = null
    }
}
