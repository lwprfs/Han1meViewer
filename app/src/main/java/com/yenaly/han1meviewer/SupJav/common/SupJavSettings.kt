package com.yenaly.han1meviewer.SupJav.common

import com.yenaly.han1meviewer.Preferences

object SupJavSettings {

    private const val KEY_QUALITY = "supjav_pref_quality"
    private const val KEY_ORDER = "supjav_server_order"
    private const val KEY_DISABLED = "supjav_servers_disabled"
    private const val KEY_FALLBACK = "supjav_auto_fallback"
    private const val KEY_LANGUAGE = "supjav_language"

    private val sp get() = Preferences.preferenceSp

    var preferredQuality: String
        get() = sp.getString(KEY_QUALITY, null)
            ?.takeIf { it in SupJavConstants.QUALITIES } ?: SupJavConstants.DEFAULT_QUALITY
        set(value) {
            sp.edit().putString(KEY_QUALITY, value).apply()
        }

    var serverOrder: List<String>
        get() {
            val saved = sp.getString(KEY_ORDER, null).orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it in SupJavConstants.SUPPORTED_SERVERS }
                .distinct()
            return saved + SupJavConstants.SUPPORTED_SERVERS.filter { it !in saved }
        }
        set(value) {
            sp.edit().putString(KEY_ORDER, value.joinToString(",")).apply()
        }

    var disabledServers: Set<String>
        get() = sp.getString(KEY_DISABLED, null).orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        set(value) {
            sp.edit().putString(KEY_DISABLED, value.joinToString(",")).apply()
        }

    fun isServerEnabled(name: String): Boolean =
        disabledServers.none { it.equals(name, ignoreCase = true) }

    var autoFallback: Boolean
        get() = sp.getBoolean(KEY_FALLBACK, true)
        set(value) {
            sp.edit().putBoolean(KEY_FALLBACK, value).apply()
        }

    var language: String
        get() = sp.getString(KEY_LANGUAGE, null)
            ?.takeIf { code -> SupJavConstants.LANGUAGES.any { it.first == code } } ?: "en"
        set(value) {
            sp.edit().putString(KEY_LANGUAGE, value).apply()
        }

    fun serverRank(name: String): Int =
        serverOrder.indexOfFirst { it.equals(name, ignoreCase = true) }.let { if (it < 0) Int.MAX_VALUE else it }

    fun resetToDefaults() {
        sp.edit()
            .remove(KEY_QUALITY)
            .remove(KEY_ORDER)
            .remove(KEY_DISABLED)
            .remove(KEY_FALLBACK)
            .remove(KEY_LANGUAGE)
            .apply()
    }
}
