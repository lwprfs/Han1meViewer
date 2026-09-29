package com.yenaly.han1meviewer.util

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.chad.library.adapter4.BaseQuickAdapter
import com.yenaly.han1meviewer.R
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun com.chad.library.adapter4.BaseQuickAdapter<*, *>.setStateViewLayout(
    @LayoutRes layoutRes: Int,
    text: String? = null,
) {
    val view = View.inflate(context, layoutRes, FrameLayout(context))
    view.findViewById<TextView>(R.id.tv_empty).text =
        text ?: context.getString(R.string.here_is_empty)
    stateView = view
}

fun com.chad.library.adapter4.BaseQuickAdapter<*, *>.setStateViewLayout(
    view: View,
    text: String? = null,
) {
    view.findViewById<TextView>(R.id.tv_empty).text =
        text ?: applicationContext.getString(R.string.here_is_empty)
    stateView = view
}

suspend fun <T : Any> BaseQuickAdapter<T, *>.awaitSubmitList(list: List<T>?) =
    suspendCoroutine { cont ->
        submitList(list) {
            cont.resume(Unit)
        }
    }

@OptIn(ExperimentalContracts::class)
@Suppress("NOTHING_TO_INLINE")
@Deprecated("Use safe call instead, this can easily cause NPE.", ReplaceWith("this ?: return"))
inline fun <T> T?.notNull(): T {
    contract {
        returns() implies (this@notNull != null)
    }
    return checkNotNull(this)
}
