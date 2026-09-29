@file:JvmName("TextUtil")

package com.yenaly.yenaly_libs.utils

import android.text.format.Formatter
import androidx.annotation.IntRange
import java.util.*

fun Long.secondToTimeCase(): String {
    val second: Long = this % 60
    var minute: Long = this / 60
    var hour = 0L
    if (minute >= 60) {
        hour = minute / 60
        minute %= 60
    }
    val secondString = if (second < 10) "0$second" else second.toString()
    val minuteString = if (minute < 10) "0$minute" else minute.toString()
    val hourString = if (hour < 10) "0$hour" else hour.toString()
    return if (hour != 0L) "$hourString:$minuteString:$secondString" else "$minuteString:$secondString"
}

fun Long.formatPlayCount(): String {
    return when {
        this < 0 -> "0"
        this < 1_0000 -> this.toString()
        this < 1_0000_0000 -> String.format(
            Locale.getDefault(),
            "%d.%02d万",
            this / 1_0000,
            this % 1_0000 / 100
        )

        else -> String.format(
            Locale.getDefault(),
            "%d.%02d亿",
            this / 1_0000_0000,
            this % 1_0000_0000 / 100_0000
        )
    }
}

@Deprecated("Use formatFileSizeV2 instead.", ReplaceWith("formatFileSizeV2()"))
fun Long.formatFileSize(short: Boolean = true): String {
    if (short) {
        return Formatter.formatShortFileSize(applicationContext, this)
    }
    return Formatter.formatFileSize(applicationContext, this)
}

private val SI_UNITS = arrayOf("B", "kB", "MB", "GB", "TB")
private val IEC_UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

fun Long.formatFileSizeV2(
    useSi: Boolean = false,
    @IntRange(from = 0) decimalPlaces: Int = 1,
    stripTrailingZeros: Boolean = true,
): String {
    val unit = if (useSi) 1000 else 1024
    if (this < unit) return "$this B"

    val units = if (useSi) SI_UNITS else IEC_UNITS

    var value = this.toDouble()
    var unitIndex = 0

    while (value >= unit && unitIndex < units.size - 1) {
        value /= unit
        unitIndex++
    }

    return if (decimalPlaces == 0 || (stripTrailingZeros && value % 1 == 0.0)) {
        "%.0f %s".format(Locale.getDefault(), value, units[unitIndex])
    } else {
        "%.${decimalPlaces}f %s".format(Locale.getDefault(), value, units[unitIndex])
    }
}

fun Long.formatBytesPerSecond(
    useSi: Boolean = false,
    @IntRange(from = 0) decimalPlaces: Int = 1,
    stripTrailingZeros: Boolean = true,
): String {
    return formatFileSizeV2(useSi, decimalPlaces, stripTrailingZeros) + "/s"
}
