package com.yenaly.han1meviewer.ui.screen.home.dailycheckin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.CalendarContract
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.ui.graphics.Color
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.CheckInType
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import java.time.LocalDate
import java.time.YearMonth

internal val contributionColors = listOf(
    Color.Transparent,
    Color(0xFF9BE9A8),
    Color(0xFF40C463),
    Color(0xFF30A14E),
    Color(0xFF216E39),
)

internal fun getContributionLevel(count: Int): Int = when {
    count <= 0 -> 0
    count == 1 -> 1
    count == 2 -> 2
    count in 3..4 -> 3
    else -> 4
}

fun typeEmoji(type: String): String = when (type) {
    CheckInType.MASTURBATION.storeName -> "\uD83E\uDD1C"
    CheckInType.WET_DREAM.storeName -> "\uD83D\uDCA4"
    CheckInType.SEX.storeName -> "\uD83D\uDC91"
    CheckInType.ORAL.storeName -> "\uD83D\uDC45"
    CheckInType.OTHER.storeName -> "\u2753"
    else -> "\uD83D\uDCCA"
}

fun computeStreaks(
    records: Map<LocalDate, Int>,
    month: YearMonth,
): Pair<Int, Int> {
    val today = LocalDate.now()
    var currentStreak = 0
    var cursor = today
    while ((records[cursor] ?: 0) > 0) {
        currentStreak++
        cursor = cursor.minusDays(1)
    }

    var bestStreak = 0
    var streak = 0
    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        if ((records[date] ?: 0) > 0) {
            streak++
            if (streak > bestStreak) bestStreak = streak
        } else {
            streak = 0
        }
    }
    return currentStreak to bestStreak
}

internal fun buildYearWeeks(year: Int): List<List<LocalDate?>> {
    val start = LocalDate.of(year, 1, 1)
    val end = LocalDate.of(year, 12, 31)
    val weeks = mutableListOf<MutableList<LocalDate?>>()
    var currentWeek = MutableList<LocalDate?>(7) { null }
    var dayIndex = start.dayOfWeek.value - 1
    var date = start
    while (!date.isAfter(end)) {
        currentWeek[dayIndex] = date
        dayIndex++
        if (dayIndex == 7) {
            weeks.add(currentWeek)
            currentWeek = MutableList(7) { null }
            dayIndex = 0
        }
        date = date.plusDays(1)
    }
    if (currentWeek.any { it != null }) {
        weeks.add(currentWeek)
    }
    return weeks
}

internal fun buildMonthLabels(
    year: Int,
    weeks: List<List<LocalDate?>>,
    monthFormat: String,
): List<Pair<String, Int>> {
    val labels = mutableListOf<Pair<String, Int>>()
    for (month in 1..12) {
        val firstDay = LocalDate.of(year, month, 1)
        val weekIdx = weeks.indexOfFirst { week -> firstDay in week }
        if (weekIdx >= 0) {
            labels.add(monthFormat.format(month) to weekIdx)
        }
    }
    return labels
}

fun createCalendarEvent(context: Context, date: LocalDate) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.dir/event")
        putExtra(
            CalendarContract.Events.TITLE,
            context.getString(R.string.calendar_title, date.monthValue, date.dayOfMonth)
        )
        putExtra(CalendarContract.Events.DESCRIPTION, context.getString(R.string.calendar_desc))
        putExtra(
            CalendarContract.Events.EVENT_LOCATION,
            context.getString(R.string.calendar_location)
        )
        putExtra(
            CalendarContract.EXTRA_EVENT_BEGIN_TIME,
            date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        putExtra(
            CalendarContract.EXTRA_EVENT_END_TIME,
            date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                .toEpochMilli()
        )
        putExtra(CalendarContract.Events.ALL_DAY, true)
        putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
    }
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        GlobalToasts.show(context.getString(R.string.no_calendar_app), level = GlobalToasts.ToastLevel.WARNING)
    }
}

fun Activity.updateReportWindowMode(isFullscreen: Boolean) {
    requestedOrientation = if (isFullscreen) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.apply {
            if (isFullscreen) {
                hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            }
        }
    } else {
        @Suppress("DEPRECATION")
        run {
            window.decorView.systemUiVisibility = if (isFullscreen) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
}
