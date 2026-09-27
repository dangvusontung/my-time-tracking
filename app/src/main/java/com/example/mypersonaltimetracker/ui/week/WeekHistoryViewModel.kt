package com.example.mypersonaltimetracker.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.domain.WeekProgress
import com.example.mypersonaltimetracker.domain.WeekProgressCalculator
import com.example.mypersonaltimetracker.ui.day.nowTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WeekHistoryRow(
    val weekStart: LocalDate,
    val isCurrentWeek: Boolean,
    val progress: WeekProgress,
) {
    /** "Đủ" / "Thiếu Xh" / "Vượt Xh" — computed in the screen via formatMinutes. */
    val overMinutes: Int get() = progress.weekTotalMinutes - progress.targetMinutes
}

data class WeekHistoryUiState(
    val rows: List<WeekHistoryRow> = emptyList(),
)

/** How many past weeks (besides the current one) are listed when they contain any data. */
private const val PAST_WEEKS = 12

class WeekHistoryViewModel(private val app: App) : ViewModel() {

    private val repo = app.container.repository
    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<WeekHistoryUiState> = combine(
        repo.observeSessions(),
        repo.observeExceptions(),
        repo.observeWeekTargets(),
        app.container.settings.settings,
        nowTicker(),
    ) { sessions, exceptions, targets, settings, now ->
        val today = LocalDate.now(zone)
        val currentWeekStart = WeekProgressCalculator.weekStartOf(today)
        val spans = sessions.map {
            SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli))
        }
        val exceptionByDate = exceptions.associateBy({ LocalDate.parse(it.date) }, { it.type })
        val weeksWithData = sessions
            .map { WeekProgressCalculator.weekStartOf(Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate()) }
            .toSet() + exceptions.map { WeekProgressCalculator.weekStartOf(LocalDate.parse(it.date)) } +
            targets.map { LocalDate.parse(it.weekStart) }

        val rows = (0..PAST_WEEKS)
            .map { currentWeekStart.minusWeeks(it.toLong()) }
            .filter { it == currentWeekStart || it in weeksWithData }
            .map { weekStart ->
                val isCurrent = weekStart == currentWeekStart
                // For a past week, pretend "today" is its Sunday so daysLeft / perDayNeeded are 0.
                val effectiveToday = if (isCurrent) today else weekStart.plusDays(6)
                val summaries = (0..6).associate { i ->
                    val d = weekStart.plusDays(i.toLong())
                    d to DaySummaryCalculator.compute(
                        spans, exceptionByDate[d], d, now,
                        settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
                        zone,
                        settings.workdayCountStartMin, settings.workdayCountEndMin,
                    )
                }
                val custom = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
                WeekHistoryRow(
                    weekStart = weekStart,
                    isCurrentWeek = isCurrent,
                    progress = WeekProgressCalculator.compute(
                        effectiveToday, weekStart, summaries, exceptionByDate, custom,
                        settings.defaultWeeklyTargetMinutes, settings.workdays,
                    ),
                )
            }
        WeekHistoryUiState(rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekHistoryUiState())

    fun setCustomTarget(weekStart: LocalDate, minutes: Int) {
        viewModelScope.launch { repo.setWeekTarget(weekStart, minutes) }
    }

    fun clearCustomTarget(weekStart: LocalDate) {
        viewModelScope.launch { repo.clearWeekTarget(weekStart) }
    }
}
