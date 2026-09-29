@file:Suppress("unused")
@file:JvmName("ContextUtil")

package com.yenaly.yenaly_libs.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.color.MaterialColors
import com.yenaly.yenaly_libs.ActivityManager

@set:JvmSynthetic
lateinit var applicationContext: Context
    internal set

val application get() = applicationContext as Application

val Context.activity: Activity?
    get() {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

inline fun <reified T : Activity> Context.findActivity(): T {
    return findActivityOrNull() ?: error("No activity of type ${T::class.java.simpleName} found")
}

inline fun <reified T : Activity> Context.findActivityOrNull(): T? {
    var context = this
    while (context is ContextWrapper) {
        if (context is T) {
            return context
        }
        context = context.baseContext
    }
    return null
}

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T : Activity> Fragment.activity(): T = requireActivity() as T

fun Context.requireActivity(): Activity =
    this.activity
        ?: ActivityManager.currentActivity.get()
        ?: error("No Activity found")

fun Context.requireComponentActivity() =
    (this.activity ?: ActivityManager.currentActivity.get()) as? ComponentActivity
        ?: error("No ComponentActivity found")

val Context.lifecycle: Lifecycle
    get() {
        var context: Context? = this
        while (true) {
            when (context) {
                is LifecycleOwner -> return context.lifecycle
                !is ContextWrapper -> error("This should never happen!")
                else -> context = context.baseContext
            }
        }
    }

@ColorInt
fun Context.getThemeColor(
    @AttrRes attrColor: Int,
    @ColorInt defColor: Int = Color.TRANSPARENT,
): Int {
    return MaterialColors.getColor(this, attrColor, defColor)
}
