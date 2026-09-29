@file:JvmName("ClipboardUtil")

package com.yenaly.yenaly_libs.utils

import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun copyTextToClipboard(
    text: CharSequence?,
    label: CharSequence? = null,
) {
    val clipboardManager = applicationContext.getSystemService<ClipboardManager>()
    val clipData = ClipData.newPlainText(label, text)
    clipboardManager?.setPrimaryClip(clipData)
}

@JvmSynthetic
fun CharSequence?.copyToClipboard(label: CharSequence? = null) = copyTextToClipboard(this, label)

val textsFromClipboard: Sequence<CharSequence?>
    get() = sequence {
        val context = applicationContext
        val clipboardManager = context.getSystemService<ClipboardManager>()
        val clipData = clipboardManager?.primaryClip ?: return@sequence
        for (i in 0..<clipData.itemCount) {
            clipData.getItemAt(i)?.coerceToText(context)?.let { str ->
                yield(str)
            }
        }
    }

val textFromClipboard: CharSequence?
    get() {
        val context = applicationContext
        val clipboardManager = context.getSystemService<ClipboardManager>()
        val clipData = clipboardManager?.primaryClip ?: return null
        if (clipData.itemCount > 0) {
            clipData.getItemAt(0)?.let { item ->
                return item.coerceToText(context)
            }
        }
        return null
    }

fun clearClipboard() {
    val clipboardManager = applicationContext.getSystemService<ClipboardManager>()
    val clipData = ClipData.newPlainText(null, null)
    clipboardManager?.setPrimaryClip(clipData)
}

fun clipboardFlow(distinct: Boolean): Flow<Sequence<CharSequence?>> {
    return callbackFlow {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>()
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            trySend(textsFromClipboard)
        }
        clipboardManager?.addPrimaryClipChangedListener(listener)
        awaitClose { clipboardManager?.removePrimaryClipChangedListener(listener) }
    }.run {
        if (distinct) {
            distinctUntilChanged { old, new ->
                val oldIterator = old.iterator()
                val newIterator = new.iterator()
                while (oldIterator.hasNext() && newIterator.hasNext()) {
                    if (oldIterator.next() != newIterator.next()) {
                        return@distinctUntilChanged false
                    }
                }
                oldIterator.hasNext() == newIterator.hasNext()
            }
        } else {
            this
        }
    }
}
