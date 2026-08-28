package com.example.mypersonaltimetracker.ui.history

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

data class HistoryRow(
    val date: LocalDate,
    val totalMinutes: Int,
    val exception: DayType?,
    val hasNote: Boolean,
)

data class HistoryUiState(
    val rows: List<HistoryRow> = emptyList(),
)

class HistoryViewModel(app: App) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<HistoryUiState> = combine(
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
        val sessionDates = sessions.map { Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() }.toSet()
        val today = LocalDate.now(zone)
        val cutoff = today.minusDays(60)

        val dates = (sessionDates + exceptionByDate.keys).filter { it >= cutoff }.sortedDescending()
        HistoryUiState(
            rows = dates.map { d ->
                val summary = DaySummaryCalculator.compute(
                    spans, exceptionByDate[d], d, now,
                    settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
                    zone,
                )
                HistoryRow(d, summary.totalMinutes, exceptionByDate[d], hasNote = d in noteDates)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())
}
