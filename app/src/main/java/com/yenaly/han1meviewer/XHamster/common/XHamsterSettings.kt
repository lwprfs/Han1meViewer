package com.yenaly.han1meviewer.XHamster.common

import com.yenaly.han1meviewer.Preferences

object XHamsterSettings {

    private const val KEY_QUALITY = "xhamster_pref_quality"

    private val sp get() = Preferences.preferenceSp

    var preferredQuality: String
        get() = sp.getString(KEY_QUALITY, null)
            ?.takeIf { it in XHamsterConstants.QUALITIES } ?: XHamsterConstants.DEFAULT_QUALITY
        set(value) {
            sp.edit().putString(KEY_QUALITY, value).apply()
        }
}
