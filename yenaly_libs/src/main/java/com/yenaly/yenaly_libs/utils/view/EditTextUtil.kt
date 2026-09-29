@file:JvmName("EditTextUtil")

package com.yenaly.yenaly_libs.utils.view

import android.view.Window
import android.widget.EditText
import com.yenaly.yenaly_libs.utils.showIme

fun EditText.showIme(window: Window) = this.let {
    it.isFocusable = true
    it.isFocusableInTouchMode = true
    if (it.hasFocus()) {
        window.showIme(true)
    } else {
        it.requestFocus()
        repeat(2) {
            window.showIme(true)
        }
    }
}

fun EditText.hideIme(window: Window) = this.let {
    it.clearFocus()
    window.showIme(false)
}
