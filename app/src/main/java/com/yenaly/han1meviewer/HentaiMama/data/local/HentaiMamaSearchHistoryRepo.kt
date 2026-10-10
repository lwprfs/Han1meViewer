package com.yenaly.han1meviewer.HentaiMama.data.local

import com.yenaly.han1meviewer.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HentaiMamaSearchHistoryEntry(
    val query: String,
    val genre: String? = null,
    val year: String? = null,
    val producer: String? = null,
    val order: String? = null,
    val genreSlug: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object HentaiMamaSearchHistoryRepo {

    private const val PREF_KEY = "hentaimama_search_history"
    private const val MAX_ENTRIES = 30

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): List<HentaiMamaSearchHistoryEntry> {
        val raw = Preferences.preferenceSp.getString(PREF_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<HentaiMamaSearchHistoryEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    fun push(entry: HentaiMamaSearchHistoryEntry) {
        if (entry.query.isBlank() &&
            entry.genre.isNullOrBlank() &&
            entry.year.isNullOrBlank() &&
            entry.producer.isNullOrBlank() &&
            entry.order.isNullOrBlank() &&
            entry.genreSlug.isNullOrBlank()
        ) return

        val existing = load().toMutableList()
        existing.removeAll { it == entry.copy(createdAt = it.createdAt) }
        existing.add(0, entry)
        val trimmed = existing.take(MAX_ENTRIES)
        Preferences.preferenceSp.edit()
            .putString(PREF_KEY, json.encodeToString(trimmed))
            .apply()
    }

    fun remove(entry: HentaiMamaSearchHistoryEntry) {
        val existing = load().toMutableList()
        existing.removeAll { it == entry.copy(createdAt = it.createdAt) }
        Preferences.preferenceSp.edit()
            .putString(PREF_KEY, json.encodeToString(existing))
            .apply()
    }

    fun clear() {
        Preferences.preferenceSp.edit().remove(PREF_KEY).apply()
    }
}
