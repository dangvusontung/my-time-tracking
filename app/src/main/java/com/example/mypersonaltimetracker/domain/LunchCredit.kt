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
 * Strict lunch-credit rule:
 * find the FIRST gap between consecutive completed sessions of the same local day whose
 * gap-start (= outAt of the earlier session, in local time) falls within
 * [lunchStartMin, lunchEndMin). Credit = gap length clipped to the window, capped at maxCredit.
 * A gap whose start is outside the window earns nothing, even if it overlaps the window.
 * Only one gap per day earns credit.
 */
object LunchCredit {

    fun compute(
        sessions: List<SessionSpan>,
        day: LocalDate,
        lunchStartMin: Int,
        lunchEndMin: Int,
        maxCreditMin: Int,
        zone: ZoneId,
    ): Int {
        val completed = sessions
            .filter { it.outAt != null && it.inAt.atZone(zone).toLocalDate() == day }
            .sortedBy { it.inAt }
        val windowStart = day.atTime(LocalTime.ofSecondOfDay(lunchStartMin * 60L)).atZone(zone).toInstant()
        val windowEnd = day.atTime(LocalTime.ofSecondOfDay(lunchEndMin * 60L)).atZone(zone).toInstant()

        for (i in 0 until completed.size - 1) {
            val gapStart = completed[i].outAt!!
            val gapEnd = completed[i + 1].inAt
            if (!gapEnd.isAfter(gapStart)) continue // no real gap
            val gapStartZoned = gapStart.atZone(zone)
            if (gapStartZoned.toLocalDate() != day) continue
            val gapStartMin = gapStartZoned.toLocalTime().toSecondOfDay() / 60
            if (gapStartMin < lunchStartMin || gapStartMin >= lunchEndMin) continue

            val clippedStart = maxOf(gapStart, windowStart)
            val clippedEnd = minOf(gapEnd, windowEnd)
            if (!clippedEnd.isAfter(clippedStart)) return 0
            val gapMinutes = (clippedEnd.epochSecond - clippedStart.epochSecond) / 60
            return minOf(gapMinutes.toInt(), maxCreditMin)
        }
        return 0
    }
}
