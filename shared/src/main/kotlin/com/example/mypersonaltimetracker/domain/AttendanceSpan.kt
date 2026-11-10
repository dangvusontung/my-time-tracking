package com.example.mypersonaltimetracker.domain

import java.time.*

/** Raw first-in to last-out span, including outings and lunch, independent of work credit. */
data class AttendanceSpan(val firstIn: Instant, val end: Instant, val running: Boolean) {
    val minutes: Int get() = (Duration.between(firstIn, end).seconds / 60).coerceAtLeast(0).toInt()
    companion object {
        fun compute(sessions: List<SessionSpan>, day: LocalDate, now: Instant, zone: ZoneId): AttendanceSpan? {
            val rows = sessions.filter { it.inAt.atZone(zone).toLocalDate() == day && it.inAt <= now }
            if (rows.isEmpty()) return null
            return AttendanceSpan(rows.minOf { it.inAt }, rows.maxOf { minOf(it.endFor(now), now) }, rows.any { it.outAt == null })
        }
    }
}
