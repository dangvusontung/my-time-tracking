package com.example.mypersonaltimetracker.data

import androidx.room.withTransaction
import com.example.mypersonaltimetracker.domain.DayType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class TimeRepository(private val db: AppDatabase) {

    private val sessions = db.sessionDao()
    private val exceptions = db.dayExceptionDao()
    private val weekTargets = db.weekTargetDao()
    private val dayNotes = db.dayNoteDao()

    fun observeSessions(): Flow<List<SessionEntity>> = sessions.observeAll()
    fun observeOpenSessions(): Flow<List<SessionEntity>> = sessions.observeOpen()
    fun observeExceptions(): Flow<List<DayExceptionEntity>> = exceptions.observeAll()
    fun observeWeekTargets(): Flow<List<WeekTargetEntity>> = weekTargets.observeAll()
    fun observeDayNotes(): Flow<List<DayNoteEntity>> = dayNotes.observeAll()

    suspend fun clockIn(now: Instant): Long =
        sessions.insert(SessionEntity(inAt = now.toEpochMilli()))

    /** Closes the current open session, if any. */
    suspend fun clockOut(now: Instant) {
        sessions.getOpen()?.let { sessions.update(it.copy(outAt = now.toEpochMilli())) }
    }

    /** Performs "Vào office" / "Ra office" depending on current state. */
    suspend fun toggle(now: Instant): Boolean {
        val open = sessions.getOpen()
        return if (open == null) {
            sessions.insert(SessionEntity(inAt = now.toEpochMilli()))
            true
        } else {
            sessions.update(open.copy(outAt = now.toEpochMilli()))
            false
        }
    }

    suspend fun upsertSession(session: SessionEntity) {
        if (session.id == 0L) sessions.insert(session) else sessions.update(session)
    }

    suspend fun deleteSession(session: SessionEntity) = sessions.delete(session)

    suspend fun setException(date: LocalDate, type: DayType, note: String?) =
        exceptions.upsert(DayExceptionEntity(date.toString(), type, note?.ifBlank { null }))

    suspend fun clearException(date: LocalDate) = exceptions.delete(date.toString())

    suspend fun setWeekTarget(weekStart: LocalDate, targetMinutes: Int) =
        weekTargets.upsert(WeekTargetEntity(weekStart, targetMinutes))

    suspend fun clearWeekTarget(weekStart: LocalDate) = weekTargets.delete(weekStart.toString())

    /** Blank notes delete the row instead of storing an empty string. */
    suspend fun setDayNote(date: LocalDate, note: String) {
        if (note.isBlank()) dayNotes.delete(date.toString())
        else dayNotes.upsert(DayNoteEntity(date.toString(), note.trim()))
    }

    /** Replaces ALL data (backup restore). Runs in a single transaction. */
    suspend fun replaceAll(
        newSessions: List<SessionEntity>,
        newExceptions: List<DayExceptionEntity>,
        newWeekTargets: List<WeekTargetEntity>,
        newDayNotes: List<DayNoteEntity>,
    ) {
        db.withTransaction {
            sessions.deleteAll()
            exceptions.deleteAll()
            weekTargets.deleteAll()
            dayNotes.deleteAll()
            sessions.insertAll(newSessions)
            exceptions.insertAll(newExceptions)
            weekTargets.insertAll(newWeekTargets)
            dayNotes.insertAll(newDayNotes)
        }
    }
}
