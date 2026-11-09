package com.yenaly.han1meviewer.util

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.yenaly.han1meviewer.Preferences

object PlayerOrientation {

    const val AUTO = "auto"
    const val LANDSCAPE = "landscape"
    const val PORTRAIT = "portrait"

    private const val KEY = "player_fullscreen_orientation"

    var mode: String
        get() = Preferences.preferenceSp.getString(KEY, AUTO).takeIf {
            it == LANDSCAPE || it == PORTRAIT
        } ?: AUTO
        set(value) {
            Preferences.preferenceSp.edit().putString(KEY, value).apply()
        }

    @Volatile
    var userRotated = false

    fun fullscreenRequest(): Int =
        if (mode == PORTRAIT) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    fun rotateTarget(activity: Activity): Int {
        val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun rotate(activity: Activity) {
        userRotated = true
        activity.requestedOrientation = rotateTarget(activity)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlays = 0

    fun overlayEntered() {
        overlays++
    }

    fun overlayLeft(activity: Activity?) {
        overlays--

        handler.post {
            if (overlays <= 0 && userRotated) {
                userRotated = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}
