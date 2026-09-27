package com.example.mypersonaltimetracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.DayType
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.ui.day.nowTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarDay(
    val date: LocalDate,
    val totalMinutes: Int,
    val exception: DayType?,
    val hasNote: Boolean,
)

data class CalendarUiState(
    val days: Map<LocalDate, CalendarDay> = emptyMap(),
)

/** Per-day totals across ALL history, for the month calendar grid. */
class CalendarViewModel(app: App) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<CalendarUiState> = combine(
        app.container.repository.observeSessions(),
        app.container.repository.observeExceptions(),
        app.container.repository.observeDayNotes(),
        app.container.settings.settings,
        nowTicker(),
    ) { sessions, exceptions, notes, settings, now ->
        val spans = sessions.map {
            SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli))
        }
        val exceptionByDate = exceptions.associateBy({ LocalDate.parse(it.date) }, { it.type })
        val noteDates = notes.map { LocalDate.parse(it.date) }.toSet()
        val dates = sessions.map { Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() }.toSet() +
            exceptionByDate.keys + noteDates

        CalendarUiState(
            days = dates.associateWith { d ->
                val summary = DaySummaryCalculator.compute(
                    spans, exceptionByDate[d], d, now,
                    settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
                    zone,
                    settings.workdayCountStartMin, settings.workdayCountEndMin,
                )
                CalendarDay(d, summary.totalMinutes, exceptionByDate[d], hasNote = d in noteDates)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())
}
