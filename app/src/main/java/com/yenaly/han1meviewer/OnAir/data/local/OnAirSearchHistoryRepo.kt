package com.yenaly.han1meviewer.OnAir.data.local

import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.Preferences
import kotlinx.serialization.Serializable

@Serializable
data class OnAirSearchHistoryEntry(
    val query: String = "",
    val field: String = "title",
    val length: String = "any",
    val date: String = "any",
    val sort: String = "newest",
    val tag: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object OnAirSearchHistoryRepo {

    private const val PREF_KEY = "onair_search_history"
    private const val MAX_ENTRIES = 30

    fun load(): List<OnAirSearchHistoryEntry> {
        val raw = Preferences.preferenceSp.getString(PREF_KEY, null) ?: return emptyList()
        return runCatching {
            HJson.decodeFromString<List<OnAirSearchHistoryEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    fun push(entry: OnAirSearchHistoryEntry) {
        val hasAnyFilter = entry.query.isNotBlank() ||
                entry.tag != null ||
                entry.field != "title" ||
                entry.length != "any" ||
                entry.date != "any" ||
                entry.sort != "newest"
        if (!hasAnyFilter) return

        val existing = load().toMutableList()
        existing.removeAll {
            it.query == entry.query &&
                    it.field == entry.field &&
                    it.length == entry.length &&
                    it.date == entry.date &&
                    it.sort == entry.sort &&
                    it.tag == entry.tag
        }
        existing.add(0, entry)
        val trimmed = existing.take(MAX_ENTRIES)
        Preferences.preferenceSp.edit()
            .putString(PREF_KEY, HJson.encodeToString(trimmed))
            .apply()
    }

    fun remove(entry: OnAirSearchHistoryEntry) {
        val existing = load().toMutableList()
        existing.removeAll {
            it.query == entry.query &&
                    it.field == entry.field &&
                    it.length == entry.length &&
                    it.date == entry.date &&
                    it.sort == entry.sort &&
                    it.tag == entry.tag &&
                    it.createdAt == entry.createdAt
        }
        Preferences.preferenceSp.edit()
            .putString(PREF_KEY, HJson.encodeToString(existing))
            .apply()
    }

    fun clear() {
        Preferences.preferenceSp.edit().remove(PREF_KEY).apply()
    }
}
