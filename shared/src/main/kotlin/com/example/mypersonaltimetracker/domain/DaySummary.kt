package com.example.mypersonaltimetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Leave / exception types credited to a day. Kept distinct even where credits are equal
 *  (future leave-quota counting depends on it). */
enum class DayType(val creditMinutes: Int) {
    ANNUAL_LEAVE(480),
    HALF_DAY_LEAVE(240),
    PUBLIC_HOLIDAY(480),
    WFH(480),
    UNPAID_LEAVE(480),
}

data class DaySummary(
    val officeMinutes: Int,
    /** Lunch adjustment in minutes — NEGATIVE (lunch is deducted from work time). */
    val lunchAdjustmentMinutes: Int,
    val exceptionCreditMinutes: Int,
) {
    val totalMinutes: Int get() = officeMinutes + lunchAdjustmentMinutes + exceptionCreditMinutes
}

object DaySummaryCalculator {

    /**
     * officeMinutes = sum of (in -> out) of the day's sessions; a running session counts
     * up to [now] for display. Overnight sessions belong to the local date of inAt.
     * Time starts counting at 07:30; there is no evening cutoff. Gaps are excluded.
     * lunchAdjustmentMinutes deducts the (standard or actual) lunch break — see
     * [LunchAdjustment].
     */
    fun compute(
        sessions: List<SessionSpan>,
        exceptionType: DayType?,
        day: LocalDate,
        now: Instant,
        lunchStartMin: Int,
        lunchEndMin: Int,
        lunchMaxDeductMin: Int,
        zone: ZoneId,
    ): DaySummary {
        val daySessions = sessions.filter { it.inAt.atZone(zone).toLocalDate() == day }
        val countFrom = day.atTime(7, 30).atZone(zone).toInstant()
        val officeSeconds = daySessions.sumOf { s ->
            val end = s.endFor(now)
            val start = maxOf(s.inAt, countFrom)
            if (end.isAfter(start)) end.epochSecond - start.epochSecond else 0L
        }
        val officeMinutes = (officeSeconds / 60).toInt()
        val lunch = LunchAdjustment.compute(sessions, day, lunchStartMin, lunchEndMin, lunchMaxDeductMin, now, zone, countFromMin = 450)
        return DaySummary(
            officeMinutes = officeMinutes,
            lunchAdjustmentMinutes = lunch,
            exceptionCreditMinutes = exceptionType?.creditMinutes ?: 0,
        )
    }
}
