package com.yenaly.han1meviewer.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder

fun NavController.canNavigateSafely(): Boolean {
    val currentState = currentBackStackEntry?.lifecycle?.currentState
    return currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true
}

private var lastNavKey: String? = null
private var lastNavAt: Long = 0L

fun <T : Any> NavController.navigateSafely(
    route: T,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (!canNavigateSafely()) return

    val key = route.toString()
    val now = android.os.SystemClock.uptimeMillis()
    if (key == lastNavKey && now - lastNavAt < 600L) return
    lastNavKey = key
    lastNavAt = now
    navigate(route) {
        launchSingleTop = false
        builder()
    }
}

fun NavController.popBackStackSafely(): Boolean {
    val current = currentBackStackEntry ?: return false
    if (!current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
    if (previousBackStackEntry == null) return false
    return popBackStack()
}

fun NavController.popToRootSafely(): Boolean = try {
    popBackStack(graph.findStartDestination().id, inclusive = false)
} catch (_: IllegalStateException) {

    false
}
