package com.example.mypersonaltimetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Describes the observed lunch outing, independently of the accounting adjustment. */
data class LunchBreakStatus(val allowedMinutes: Int = 60, val recordedMinutes: Int? = null) {
    val extraMinutes: Int get() = ((recordedMinutes ?: 0) - allowedMinutes).coerceAtLeast(0)

    companion object {
        fun compute(
            sessions: List<SessionSpan>, day: LocalDate, now: Instant, zone: ZoneId,
            startMinute: Int, endMinute: Int, allowedMinutes: Int,
        ): LunchBreakStatus {
            val start = day.atStartOfDay(zone).plusMinutes(startMinute.toLong()).toInstant()
            val end = day.atStartOfDay(zone).plusMinutes(endMinute.toLong()).toInstant()
            val gaps = sessions.filter { it.inAt.atZone(zone).toLocalDate() == day }
                .sortedBy { it.inAt }.zipWithNext().mapNotNull { (previous, next) ->
                    val out = previous.outAt ?: return@mapNotNull null
                    val back = minOf(next.inAt, now)
                    if (out < end && back > start && back > out) (back.epochSecond - out.epochSecond) / 60 else null
                }
            return LunchBreakStatus(allowedMinutes, gaps.maxOrNull()?.toInt())
        }
    }
}
