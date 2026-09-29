package com.yenaly.han1meviewer.ui.screen.home.dailycheckin

import com.yenaly.han1meviewer.ui.viewmodel.MonthlyStats
import java.time.LocalDate
import java.time.YearMonth

data class DailyCheckInUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val records: Map<LocalDate, Int> = emptyMap(),
    val checkedDays: Int = 0,
    val monthlyTotal: Int = 0,
    val bestStreakThisMonth: Int = 0,
    val monthlyStats: MonthlyStats = MonthlyStats(),
    val today: LocalDate = LocalDate.now(),
    val todayCount: Int = 0,
)

sealed interface DailyCheckInEvent {

    data class OnDateClick(val date: LocalDate) : DailyCheckInEvent

    data class OnDateLongClick(val date: LocalDate) : DailyCheckInEvent

    data object OnPreviousMonth : DailyCheckInEvent

    data object OnNextMonth : DailyCheckInEvent

    data object OnTodayCheckIn : DailyCheckInEvent

    data object OnTodayClear : DailyCheckInEvent

    data object OnShowReport : DailyCheckInEvent
}
