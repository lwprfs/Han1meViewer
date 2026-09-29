package com.yenaly.han1meviewer.ui.screen.home.preview

import androidx.compose.foundation.lazy.LazyListState

internal fun currentCodeFrom(year: Int, month: Int): String = "%04d%02d".format(year, month)

internal fun currentDateCode(): String {
    val now = java.time.LocalDate.now()
    return currentCodeFrom(now.year, now.monthValue)
}

internal fun toNormalDateLabel(code: String): String {
    val year = code.substring(0, 4).toInt()
    val month = code.substring(4, 6).toInt()
    return "$year/$month"
}

internal fun shiftMonthCode(code: String, delta: Int): String {
    var year = code.substring(0, 4).toInt()
    var month = code.substring(4, 6).toInt() + delta
    while (month < 1) {
        month += 12
        year -= 1
    }
    while (month > 12) {
        month -= 12
        year += 1
    }
    return currentCodeFrom(year, month)
}

internal suspend fun centerPreviewTourItem(
    listState: LazyListState,
    index: Int,
) {
    if (index < 0) return
    listState.animateScrollToItem(index)
}
