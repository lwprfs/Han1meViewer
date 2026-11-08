package com.yenaly.han1meviewer.RouVideo.common

import com.yenaly.han1meviewer.Preferences

object RouVideoSettings {

    private const val KEY_TAB_ORDER = "rouvideo_tab_order"
    private const val KEY_TAB_HIDDEN = "rouvideo_tabs_hidden"
    private const val KEY_TITLE_LANGUAGE = "rouvideo_title_language"
    private const val KEY_REPORT_PLAYS = "rouvideo_report_plays"

    const val LANG_TRADITIONAL = "hant"
    const val LANG_SIMPLIFIED = "hans"

    private val sp get() = Preferences.preferenceSp

    private fun readList(key: String): List<String> =
        sp.getString(key, null).orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    var tabOrder: List<String>
        get() {
            val all = RouVideoConstants.DEFAULT_TABS.map { it.key }
            val saved = readList(KEY_TAB_ORDER).filter { it in all }.distinct()
            return saved + all.filter { it !in saved }
        }
        set(value) {
            sp.edit().putString(KEY_TAB_ORDER, value.joinToString(",")).apply()
        }

    var hiddenTabs: Set<String>
        get() = readList(KEY_TAB_HIDDEN).toSet()
        set(value) {
            sp.edit().putString(KEY_TAB_HIDDEN, value.joinToString(",")).apply()
        }

    fun visibleTabs(): List<RouVideoConstants.HomeTab> {
        val byKey = RouVideoConstants.DEFAULT_TABS.associateBy { it.key }
        val ordered = tabOrder.mapNotNull { byKey[it] }
        val hidden = hiddenTabs
        return ordered.filter { it.key !in hidden }.ifEmpty { ordered }
    }

    var titleLanguage: String
        get() = sp.getString(KEY_TITLE_LANGUAGE, null)
            ?.takeIf { it == LANG_TRADITIONAL || it == LANG_SIMPLIFIED } ?: LANG_TRADITIONAL
        set(value) {
            sp.edit().putString(KEY_TITLE_LANGUAGE, value).apply()
        }

    var reportPlays: Boolean
        get() = sp.getBoolean(KEY_REPORT_PLAYS, false)
        set(value) {
            sp.edit().putBoolean(KEY_REPORT_PLAYS, value).apply()
        }

    fun pick(traditional: String, simplified: String?): String =
        if (titleLanguage == LANG_SIMPLIFIED && !simplified.isNullOrBlank()) simplified else traditional

    fun resetToDefaults() {
        sp.edit()
            .remove(KEY_TAB_ORDER)
            .remove(KEY_TAB_HIDDEN)
            .remove(KEY_TITLE_LANGUAGE)
            .remove(KEY_REPORT_PLAYS)
            .apply()
    }
}
