package com.yenaly.han1meviewer.MissAV.ui.search
import com.yenaly.han1meviewer.Preferences

object MissAvSearchHistoryRepo {

    private const val PREF_KEY = "missav_search_history"
    private const val MAX_ENTRIES = 20
    private const val SEP = "\u001F"

    fun load(): List<String> =
        Preferences.preferenceSp.getString(PREF_KEY, null)
            ?.split(SEP)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun push(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = load().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val trimmedList = current.take(MAX_ENTRIES)
        Preferences.preferenceSp.edit()
            .putString(PREF_KEY, trimmedList.joinToString(SEP))
            .apply()
    }

    fun clear() {
        Preferences.preferenceSp.edit().remove(PREF_KEY).apply()
    }
}