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
 * One lunch break of [maxDeductMin] minutes can be taken anywhere within the lunch window.
 * Session gaps are already excluded from office time. Choose the lunch interval that covers
 * the most recorded absence, then deduct only the missing minutes, capped by office time
 * actually observed inside the window. An afternoon session may still be running.
 */
object LunchAdjustment {
    private data class Interval(val start: Long, val end: Long)

    fun compute(
        sessions: List<SessionSpan>,
        day: LocalDate,
        lunchStartMin: Int,
        lunchEndMin: Int,
        maxDeductMin: Int,
        now: Instant,
        zone: ZoneId,
        countFromMin: Int = 0,
    ): Int {
        if (maxDeductMin <= 0 || lunchEndMin <= lunchStartMin) return 0
        val daySessions = sessions.filter { it.inAt.atZone(zone).toLocalDate() == day }.sortedBy { it.inAt }
        if (daySessions.isEmpty()) return 0
        val start = day.atTime(LocalTime.ofSecondOfDay(lunchStartMin * 60L)).atZone(zone).toEpochSecond()
        val end = day.atTime(LocalTime.ofSecondOfDay(lunchEndMin * 60L)).atZone(zone).toEpochSecond()
        val duration = minOf(maxDeductMin * 60L, end - start)
        val countFrom = day.atStartOfDay(zone).plusMinutes(countFromMin.toLong()).toEpochSecond()
        val coveredSeconds = daySessions.sumOf { s ->
            (minOf(s.endFor(now).epochSecond, end) - maxOf(s.inAt.epochSecond, start, countFrom)).coerceAtLeast(0)
        }
        if (coveredSeconds == 0L) return 0
        val gaps = daySessions.zipWithNext().mapNotNull { (previous, next) ->
            val gapStart = maxOf(previous.endFor(now).epochSecond, start)
            val gapEnd = minOf(next.inAt.epochSecond, now.epochSecond, end)
            if (gapEnd > gapStart) Interval(gapStart, gapEnd) else null
        }
        // Overlap changes slope only at gap boundaries and those boundaries minus the lunch duration.
        val candidates = listOf(start, end - duration) + gaps.flatMap {
            listOf(it.start, it.end, it.start - duration, it.end - duration)
        }.map { it.coerceIn(start, end - duration) }
        val creditedGapSeconds = candidates.maxOf { candidate ->
            gaps.sumOf { gap -> (minOf(gap.end, candidate + duration) - maxOf(gap.start, candidate)).coerceAtLeast(0) }
        }.coerceAtMost(duration)
        val missingMinutes = duration / 60 - creditedGapSeconds / 60
        return -minOf(coveredSeconds / 60, missingMinutes).toInt()
    }
}
