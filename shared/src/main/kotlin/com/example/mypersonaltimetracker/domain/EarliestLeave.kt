package com.example.mypersonaltimetracker.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Earliest instant the day's total reaches the "luật rừng" 8h30
 * ([JUNGLE_LAW_DAILY_MINUTES]), assuming the running session keeps going.
 *
 * Returns null when no session is running (nothing to "leave" from), or when the law
 * cannot be reached. Returns [now] when the law is already satisfied.
 */
object EarliestLeaveCalculator {

    fun compute(
        sessions: List<SessionSpan>,
        exceptionType: DayType?,
        day: LocalDate,
        now: Instant,
        lunchStartMin: Int,
        lunchEndMin: Int,
        lunchMaxDeductMin: Int,
        zone: ZoneId,
        targetMinutes: Int = JUNGLE_LAW_DAILY_MINUTES,
    ): Instant? {
        val running = sessions.any { it.outAt == null && it.inAt.atZone(zone).toLocalDate() == day }
        if (!running) return null

        // Evaluate as if the user checked out at [at]: closing the running session lets the
        // lunch-gap rule see the real break, matching the summary after an actual checkout.
        fun total(at: Instant): Int {
            val closed = sessions.map { if (it.outAt == null) it.copy(outAt = at) else it }
            return DaySummaryCalculator.compute(
                closed, exceptionType, day, at,
                lunchStartMin, lunchEndMin, lunchMaxDeductMin, zone,
            ).totalMinutes
        }

        if (total(now) >= targetMinutes) return now

        // Fixed-point iteration: candidate advances by the remaining minutes. The lunch
        // deduction is the only nonlinearity (bounded by lunchMaxDeductMin), so a few
        // iterations converge.
        var candidate = now
        repeat(5) {
            val remaining = targetMinutes - total(candidate)
            if (remaining <= 0) return candidate
            candidate = candidate.plus(Duration.ofMinutes(remaining.toLong()))
        }
        return if (total(candidate) >= targetMinutes) candidate else null
    }
}
