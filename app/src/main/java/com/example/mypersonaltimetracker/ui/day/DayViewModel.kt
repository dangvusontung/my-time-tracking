package com.example.mypersonaltimetracker.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.data.DayExceptionEntity
import com.example.mypersonaltimetracker.data.SessionEntity
import com.example.mypersonaltimetracker.domain.DaySummary
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.DayType
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.domain.SessionValidationError
import com.example.mypersonaltimetracker.domain.SessionValidator
import com.example.mypersonaltimetracker.office.OfficeStatusService
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

    val uiState: StateFlow<DayUiState> = combine(
        repo.observeSessions(),
        repo.observeExceptions(),
        repo.observeDayNotes(),
        settingsRepo.settings,
        nowTicker(),
    ) { sessions, exceptions, notes, settings, now ->
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

        val summary = DaySummaryCalculator.compute(
            sessions.map { SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli)) },
            exception?.type,
            date, now,
            settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
            zone,
        )

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
            val errors = SessionValidator.validate(candidate, others, Instant.now())
            if (errors.isNotEmpty()) {
                onError(errors)
                return@launch
            }
            repo.upsertSession(SessionEntity(id, inAt.toEpochMilli(), outAt?.toEpochMilli(), isEstimated))
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
