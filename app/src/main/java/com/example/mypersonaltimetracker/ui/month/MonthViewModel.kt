package com.example.mypersonaltimetracker.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.MonthTotal
import com.example.mypersonaltimetracker.domain.MonthTotalsCalculator
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.ui.day.nowTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MonthUiState(
    val months: List<MonthTotal> = emptyList(),
)

private const val MAX_MONTHS = 12

class MonthViewModel(app: App) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<MonthUiState> = combine(
        app.container.repository.observeSessions(),
        app.container.repository.observeExceptions(),
        app.container.settings.settings,
        nowTicker(),
    ) { sessions, exceptions, settings, now ->
        val spans = sessions.map {
            SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli))
        }
        val exceptionByDate = exceptions.associateBy({ LocalDate.parse(it.date) }, { it.type })
        val dates = sessions.map { Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() }.toSet() +
            exceptionByDate.keys

        val summaries = dates.associateWith { d ->
            DaySummaryCalculator.compute(
                spans, exceptionByDate[d], d, now,
                settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
                zone,
            )
        }
        MonthUiState(months = MonthTotalsCalculator.aggregate(summaries, MAX_MONTHS))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthUiState())
}
