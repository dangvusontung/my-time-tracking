package com.example.mypersonaltimetracker.domain

import java.time.*

/** Friday's 40-hour estimate assumes no more outings and never precedes 16:30. */
object FridayLeaveCalculator {
    fun compute(sessions: List<SessionSpan>, exception: DayType?, day: LocalDate, now: Instant,
                priorWeekMinutes: Int, lunchStart: Int, lunchEnd: Int, lunchDeduct: Int, zone: ZoneId): Instant? {
        if (day.dayOfWeek != DayOfWeek.FRIDAY || now.atZone(zone).toLocalDate() != day) return null
        val floor = maxOf(now, day.atTime(16, 30).atZone(zone).toInstant())
        fun ceilingMinute(at: Instant): Instant {
            val minute = at.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
            return if (minute < at) minute.plusSeconds(60) else minute
        }
        val today = DaySummaryCalculator.compute(sessions, exception, day, now, lunchStart, lunchEnd, lunchDeduct, zone)
        if (priorWeekMinutes + today.totalMinutes >= 2400) return ceilingMinute(floor)
        val reached = EarliestLeaveCalculator.compute(sessions, exception, day, now, lunchStart, lunchEnd,
            lunchDeduct, zone, (2400 - priorWeekMinutes).coerceAtLeast(0)) ?: return null
        return ceilingMinute(maxOf(floor, reached))
    }
}
