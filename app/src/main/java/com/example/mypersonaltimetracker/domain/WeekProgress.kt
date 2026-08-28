package com.example.mypersonaltimetracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

data class WeekProgress(
    val weekStart: LocalDate,
    val officeMinutes: Int,
    val lunchCreditMinutes: Int,
    val exceptionCreditMinutes: Int,
    val weekTotalMinutes: Int,
    val targetMinutes: Int,
    val hasCustomTarget: Boolean,
    val remainingMinutes: Int,
    /** Can be fractional because a half-day leave subtracts 0.5. */
    val daysLeft: Double,
    val perDayNeededMinutes: Int,
)

object WeekProgressCalculator {

    fun weekStartOf(day: LocalDate): LocalDate =
        day.with(DayOfWeek.MONDAY)

    /**
     * @param today the local "today"
     * @param weekStart Monday of today's week
     * @param daySummaries day summaries Mon..Sun of this week (missing days = no data, treated as 0)
     * @param exceptionTypes exceptions by date in this week
     * @param customTargetMinutes WeekTargetEntity value for this week, or null
     * @param defaultTargetMinutes settings default
     * @param workdays configured working days of the week
     */
    fun compute(
        today: LocalDate,
        weekStart: LocalDate,
        daySummaries: Map<LocalDate, DaySummary>,
        exceptionTypes: Map<LocalDate, DayType>,
        customTargetMinutes: Int?,
        defaultTargetMinutes: Int,
        workdays: Set<DayOfWeek>,
    ): WeekProgress {
        var office = 0
        var lunch = 0
        var exception = 0
        for (i in 0..6) {
            val d = weekStart.plusDays(i.toLong())
            val s = daySummaries[d] ?: continue
            office += s.officeMinutes
            lunch += s.lunchCreditMinutes
            exception += s.exceptionCreditMinutes
        }
        val weekTotal = office + lunch + exception
        val target = customTargetMinutes ?: defaultTargetMinutes
        val remaining = max(0, target - weekTotal)

        // Working days (from the configured set) from today through Sunday, inclusive of today.
        var daysLeft = 0.0
        for (i in 0..6) {
            val d = weekStart.plusDays(i.toLong())
            if (d.isBefore(today)) continue
            if (d.dayOfWeek !in workdays) continue
            val type = exceptionTypes[d]
            daysLeft += 1.0
            when {
                type == DayType.HALF_DAY_LEAVE -> daysLeft -= 0.5
                type != null && type.creditMinutes >= 480 -> daysLeft -= 1.0
            }
        }
        if (daysLeft < 0.0) daysLeft = 0.0

        val perDayNeeded = if (daysLeft > 0.0) ceil(remaining / daysLeft).toInt() else 0

        return WeekProgress(
            weekStart = weekStart,
            officeMinutes = office,
            lunchCreditMinutes = lunch,
            exceptionCreditMinutes = exception,
            weekTotalMinutes = weekTotal,
            targetMinutes = target,
            hasCustomTarget = customTargetMinutes != null,
            remainingMinutes = remaining,
            daysLeft = daysLeft,
            perDayNeededMinutes = perDayNeeded,
        )
    }
}
