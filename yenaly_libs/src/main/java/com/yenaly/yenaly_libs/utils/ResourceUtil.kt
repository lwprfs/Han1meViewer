@file:Suppress("unused")
@file:JvmName("ResourceUtil")

package com.yenaly.yenaly_libs.utils

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.util.TypedValueCompat

val Number.dpF: Float
    @JvmName("dpToPxF")
    get() = TypedValueCompat.dpToPx(
        this.toFloat(),
        applicationContext.resources.displayMetrics
    )

val Number.spF: Float
    @JvmName("spToPxF")
    get() = TypedValueCompat.spToPx(
        this.toFloat(),
        applicationContext.resources.displayMetrics
    )

val Number.dp: Int
    @JvmName("dpToPx")
    get() {
        val f = dpF
        val res = (if (f >= 0) f + 0.5f else f - 0.5f).toInt()
        return res
    }

val Number.sp: Int
    @JvmName("spToPx")
    get() {
        val f = spF
        val res = (if (f >= 0) f + 0.5f else f - 0.5f).toInt()
        return res
    }

val statusBarHeight: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        val resources: Resources = applicationContext.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

val navBarHeight: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        val resources: Resources = applicationContext.resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

val isOrientationLandscape: Boolean
    get() {
        return applicationContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
