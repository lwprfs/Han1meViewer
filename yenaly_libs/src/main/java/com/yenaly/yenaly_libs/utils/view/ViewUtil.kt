@file:JvmName("ViewUtil")
@file:Suppress("unused")

package com.yenaly.yenaly_libs.utils.view

import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewOutlineProvider
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import kotlin.math.min

fun View.setMargins(
    start: Int = marginStart,
    top: Int = marginTop,
    end: Int = marginEnd,
    bottom: Int = marginBottom,
) = updateLayoutParams<MarginLayoutParams> {
    setMargins(0, top, 0, bottom)
    marginStart = start
    marginEnd = end
}

fun View.resize(
    scaleX: Float,
    scaleY: Float,
) = updateLayoutParams {
    width = (width * scaleX).toInt()
    height = (height * scaleY).toInt()
}

fun View.removeItself() {
    (parent as? ViewGroup)?.removeView(this)
}

inline fun <reified T : View> View.findParent(): T {
    var parent = parent
    while (parent != null) {
        if (parent is T) {
            return parent
        }
        parent = parent.parent
    }
    error("No parent of type ${T::class.java.simpleName} found")
}

fun View.setRoundCorner(radius: Float) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val min = min(view.width, view.height)
            val realRadius = if (radius > min / 2) (min / 2).toFloat() else radius
            outline.setRoundRect(0, 0, view.width, view.height, realRadius)
        }
    }
}

fun View.setRoundCorner(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        @Suppress("DEPRECATION")
        override fun getOutline(view: View, outline: Outline) {
            val path = Path().apply {
                addRoundRect(
                    0f, 0f, view.width.toFloat(), view.height.toFloat(),
                    floatArrayOf(
                        topLeft, topLeft,
                        topRight, topRight,
                        bottomRight, bottomRight,
                        bottomLeft, bottomLeft
                    ),
                    Path.Direction.CW
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                outline.setPath(path)
            } else {
                outline.setConvexPath(path)
            }
        }
    }
}

val View.verticalVisiblePercent: Float
    get() {
        val rect = Rect()
        val visibleHeight = if (getLocalVisibleRect(rect)) rect.height() else 0
        val fullHeight = with(rect) { getHitRect(rect); this.height() }
        return if (fullHeight == 0) 0F else visibleHeight.toFloat() / fullHeight
    }

val View.horizontalVisiblePercent: Float
    get() {
        val rect = Rect()
        val visibleWidth = if (getLocalVisibleRect(rect)) rect.width() else 0
        val fullWidth = with(rect) { getHitRect(rect); this.width() }
        return if (fullWidth == 0) 0F else visibleWidth.toFloat() / fullWidth
    }

val View.visiblePercent: Float
    get() = min(verticalVisiblePercent, horizontalVisiblePercent)
