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

data class WeekUiState(
    val progress: WeekProgress? = null,
    /** Tổng phút từng ngày trong tuần, Thứ Hai..Chủ nhật. */
    val dayTotals: List<Int> = List(7) { 0 },
    val weekStart: LocalDate = LocalDate.now(),
    /** Mục tiêu trung bình mỗi ngày làm việc (để vẽ đường tham chiếu trên biểu đồ). */
    val perDayTargetMinutes: Int? = null,
)

class WeekViewModel(app: App) : ViewModel() {

    private val repo = app.container.repository
    private val settingsRepo = app.container.settings
    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<WeekUiState> = combine(
        repo.observeSessions(),
        repo.observeExceptions(),
        repo.observeWeekTargets(),
        settingsRepo.settings,
        nowTicker(),
    ) { sessions, exceptions, targets, settings, now ->
        val today = LocalDate.now(zone)
        val weekStart = WeekProgressCalculator.weekStartOf(today)
        val spans = sessions.map {
            SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli))
        }
        val exceptionByDate = exceptions.associateBy({ LocalDate.parse(it.date) }, { it.type })
        val summaries = (0..6).associate { i ->
            val d = weekStart.plusDays(i.toLong())
            d to DaySummaryCalculator.compute(
                spans, exceptionByDate[d], d, now,
                settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
                zone,
            )
        }
        val custom = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
        WeekUiState(
            progress = WeekProgressCalculator.compute(
                today, weekStart, summaries, exceptionByDate, custom, settings.defaultWeeklyTargetMinutes,
                settings.workdays,
            ),
            dayTotals = (0..6).map { i -> summaries[weekStart.plusDays(i.toLong())]?.totalMinutes ?: 0 },
            weekStart = weekStart,
            perDayTargetMinutes = if (settings.workdays.isEmpty()) {
                null
            } else {
                (custom ?: settings.defaultWeeklyTargetMinutes) / settings.workdays.size
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekUiState())

    fun setCustomTarget(minutes: Int) {
        viewModelScope.launch {
            val weekStart = WeekProgressCalculator.weekStartOf(LocalDate.now(zone))
            repo.setWeekTarget(weekStart, minutes)
        }
    }

    fun clearCustomTarget() {
        viewModelScope.launch {
            val weekStart = WeekProgressCalculator.weekStartOf(LocalDate.now(zone))
            repo.clearWeekTarget(weekStart)
        }
    }
}
