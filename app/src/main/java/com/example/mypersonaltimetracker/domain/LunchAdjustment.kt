package com.example.mypersonaltimetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A session reduced to what the domain rules need. [outAt] == null means running. */
data class SessionSpan(
    val id: Long,
    val inAt: Instant,
    val outAt: Instant?,
) {
    fun endFor(now: Instant): Instant = outAt ?: now
}

/**
 * Lunch adjustment rule — a DEDUCTION (returned as minutes <= 0), because lunch
 * time is not counted as work time:
 *
 * - If the day has a real gap between consecutive completed sessions whose gap-start
 *   falls inside [lunchStartMin, lunchEndMin), the gap is the actual lunch break and is
 *   already excluded from the office span. We still top up the deduction to the standard
 *   [maxDeductMin] when the gap was SHORTER than the standard (e.g. a 30-minute lunch on
 *   a full day still deducts the remaining 30). A gap longer than the standard deducts
 *   nothing extra — the gap already removed the whole break.
 * - If there is no qualifying gap (one continuous session through lunch), deduct
 *   [maxDeductMin], limited to how much of the window the day's sessions actually cover.
 *
 * Only the first qualifying gap counts. Running sessions count toward window coverage
 * (up to [now]) but are excluded from gap detection.
 */
object LunchAdjustment {

    fun compute(
        sessions: List<SessionSpan>,
        day: LocalDate,
        lunchStartMin: Int,
        lunchEndMin: Int,
        maxDeductMin: Int,
        now: Instant,
        zone: ZoneId,
    ): Int {
        val daySessions = sessions
            .filter { it.inAt.atZone(zone).toLocalDate() == day }
            .sortedBy { it.inAt }
        if (daySessions.isEmpty() || maxDeductMin <= 0) return 0

        val windowStart = day.atTime(LocalTime.ofSecondOfDay(lunchStartMin * 60L)).atZone(zone).toInstant()
        val windowEnd = day.atTime(LocalTime.ofSecondOfDay(lunchEndMin * 60L)).atZone(zone).toInstant()

        // Minutes of the lunch window covered by sessions (the part one could have lunch in).
        var coveredSec = 0L
        for (s in daySessions) {
            val cs = maxOf(s.inAt, windowStart)
            val ce = minOf(s.endFor(now), windowEnd)
            if (ce.isAfter(cs)) coveredSec += ce.epochSecond - cs.epochSecond
        }
        val standard = minOf((coveredSec / 60).toInt(), maxDeductMin)
        if (standard <= 0) return 0

        // First gap starting inside the window = actual lunch break.
        val completed = daySessions.filter { it.outAt != null }
        for (i in 0 until completed.size - 1) {
            val gapStart = completed[i].outAt!!
            val gapEnd = completed[i + 1].inAt
            if (!gapEnd.isAfter(gapStart)) continue // no real gap
            val gapStartZoned = gapStart.atZone(zone)
            if (gapStartZoned.toLocalDate() != day) continue
            val gapStartMin = gapStartZoned.toLocalTime().toSecondOfDay() / 60
            if (gapStartMin < lunchStartMin || gapStartMin >= lunchEndMin) continue

            val gapMinutes = ((gapEnd.epochSecond - gapStart.epochSecond) / 60).toInt()
            return -maxOf(0, standard - gapMinutes)
        }
        return -standard
    }
}
