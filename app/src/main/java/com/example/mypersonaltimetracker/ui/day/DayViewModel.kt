package com.example.mypersonaltimetracker.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.data.DayExceptionEntity
import com.example.mypersonaltimetracker.data.SessionEntity
import com.example.mypersonaltimetracker.domain.DaySummary
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.DayType
import com.example.mypersonaltimetracker.domain.EarliestLeaveCalculator
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.domain.SessionValidationError
import com.example.mypersonaltimetracker.domain.SessionValidator
import com.example.mypersonaltimetracker.domain.WeekProgressCalculator
import com.example.mypersonaltimetracker.office.OfficeStatusService
import com.example.mypersonaltimetracker.reminder.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun nowTicker(): kotlinx.coroutines.flow.Flow<Instant> = flow {
    while (true) {
        emit(Instant.now())
        delay(1000)
    }
}

data class SessionUi(
    val id: Long,
    val inAt: Instant,
    val outAt: Instant?,
    val durationMinutes: Int,
    val isEstimated: Boolean,
) {
    val isRunning: Boolean get() = outAt == null
}

data class DayUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val now: Instant = Instant.now(),
    val sessions: List<SessionUi> = emptyList(),
    val summary: DaySummary = DaySummary(0, 0, 0),
    val exception: DayExceptionEntity? = null,
    val note: String? = null,
    val runningSession: SessionUi? = null,
    /** Open sessions whose inAt local date is before [date] — only populated for today. */
    val staleSessions: List<SessionUi> = emptyList(),
    /** Mục tiêu giờ làm của ngày (mục tiêu tuần / số ngày làm việc), null nếu không xác định. */
    val dailyTargetMinutes: Int? = null,
    /** Giờ về sớm nhất (đủ "luật rừng" 8h30) — chỉ cho hôm nay, null nếu không có phiên đang chạy. */
    val earliestLeaveAt: Instant? = null,
) {
    val isRunning: Boolean get() = runningSession != null
    val overTenHours: Boolean get() = summary.totalMinutes > 600
}

class DayViewModel(
    private val app: App,
    val date: LocalDate,
) : ViewModel() {

    private val repo = app.container.repository
    private val settingsRepo = app.container.settings
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Settings + mục tiêu ngày (từ mục tiêu tuần tuỳ chỉnh nếu có). */
    private val configFlow = combine(
        settingsRepo.settings,
        repo.observeWeekTargets(),
    ) { settings, targets ->
        val weekStart = WeekProgressCalculator.weekStartOf(LocalDate.now(zone))
        val weekly = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
            ?: settings.defaultWeeklyTargetMinutes
        val daily = if (settings.workdays.isEmpty()) null else weekly / settings.workdays.size
        settings to daily
    }

    val uiState: StateFlow<DayUiState> = combine(
        repo.observeSessions(),
        repo.observeExceptions(),
        repo.observeDayNotes(),
        configFlow,
        nowTicker(),
    ) { sessions, exceptions, notes, config, now ->
        val (settings, dailyTarget) = config
        val today = LocalDate.now(zone)
        val exception = exceptions.firstOrNull { it.date == date.toString() }
        val note = notes.firstOrNull { it.date == date.toString() }?.note

        fun toUi(e: SessionEntity): SessionUi {
            val end = e.outAt?.let(Instant::ofEpochMilli) ?: now
            val durationMin = maxOf(0L, (end.toEpochMilli() - e.inAt) / 60000).toInt()
            return SessionUi(e.id, Instant.ofEpochMilli(e.inAt), e.outAt?.let(Instant::ofEpochMilli), durationMin, e.isEstimated)
        }

        val daySessions = sessions
            .filter { Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() == date }
            .sortedBy { it.inAt }
            .map(::toUi)

        val spans = sessions.map {
            SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli))
        }
        val summary = DaySummaryCalculator.compute(
            spans,
            exception?.type,
            date, now,
            settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
            zone,
            settings.workdayCountStartMin, settings.workdayCountEndMin,
        )

        val earliestLeave = if (date == today) {
            EarliestLeaveCalculator.compute(
                spans, exception?.type, date, now,
                settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
                zone,
                settings.workdayCountStartMin, settings.workdayCountEndMin,
            )
        } else {
            null
        }

        val stale = if (date == today) {
            sessions.filter { it.outAt == null && Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() < today }
                .map(::toUi)
        } else {
            emptyList()
        }

        DayUiState(
            date = date,
            isToday = date == today,
            now = now,
            sessions = daySessions,
            summary = summary,
            exception = exception,
            note = note,
            runningSession = daySessions.firstOrNull { it.isRunning }
                ?: sessions.firstOrNull { it.outAt == null }?.let(::toUi),
            staleSessions = stale,
            dailyTargetMinutes = dailyTarget,
            earliestLeaveAt = earliestLeave,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayUiState(date = date, isToday = true))

    fun toggle() {
        viewModelScope.launch {
            repo.toggle(Instant.now())
            OfficeStatusService.sync(app)
        }
    }

    fun deleteSession(session: SessionUi) {
        viewModelScope.launch {
            repo.deleteSession(
                SessionEntity(session.id, session.inAt.toEpochMilli(), session.outAt?.toEpochMilli(), session.isEstimated),
            )
            ReminderScheduler.armDailyTarget(app)
        }
    }

    fun saveSession(
        id: Long,
        inAt: Instant,
        outAt: Instant?,
        isEstimated: Boolean,
        onError: (List<SessionValidationError>) -> Unit,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            val allSessions = repo.observeSessions().first()
            val candidate = SessionSpan(id, inAt, outAt)
            val others = allSessions.map {
                SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli))
            }
            val errors = SessionValidator.validate(candidate, others, Instant.now(), zone)
            if (errors.isNotEmpty()) {
                onError(errors)
                return@launch
            }
            repo.upsertSession(SessionEntity(id, inAt.toEpochMilli(), outAt?.toEpochMilli(), isEstimated))
            ReminderScheduler.armDailyTarget(app)
            onSuccess()
        }
    }

    fun setException(type: DayType, note: String?) {
        viewModelScope.launch { repo.setException(date, type, note) }
    }

    fun clearException() {
        viewModelScope.launch { repo.clearException(date) }
    }

    fun saveNote(note: String) {
        viewModelScope.launch { repo.setDayNote(date, note) }
    }
}
