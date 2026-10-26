package com.yenaly.han1meviewer.OnAir.common

import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences

object OnAirSitePreferences {

    private const val PREF_ONAIR_HOST = "onair_host"
    private const val PREF_ONAIR_MIRROR_INDEX = "onair_mirror_index"

    val selectedMirrorIndex: Int
        get() = Preferences.preferenceSp.getInt(PREF_ONAIR_MIRROR_INDEX, 0)
            .coerceIn(0, OnAirConstants.ONAIR_URL.lastIndex)

    val selectedBaseUrl: String
        get() = OnAirConstants.ONAIR_URL[selectedMirrorIndex]

    fun setMirrorIndex(index: Int) {
        val clamped = index.coerceIn(0, OnAirConstants.ONAIR_URL.lastIndex)
        Preferences.preferenceSp.edit {
            putInt(PREF_ONAIR_MIRROR_INDEX, clamped)
            putString(PREF_ONAIR_HOST, OnAirConstants.ONAIR_HOSTNAME[clamped])
        }
    }

    fun setHost(hostname: String) {
        val index = OnAirConstants.ONAIR_HOSTNAME.indexOfFirst {
            it.equals(hostname, ignoreCase = true)
        }
        if (index >= 0) {
            setMirrorIndex(index)
        }
    }

    fun currentHostname(): String = OnAirConstants.ONAIR_HOSTNAME[selectedMirrorIndex]
}
