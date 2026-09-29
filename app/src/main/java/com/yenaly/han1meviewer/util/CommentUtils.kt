package com.yenaly.han1meviewer.util

fun parseTimeStrToMinutes(timeStr: String): Int {
    return when {
        "分鐘前" in timeStr -> timeStr.removeSuffix("分鐘前").trim().toInt()
        "小時前" in timeStr -> timeStr.removeSuffix("小時前").trim().toInt() * 60
        "天前" in timeStr -> timeStr.removeSuffix("天前").trim().toInt() * 60 * 24
        "週前" in timeStr -> timeStr.removeSuffix("週前").trim().toInt() * 60 * 24 * 7
        "個月前" in timeStr -> timeStr.removeSuffix("個月前").trim().toInt() * 60 * 24 * 30
        "年前" in timeStr -> timeStr.removeSuffix("年前").trim().toInt() * 60 * 24 * 365
        else -> 0
    }
}

fun <Hatsune, Miku : Comparable<Miku>> List<Hatsune>.safeSortedBy(
    selector: (Hatsune) -> Miku?,
    descending: Boolean = false
): List<Hatsune> {
    return runCatching {
        if (descending) this.sortedByDescending { selector(it) ?: return@sortedByDescending null }
        else this.sortedBy { selector(it) ?: return@sortedBy null }
    }.getOrElse { this }
}
