@file:Suppress("unused")

package com.yenaly.yenaly_libs.utils

import android.content.res.Configuration
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Suppress("DEPRECATION")
fun Window.addStatusBarWithColor(@ColorInt color: Int) {

    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    statusBarColor = color
}

val Window.currentStatusBarHeight: Int
    get() {
        val windowInsetsCompat = ViewCompat.getRootWindowInsets(decorView)
        return windowInsetsCompat?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
            ?: statusBarHeight
    }

val Window.currentNavBarHeight: Int
    get() {
        val windowInsetsCompat = ViewCompat.getRootWindowInsets(decorView)
        return windowInsetsCompat?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
            ?: navBarHeight
    }

val Window.isStatusBarVisible: Boolean
    get() {
        val windowInsetsCompat = ViewCompat.getRootWindowInsets(decorView)
        return windowInsetsCompat?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
    }

val Window.isNavBarVisible: Boolean
    get() {
        val windowInsetsCompat = ViewCompat.getRootWindowInsets(decorView)
        return windowInsetsCompat?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
    }

fun Window.controlStatusBar(isVisible: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    if (isVisible) {
        controller.show(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }
}

fun Window.controlNavBar(isVisible: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    if (isVisible) {
        controller.show(WindowInsetsCompat.Type.navigationBars())
    } else {
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
}

fun Window.controlSystemBars(isVisible: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    if (isVisible) {
        controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

fun Window.setSystemBarIconLightMode(statusBar: Boolean, navBar: Boolean = false) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.isAppearanceLightStatusBars = statusBar
    controller.isAppearanceLightNavigationBars = navBar
}

inline val Window.isImeVisible: Boolean
    get() {
        val windowInsetsCompat = ViewCompat.getRootWindowInsets(decorView)
        return windowInsetsCompat?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
    }

fun Window.showIme(ime: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    if (ime) {
        controller.show(WindowInsetsCompat.Type.ime())
    } else {
        controller.hide(WindowInsetsCompat.Type.ime())
    }
}

val isAppDarkMode: Boolean
    get() {
        return applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

fun Window.isDecorFitsSystemWindows(decorFitsSystemWindows: Boolean) =
    WindowCompat.setDecorFitsSystemWindows(this, decorFitsSystemWindows)
