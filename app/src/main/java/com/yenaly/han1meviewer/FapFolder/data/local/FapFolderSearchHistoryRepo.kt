package com.yenaly.han1meviewer.FapFolder.data.local

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchHistoryEntry
import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.Preferences

object FapFolderSearchHistoryRepo {

    private const val MAX_ENTRIES = 30

    fun load(): List<FapFolderSearchHistoryEntry> {
        val raw = Preferences.preferenceSp.getString(
            FapFolderConstants.PREF_FAPFOLDER_SEARCH_HISTORY, null
        ) ?: return emptyList()
        return runCatching {
            HJson.decodeFromString<List<FapFolderSearchHistoryEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    fun push(entry: FapFolderSearchHistoryEntry) {
        val trimmedQuery = entry.query.trim()
        if (trimmedQuery.isEmpty() && entry.tab == "posts" && !entry.isHashtag) return

        val normalized = entry.copy(query = trimmedQuery)
        val existing = load().toMutableList()
        existing.removeAll {
            it.query.equals(normalized.query, ignoreCase = true) &&
                    it.isHashtag == normalized.isHashtag &&
                    it.tab == normalized.tab
        }
        existing.add(0, normalized)
        val trimmed = existing.take(MAX_ENTRIES)
        Preferences.preferenceSp.edit()
            .putString(
                FapFolderConstants.PREF_FAPFOLDER_SEARCH_HISTORY,
                HJson.encodeToString(trimmed)
            )
            .apply()
    }

    fun remove(entry: FapFolderSearchHistoryEntry) {
        val existing = load().toMutableList()
        existing.removeAll {
            it.query.equals(entry.query, ignoreCase = true) &&
                    it.isHashtag == entry.isHashtag &&
                    it.tab == entry.tab &&
                    it.createdAt == entry.createdAt
        }
        Preferences.preferenceSp.edit()
            .putString(
                FapFolderConstants.PREF_FAPFOLDER_SEARCH_HISTORY,
                HJson.encodeToString(existing)
            )
            .apply()
    }

    fun clear() {
        Preferences.preferenceSp.edit()
            .remove(FapFolderConstants.PREF_FAPFOLDER_SEARCH_HISTORY)
            .apply()
    }
}
