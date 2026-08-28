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
    val lunchCreditMinutes: Int,
    val exceptionCreditMinutes: Int,
) {
    val totalMinutes: Int get() = officeMinutes + lunchCreditMinutes + exceptionCreditMinutes
}

object DaySummaryCalculator {

    /**
     * officeMinutes = sum of (in -> out) of the day's sessions; a running session counts
     * up to [now] for display. Overnight sessions belong to the local date of inAt.
     */
    fun compute(
        sessions: List<SessionSpan>,
        exceptionType: DayType?,
        day: LocalDate,
        now: Instant,
        lunchStartMin: Int,
        lunchEndMin: Int,
        lunchMaxCreditMin: Int,
        zone: ZoneId,
    ): DaySummary {
        val daySessions = sessions.filter { it.inAt.atZone(zone).toLocalDate() == day }
        val officeSeconds = daySessions.sumOf { s ->
            val end = s.endFor(now)
            if (end.isAfter(s.inAt)) end.epochSecond - s.inAt.epochSecond else 0L
        }
        val officeMinutes = (officeSeconds / 60).toInt()
        val lunch = LunchCredit.compute(sessions, day, lunchStartMin, lunchEndMin, lunchMaxCreditMin, zone)
        return DaySummary(
            officeMinutes = officeMinutes,
            lunchCreditMinutes = lunch,
            exceptionCreditMinutes = exceptionType?.creditMinutes ?: 0,
        )
    }
}
