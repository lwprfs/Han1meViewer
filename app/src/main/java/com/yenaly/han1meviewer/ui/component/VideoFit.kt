package com.yenaly.han1meviewer.ui.component

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.VideoSize

/**
 * Keeps a video surface at the video's original aspect ratio, centered inside its parent
 * (the parent supplies the black bars). When [stretched] is true the surface fills the parent.
 */
class VideoFitState {
    var aspect: Float = 0f
    var stretched: Boolean = false

    fun updateFrom(size: VideoSize) {
        aspect = if (size.width > 0 && size.height > 0) {
            size.width * size.pixelWidthHeightRatio / size.height
        } else 0f
    }
}

fun fitVideoSurface(parent: ViewGroup, surface: View, state: VideoFitState) {
    val w = parent.width
    val h = parent.height
    if (w <= 0 || h <= 0) return
    var targetW = w
    var targetH = h
    val aspect = state.aspect
    if (!state.stretched && aspect > 0f) {
        if (w.toFloat() / h > aspect) {
            targetW = (h * aspect).toInt()
        } else {
            targetH = (w / aspect).toInt()
        }
    }
    val lp = (surface.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(targetW, targetH)
    if (lp.width != targetW || lp.height != targetH || lp.gravity != Gravity.CENTER) {
        lp.width = targetW
        lp.height = targetH
        lp.gravity = Gravity.CENTER
        surface.layoutParams = lp
    }
}

/** Installs a layout listener so the surface is re-fitted whenever the parent is resized. */
fun installVideoFit(parent: FrameLayout, surface: View, state: VideoFitState) {
    parent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        fitVideoSurface(parent, surface, state)
    }
}
