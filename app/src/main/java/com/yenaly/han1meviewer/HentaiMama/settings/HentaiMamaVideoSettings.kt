package com.yenaly.han1meviewer.HentaiMama.settings

import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences

object HentaiMamaVideoSettings {

    private const val PREF_SERVER_KEY = "hentaimama_preferred_server"
    private const val PREF_QUALITY_KEY = "hentaimama_preferred_quality"

    const val SERVER_AUTO = "__auto__"

    const val QUALITY_AUTO = "__auto__"

    var preferredServer: String
        get() = Preferences.preferenceSp.getString(PREF_SERVER_KEY, SERVER_AUTO) ?: SERVER_AUTO
        set(value) = Preferences.preferenceSp.edit { putString(PREF_SERVER_KEY, value) }

    var preferredQuality: String
        get() = Preferences.preferenceSp.getString(PREF_QUALITY_KEY, QUALITY_AUTO) ?: QUALITY_AUTO
        set(value) = Preferences.preferenceSp.edit { putString(PREF_QUALITY_KEY, value) }
}
