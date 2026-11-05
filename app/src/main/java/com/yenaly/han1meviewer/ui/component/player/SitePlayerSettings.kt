package com.yenaly.han1meviewer.ui.component.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType

object SitePlayerSettings {

    val configurableSites: List<SiteType> = listOf(
        SiteType.MISSAV,
        SiteType.HENTAIMAMA,
        SiteType.ONAIR,
        SiteType.FAPFOLDER,
    )

    fun label(site: SiteType): String = when (site) {
        SiteType.HANIME -> "Hanime1"
        SiteType.JAVCHU -> "Javchu"
        SiteType.MISSAV -> "MissAV"
        SiteType.HENTAIMAMA -> "HentaiMama"
        SiteType.ONAIR -> "OnAir"
        SiteType.FAPFOLDER -> "FapFolder"
    }

    sealed class Spec(val id: String, val title: String, val summary: String?) {
        abstract fun appDefault(): String

        class Switch(
            id: String, title: String, summary: String?,
            private val default: () -> Boolean,
        ) : Spec(id, title, summary) {
            override fun appDefault(): String = default().toString()
        }

        class Choice(
            id: String, title: String, summary: String?,
            val options: List<Pair<String, String>>,
            val onlySites: Set<SiteType>? = null,
            private val default: () -> String,
        ) : Spec(id, title, summary) {
            override fun appDefault(): String = default()
        }

        class Slider(
            id: String, title: String, summary: String?,
            val range: IntRange,
            val unit: String,
            private val default: () -> Int,
        ) : Spec(id, title, summary) {
            override fun appDefault(): String = default().toString()
        }
    }

    const val ID_DEFAULT_SPEED = "default_speed"
    const val ID_LONG_PRESS_SPEED = "long_press_speed"
    const val ID_SLIDE_SENSITIVITY = "slide_sensitivity"
    const val ID_BOTTOM_PROGRESS = "bottom_progress"
    const val ID_DOUBLE_TAP_SEEK = "double_tap_seek_sec"
    const val ID_HIDE_DELAY = "controls_hide_sec"
    const val ID_GESTURES = "gestures_enabled"
    const val ID_RESUME = "resume_playback"
    const val ID_AUTO_PLAY = "auto_play"
    const val ID_STRETCH = "stretch_fullscreen"
    const val ID_DEFAULT_QUALITY = "default_quality"

    private val speedOptions = listOf(
        "0.5x" to "0.5", "0.75x" to "0.75", "1.0x" to "1.0", "1.25x" to "1.25",
        "1.5x" to "1.5", "2.0x" to "2.0",
    )

    private val longPressOptions = listOf(
        "1.5x" to "1.5", "2.0x" to "2.0", "2.5x" to "2.5", "3.0x" to "3.0",
        "3.5x" to "3.5", "4.0x" to "4.0",
    )

    private val qualityOptions = listOf(
        "Original (best)" to "original",
        "720p" to "720",
        "480p" to "480",
        "360p" to "360",
        "240p" to "240",
        "144p" to "144",
    )

    val specs: List<Spec> = listOf(
        Spec.Choice(
            ID_DEFAULT_QUALITY, "Default resolution",
            "Quality used when a video opens. If it is not offered, the closest lower one is used",
            qualityOptions,
            onlySites = setOf(SiteType.ONAIR),
        ) { "original" },
        Spec.Choice(
            ID_DEFAULT_SPEED, "Default playback speed", null, speedOptions,
        ) { Preferences.playerSpeed.toString() },
        Spec.Choice(
            ID_LONG_PRESS_SPEED, "Long-press speed", "Hold the video to speed up", longPressOptions,
        ) { Preferences.longPressSpeedTime.toString() },
        Spec.Slider(
            ID_SLIDE_SENSITIVITY, "Slide sensitivity",
            "How far a swipe moves progress, brightness and volume", 1..9, "",
        ) { Preferences.slideSensitivity },
        Spec.Switch(
            ID_BOTTOM_PROGRESS, "Show bottom progress", "Thin progress line while controls are hidden",
        ) { Preferences.showBottomProgress },
        Spec.Switch(
            ID_GESTURES, "Swipe gestures", "Swipe for progress, brightness and volume",
        ) { true },
        Spec.Slider(
            ID_DOUBLE_TAP_SEEK, "Double-tap skip", "Seconds to skip on a double tap", 5..30, "s",
        ) { 10 },
        Spec.Slider(
            ID_HIDE_DELAY, "Hide controls after", "Seconds before the controls fade out", 2..10, "s",
        ) { 3 },
        Spec.Switch(
            ID_RESUME, "Resume playback", "Offer to continue where you stopped",
        ) { Preferences.allowResumePlayback },
        Spec.Switch(
            ID_AUTO_PLAY, "Auto play", "Start playing as soon as the video is ready",
        ) { true },
        Spec.Switch(
            ID_STRETCH, "Fill screen in fullscreen", "Stretch the video to fill the screen by default",
        ) { false },
    )

    var revision by mutableIntStateOf(0)
        private set

    private fun key(site: SiteType, name: String) = "site_player_${site.name.lowercase()}_$name"

    private fun syncKey(site: SiteType) = key(site, "sync_with_app")

    fun isSynced(site: SiteType): Boolean {
        if (site == SiteType.HANIME || site == SiteType.JAVCHU) return true
        return Preferences.preferenceSp.getBoolean(syncKey(site), true)
    }

    fun setSynced(site: SiteType, synced: Boolean) {
        Preferences.preferenceSp.edit { putBoolean(syncKey(site), synced) }
        revision++
    }

    fun spec(id: String): Spec? = specs.firstOrNull { it.id == id }

    fun specsFor(site: SiteType): List<Spec> = specs.filter {
        val only = (it as? Spec.Choice)?.onlySites
        only == null || site in only
    }

    private fun hiddenKey(site: SiteType) = key(site, "hidden_settings")

    fun hiddenIds(site: SiteType): Set<String> {
        @Suppress("UNUSED_VARIABLE") val rev = revision
        return Preferences.preferenceSp.getStringSet(hiddenKey(site), emptySet()) ?: emptySet()
    }

    fun isListed(site: SiteType, id: String): Boolean = id !in hiddenIds(site)

    fun setListed(site: SiteType, id: String, listed: Boolean) {
        val next = hiddenIds(site).toMutableSet().apply { if (listed) remove(id) else add(id) }
        Preferences.preferenceSp.edit { putStringSet(hiddenKey(site), next) }
        revision++
    }

    fun getString(site: SiteType, id: String): String {
        val spec = spec(id) ?: return ""
        if (isSynced(site)) return spec.appDefault()
        return Preferences.preferenceSp.getString(key(site, id), null) ?: spec.appDefault()
    }

    fun setString(site: SiteType, id: String, value: String) {

        if (isSynced(site)) separate(site)
        Preferences.preferenceSp.edit { putString(key(site, id), value) }
        revision++
    }

    fun separate(site: SiteType) {
        Preferences.preferenceSp.edit {
            specs.forEach { putString(key(site, it.id), it.appDefault()) }
            putBoolean(syncKey(site), false)
        }
        revision++
    }

    fun resetToApp(site: SiteType) {
        Preferences.preferenceSp.edit {
            specs.forEach { remove(key(site, it.id)) }
            putBoolean(syncKey(site), true)
        }
        revision++
    }

    private fun bool(site: SiteType, id: String) = getString(site, id).toBooleanStrictOrNull() ?: true
    private fun int(site: SiteType, id: String, def: Int) = getString(site, id).toIntOrNull() ?: def
    private fun float(site: SiteType, id: String, def: Float) =
        getString(site, id).toFloatOrNull() ?: def

    fun config(site: SiteType): SitePlayerConfig {

        @Suppress("UNUSED_VARIABLE") val rev = revision
        return SitePlayerConfig(
            defaultSpeed = float(site, ID_DEFAULT_SPEED, 1f),
            longPressSpeed = float(site, ID_LONG_PRESS_SPEED, 2.5f),
            slideSensitivity = int(site, ID_SLIDE_SENSITIVITY, 5).coerceIn(1, 9),
            showBottomProgress = bool(site, ID_BOTTOM_PROGRESS),
            gesturesEnabled = bool(site, ID_GESTURES),
            doubleTapSeekMs = int(site, ID_DOUBLE_TAP_SEEK, 10).coerceIn(5, 30) * 1000L,
            controlsHideMs = int(site, ID_HIDE_DELAY, 3).coerceIn(2, 10) * 1000L,
            resumePlayback = bool(site, ID_RESUME),
            autoPlay = bool(site, ID_AUTO_PLAY),
            stretchFullscreen = getString(site, ID_STRETCH).toBooleanStrictOrNull() ?: false,
            defaultQuality = getString(site, ID_DEFAULT_QUALITY).ifBlank { "original" },
        )
    }
}

data class SitePlayerConfig(
    val defaultSpeed: Float = 1f,
    val longPressSpeed: Float = 2.5f,
    val slideSensitivity: Int = 5,
    val showBottomProgress: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val doubleTapSeekMs: Long = 10_000L,
    val controlsHideMs: Long = 3_000L,
    val resumePlayback: Boolean = true,
    val autoPlay: Boolean = true,
    val stretchFullscreen: Boolean = false,
    val defaultQuality: String = "original",
)
