package com.yenaly.han1meviewer.ui.component.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.yenaly_libs.utils.applicationContext

object SitePlayerSettings {

    val configurableSites: List<SiteType> = listOf(
        SiteType.MISSAV,
        SiteType.HENTAIMAMA,
        SiteType.ONAIR,
        SiteType.FAPFOLDER,
        SiteType.SUPJAV,
        SiteType.ROUVIDEO,
        SiteType.XHAMSTER,
    )

    fun label(site: SiteType): String = when (site) {
        SiteType.HANIME -> "Hanime1"
        SiteType.JAVCHU -> "Javchu"
        SiteType.MISSAV -> "MissAV"
        SiteType.HENTAIMAMA -> "HentaiMama"
        SiteType.ONAIR -> "OnAir"
        SiteType.FAPFOLDER -> "FapFolder"
        SiteType.SUPJAV -> "SupJav"
        SiteType.ROUVIDEO -> applicationContext.getString(R.string.site_rouvideo_name)
        SiteType.XHAMSTER -> applicationContext.getString(R.string.site_xhamster_name)
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
    const val ID_KEEP_SCREEN_ON = "keep_screen_on"
    const val ID_GESTURE_SEEK = "gesture_seek"
    const val ID_GESTURE_BRIGHTNESS = "gesture_brightness"
    const val ID_GESTURE_VOLUME = "gesture_volume"
    const val ID_DOUBLE_TAP_ENABLED = "double_tap_enabled"
    const val ID_DOUBLE_TAP_CENTER_PAUSE = "double_tap_center_pause"
    const val ID_LONG_PRESS_ENABLED = "long_press_enabled"
    const val ID_AUTO_HIDE = "auto_hide_controls"
    const val ID_CONTROLS_OPACITY = "controls_opacity"
    const val ID_SHOW_TITLE = "show_title"
    const val ID_SHOW_CLOCK = "show_clock"
    const val ID_SHOW_SPEED_BUTTON = "show_speed_button"
    const val ID_SHOW_QUALITY_BUTTON = "show_quality_button"
    const val ID_SHOW_LOCK_BUTTON = "show_lock_button"
    const val ID_SHOW_STRETCH_BUTTON = "show_stretch_button"
    const val ID_SHOW_FULLSCREEN_BUTTON = "show_fullscreen_button"
    const val ID_SHOW_SKIP_BUTTONS = "show_skip_buttons"
    const val ID_SHOW_REMAINING = "show_remaining_time"

    private val speedOptions = listOf(
        "0.5x" to "0.5", "0.75x" to "0.75", "1.0x" to "1.0", "1.25x" to "1.25",
        "1.5x" to "1.5", "2.0x" to "2.0",
    )

    private val longPressOptions = listOf(
        "1.5x" to "1.5", "2.0x" to "2.0", "2.5x" to "2.5", "3.0x" to "3.0",
        "3.5x" to "3.5", "4.0x" to "4.0",
    )

    private val qualityOptions: List<Pair<String, String>>
        get() = listOf(
        applicationContext.getString(R.string.site_quality_original) to "original",
        "720p" to "720",
        "480p" to "480",
        "360p" to "360",
        "240p" to "240",
        "144p" to "144",
    )

    val specs: List<Spec>
        get() = listOf(

        Spec.Switch(
            ID_AUTO_PLAY, applicationContext.getString(R.string.site_player_auto_play_title), applicationContext.getString(R.string.site_player_auto_play_summary),
        ) { true },
        Spec.Switch(
            ID_RESUME, applicationContext.getString(R.string.site_player_resume_title), applicationContext.getString(R.string.site_player_resume_summary),
        ) { Preferences.allowResumePlayback },
        Spec.Choice(
            ID_DEFAULT_SPEED, applicationContext.getString(R.string.site_player_default_speed_title), null, speedOptions,
        ) { Preferences.playerSpeed.toString() },
        Spec.Choice(
            ID_DEFAULT_QUALITY,
            applicationContext.getString(R.string.site_default_resolution),
            applicationContext.getString(R.string.site_default_resolution_summary),
            qualityOptions,
            onlySites = setOf(SiteType.ONAIR),
        ) { "original" },
        Spec.Switch(
            ID_KEEP_SCREEN_ON, applicationContext.getString(R.string.site_player_keep_screen_on_title), applicationContext.getString(R.string.site_player_keep_screen_on_summary),
        ) { true },
        Spec.Switch(
            ID_STRETCH, applicationContext.getString(R.string.site_player_stretch_title), applicationContext.getString(R.string.site_player_stretch_summary),
        ) { false },

        Spec.Switch(
            ID_GESTURES, applicationContext.getString(R.string.site_player_gestures_title), applicationContext.getString(R.string.site_player_gestures_summary),
        ) { true },
        Spec.Switch(
            ID_GESTURE_SEEK, applicationContext.getString(R.string.site_player_gesture_seek_title), applicationContext.getString(R.string.site_player_gesture_seek_summary),
        ) { true },
        Spec.Switch(
            ID_GESTURE_BRIGHTNESS, applicationContext.getString(R.string.site_player_gesture_brightness_title), applicationContext.getString(R.string.site_player_gesture_brightness_summary),
        ) { true },
        Spec.Switch(
            ID_GESTURE_VOLUME, applicationContext.getString(R.string.site_player_gesture_volume_title), applicationContext.getString(R.string.site_player_gesture_volume_summary),
        ) { true },
        Spec.Slider(
            ID_SLIDE_SENSITIVITY, applicationContext.getString(R.string.site_player_slide_sensitivity_title),
            applicationContext.getString(R.string.site_player_slide_sensitivity_summary), 1..9, "",
        ) { Preferences.slideSensitivity },
        Spec.Switch(
            ID_DOUBLE_TAP_ENABLED, applicationContext.getString(R.string.site_player_double_tap_enabled_title), applicationContext.getString(R.string.site_player_double_tap_enabled_summary),
        ) { true },
        Spec.Slider(
            ID_DOUBLE_TAP_SEEK, applicationContext.getString(R.string.site_player_double_tap_seek_title), applicationContext.getString(R.string.site_player_double_tap_seek_summary), 5..30, "s",
        ) { 10 },
        Spec.Switch(
            ID_DOUBLE_TAP_CENTER_PAUSE, applicationContext.getString(R.string.site_player_double_tap_center_pause_title),
            applicationContext.getString(R.string.site_player_double_tap_center_pause_summary),
        ) { false },
        Spec.Switch(
            ID_LONG_PRESS_ENABLED, applicationContext.getString(R.string.site_player_long_press_enabled_title), applicationContext.getString(R.string.site_player_long_press_enabled_summary),
        ) { true },
        Spec.Choice(
            ID_LONG_PRESS_SPEED, applicationContext.getString(R.string.site_player_long_press_speed_title), applicationContext.getString(R.string.site_player_long_press_speed_summary), longPressOptions,
        ) { Preferences.longPressSpeedTime.toString() },

        Spec.Switch(
            ID_AUTO_HIDE, applicationContext.getString(R.string.site_player_auto_hide_title), applicationContext.getString(R.string.site_player_auto_hide_summary),
        ) { true },
        Spec.Slider(
            ID_HIDE_DELAY, applicationContext.getString(R.string.site_player_hide_delay_title), applicationContext.getString(R.string.site_player_hide_delay_summary), 2..10, "s",
        ) { 3 },
        Spec.Slider(
            ID_CONTROLS_OPACITY, applicationContext.getString(R.string.site_player_controls_opacity_title), applicationContext.getString(R.string.site_player_controls_opacity_summary), 10..90, "%",
        ) { 32 },
        Spec.Switch(
            ID_BOTTOM_PROGRESS, applicationContext.getString(R.string.site_player_bottom_progress_title), applicationContext.getString(R.string.site_player_bottom_progress_summary),
        ) { Preferences.showBottomProgress },
        Spec.Switch(ID_SHOW_TITLE, applicationContext.getString(R.string.site_player_show_title_title), applicationContext.getString(R.string.site_player_show_title_summary)) { true },
        Spec.Switch(ID_SHOW_CLOCK, applicationContext.getString(R.string.site_player_show_clock_title), applicationContext.getString(R.string.site_player_show_clock_summary)) { true },
        Spec.Switch(ID_SHOW_SPEED_BUTTON, applicationContext.getString(R.string.site_player_show_speed_button_title), applicationContext.getString(R.string.site_player_show_speed_button_summary)) { true },
        Spec.Switch(ID_SHOW_QUALITY_BUTTON, applicationContext.getString(R.string.site_player_show_quality_button_title), applicationContext.getString(R.string.site_player_show_quality_button_summary)) { true },
        Spec.Switch(ID_SHOW_LOCK_BUTTON, applicationContext.getString(R.string.site_player_show_lock_button_title), applicationContext.getString(R.string.site_player_show_lock_button_summary)) { true },
        Spec.Switch(ID_SHOW_STRETCH_BUTTON, applicationContext.getString(R.string.site_player_show_stretch_button_title), applicationContext.getString(R.string.site_player_show_stretch_button_summary)) { true },
        Spec.Switch(ID_SHOW_FULLSCREEN_BUTTON, applicationContext.getString(R.string.site_player_show_fullscreen_button_title), applicationContext.getString(R.string.site_player_show_fullscreen_button_summary)) { true },
        Spec.Switch(
            ID_SHOW_SKIP_BUTTONS, applicationContext.getString(R.string.site_player_show_skip_buttons_title), applicationContext.getString(R.string.site_player_show_skip_buttons_summary),
        ) { false },
        Spec.Switch(
            ID_SHOW_REMAINING, applicationContext.getString(R.string.site_player_show_remaining_title), applicationContext.getString(R.string.site_player_show_remaining_summary),
        ) { false },
    )

    val groups: List<Pair<String, List<String>>>
        get() = listOf(
        applicationContext.getString(R.string.site_player_group_playback) to listOf(
            ID_AUTO_PLAY, ID_RESUME, ID_DEFAULT_SPEED, ID_DEFAULT_QUALITY, ID_KEEP_SCREEN_ON, ID_STRETCH,
        ),
        applicationContext.getString(R.string.site_player_group_gestures) to listOf(
            ID_GESTURES, ID_GESTURE_SEEK, ID_GESTURE_BRIGHTNESS, ID_GESTURE_VOLUME, ID_SLIDE_SENSITIVITY,
            ID_DOUBLE_TAP_ENABLED, ID_DOUBLE_TAP_SEEK, ID_DOUBLE_TAP_CENTER_PAUSE,
            ID_LONG_PRESS_ENABLED, ID_LONG_PRESS_SPEED,
        ),
        applicationContext.getString(R.string.site_player_group_controls) to listOf(
            ID_AUTO_HIDE, ID_HIDE_DELAY, ID_CONTROLS_OPACITY, ID_BOTTOM_PROGRESS, ID_SHOW_TITLE,
            ID_SHOW_CLOCK, ID_SHOW_SPEED_BUTTON, ID_SHOW_QUALITY_BUTTON, ID_SHOW_LOCK_BUTTON,
            ID_SHOW_STRETCH_BUTTON, ID_SHOW_FULLSCREEN_BUTTON, ID_SHOW_SKIP_BUTTONS, ID_SHOW_REMAINING,
        ),
    )

    private val parents: Map<String, String> = mapOf(
        ID_GESTURE_SEEK to ID_GESTURES,
        ID_GESTURE_BRIGHTNESS to ID_GESTURES,
        ID_GESTURE_VOLUME to ID_GESTURES,
        ID_SLIDE_SENSITIVITY to ID_GESTURES,
        ID_DOUBLE_TAP_SEEK to ID_DOUBLE_TAP_ENABLED,
        ID_DOUBLE_TAP_CENTER_PAUSE to ID_DOUBLE_TAP_ENABLED,
        ID_LONG_PRESS_SPEED to ID_LONG_PRESS_ENABLED,
        ID_HIDE_DELAY to ID_AUTO_HIDE,
    )

    fun isEnabled(site: SiteType, id: String): Boolean {
        val parent = parents[id] ?: return true
        return bool(site, parent)
    }

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

    fun setAllSwitches(site: SiteType, enabled: Boolean) {
        if (isSynced(site)) separate(site)
        Preferences.preferenceSp.edit {
            specsFor(site).filterIsInstance<Spec.Switch>().forEach {
                putString(key(site, it.id), enabled.toString())
            }
        }
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
            keepScreenOn = bool(site, ID_KEEP_SCREEN_ON),
            gestureSeek = bool(site, ID_GESTURE_SEEK),
            gestureBrightness = bool(site, ID_GESTURE_BRIGHTNESS),
            gestureVolume = bool(site, ID_GESTURE_VOLUME),
            doubleTapEnabled = bool(site, ID_DOUBLE_TAP_ENABLED),
            doubleTapCenterPause = getString(site, ID_DOUBLE_TAP_CENTER_PAUSE).toBooleanStrictOrNull() ?: false,
            longPressEnabled = bool(site, ID_LONG_PRESS_ENABLED),
            autoHideControls = bool(site, ID_AUTO_HIDE),
            controlsOpacity = int(site, ID_CONTROLS_OPACITY, 32).coerceIn(10, 90) / 100f,
            showTitle = bool(site, ID_SHOW_TITLE),
            showClock = bool(site, ID_SHOW_CLOCK),
            showSpeedButton = bool(site, ID_SHOW_SPEED_BUTTON),
            showQualityButton = bool(site, ID_SHOW_QUALITY_BUTTON),
            showLockButton = bool(site, ID_SHOW_LOCK_BUTTON),
            showStretchButton = bool(site, ID_SHOW_STRETCH_BUTTON),
            showFullscreenButton = bool(site, ID_SHOW_FULLSCREEN_BUTTON),
            showSkipButtons = getString(site, ID_SHOW_SKIP_BUTTONS).toBooleanStrictOrNull() ?: false,
            showRemainingTime = getString(site, ID_SHOW_REMAINING).toBooleanStrictOrNull() ?: false,
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
    val keepScreenOn: Boolean = true,
    val gestureSeek: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val doubleTapEnabled: Boolean = true,
    val doubleTapCenterPause: Boolean = false,
    val longPressEnabled: Boolean = true,
    val autoHideControls: Boolean = true,
    val controlsOpacity: Float = 0.32f,
    val showTitle: Boolean = true,
    val showClock: Boolean = true,
    val showSpeedButton: Boolean = true,
    val showQualityButton: Boolean = true,
    val showLockButton: Boolean = true,
    val showStretchButton: Boolean = true,
    val showFullscreenButton: Boolean = true,
    val showSkipButtons: Boolean = false,
    val showRemainingTime: Boolean = false,
)
