@file:JvmName("ClickUtil")
@file:Suppress("unused")

package com.yenaly.yenaly_libs.utils.view

import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun View.clickFlow(): Flow<View> {
    return callbackFlow {
        setOnClickListener {
            trySend(it)
        }
        awaitClose { setOnClickListener(null) }
    }
}

fun View.click(lifecycle: Lifecycle, onClick: View.OnClickListener) {
    clickFlow().onEach {
        onClick.onClick(it)
    }.launchIn(lifecycle.coroutineScope)
}

fun View.clickDelayed(
    lifecycle: Lifecycle,
    delayMillis: Long = 500,
    onClick: View.OnClickListener
) {
    clickFlow().onEach {
        delay(delayMillis)
        onClick.onClick(it)
    }.launchIn(lifecycle.coroutineScope)
}

fun View.clickTrigger(
    lifecycle: Lifecycle,
    intervalMillis: Long = 500,
    onClick: View.OnClickListener
) = ClickTrigger().bind(this, lifecycle, intervalMillis, onClick)

fun View.clickWithCondition(
    lifecycle: Lifecycle,
    @IdRes tag: Int,
    onClick: View.OnClickListener
) {

    setTag(tag, true)
    clickFlow().onEach {
        if (it.getTag(tag) == true) {
            onClick.onClick(it)
        }
    }.launchIn(lifecycle.coroutineScope)
}

private class ClickTrigger {
    private var lastMillis: Long = 0

    fun bind(
        view: View, lifecycle: Lifecycle,
        intervalMillis: Long = 500,
        onClick: View.OnClickListener
    ) {
        view.clickFlow().onEach {
            val currentMillis = System.currentTimeMillis()
            if (currentMillis - lastMillis < intervalMillis) {
                return@onEach
            }
            lastMillis = currentMillis
            onClick.onClick(it)
        }.launchIn(lifecycle.coroutineScope)
    }
}
