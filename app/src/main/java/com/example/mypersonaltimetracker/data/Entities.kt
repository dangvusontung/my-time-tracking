package com.example.mypersonaltimetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mypersonaltimetracker.domain.DayType
import java.time.LocalDate

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inAt: Long,
    val outAt: Long? = null,
    val isEstimated: Boolean = false,
)

@Entity(tableName = "day_exceptions")
data class DayExceptionEntity(
    @PrimaryKey val date: String, // ISO LocalDate
    val type: DayType,
    val note: String? = null,
)

@Entity(tableName = "week_targets")
data class WeekTargetEntity(
    @PrimaryKey val weekStart: String, // ISO LocalDate of Monday
    val targetMinutes: Int,
) {
    constructor(weekStart: LocalDate, targetMinutes: Int) : this(weekStart.toString(), targetMinutes)
}

@Entity(tableName = "day_notes")
data class DayNoteEntity(
    @PrimaryKey val date: String, // ISO LocalDate
    val note: String,
)
