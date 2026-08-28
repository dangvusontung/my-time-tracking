package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekProgressTest {

    // Week of Mon 2026-08-24 .. Sun 2026-08-30
    private val monday: LocalDate = LocalDate.of(2026, 8, 24)
    private val friday: LocalDate = LocalDate.of(2026, 8, 28)
    private val monFri: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    private fun summary(total: Int, office: Int = total, lunch: Int = 0, exception: Int = 0) =
        DaySummary(officeMinutes = office, lunchCreditMinutes = lunch, exceptionCreditMinutes = exception)
            .also { require(it.totalMinutes == total) }

    private fun progress(
        today: LocalDate,
        summaries: Map<LocalDate, DaySummary> = emptyMap(),
        exceptions: Map<LocalDate, DayType> = emptyMap(),
        customTarget: Int? = null,
        defaultTarget: Int = 2400,
        workdays: Set<DayOfWeek> = monFri,
    ) = WeekProgressCalculator.compute(
        today, WeekProgressCalculator.weekStartOf(today), summaries, exceptions, customTarget, defaultTarget, workdays,
    )

    @Test
    fun `week start is monday for any day of week`() {
        assertEquals(monday, WeekProgressCalculator.weekStartOf(monday))
        assertEquals(monday, WeekProgressCalculator.weekStartOf(LocalDate.of(2026, 8, 30))) // Sunday
        assertEquals(LocalDate.of(2026, 8, 31), WeekProgressCalculator.weekStartOf(LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun `totals split into office lunch exception`() {
        val summaries = mapOf(
            monday to summary(total = 540, office = 480, lunch = 60),
            monday.plusDays(1) to summary(total = 480, office = 0, exception = 480),
        )
        val p = progress(today = monday.plusDays(2), summaries = summaries)
        assertEquals(480, p.officeMinutes)
        assertEquals(60, p.lunchCreditMinutes)
        assertEquals(480, p.exceptionCreditMinutes)
        assertEquals(1020, p.weekTotalMinutes)
    }

    @Test
    fun `default target used when no custom target`() {
        val p = progress(today = friday)
        assertEquals(2400, p.targetMinutes)
        assertFalse(p.hasCustomTarget)
    }

    @Test
    fun `custom target overrides default`() {
        val p = progress(today = friday, customTarget = 1800)
        assertEquals(1800, p.targetMinutes)
        assertTrue(p.hasCustomTarget)
    }

    @Test
    fun `exception credit adds to total but never reduces target`() {
        val summaries = mapOf(monday to summary(total = 480, office = 0, exception = 480))
        val exceptions = mapOf(monday to DayType.PUBLIC_HOLIDAY)
        val p = progress(today = monday.plusDays(1), summaries = summaries, exceptions = exceptions, customTarget = 2400)
        assertEquals(480, p.weekTotalMinutes)
        assertEquals(2400, p.targetMinutes)
        assertEquals(1920, p.remainingMinutes)
    }

    @Test
    fun `remaining floors at zero when over target`() {
        val summaries = mapOf(monday to summary(total = 3000, office = 3000))
        val p = progress(today = monday.plusDays(1), summaries = summaries)
        assertEquals(0, p.remainingMinutes)
        assertEquals(0, p.perDayNeededMinutes)
    }

    @Test
    fun `daysLeft counts workdays from today through sunday inclusive of today`() {
        // today Wednesday: Wed, Thu, Fri = 3
        val p = progress(today = monday.plusDays(2))
        assertEquals(3.0, p.daysLeft, 0.0)
        // today Monday: 5
        assertEquals(5.0, progress(today = monday).daysLeft, 0.0)
        // today Friday: 1
        assertEquals(1.0, progress(today = friday).daysLeft, 0.0)
    }

    @Test
    fun `daysLeft on weekend is zero and perDayNeeded is zero`() {
        val p = progress(today = LocalDate.of(2026, 8, 29)) // Saturday
        assertEquals(0.0, p.daysLeft, 0.0)
        assertEquals(0, p.perDayNeededMinutes)
        assertEquals(monday, p.weekStart)
    }

    @Test
    fun `full day exceptions subtract from daysLeft`() {
        val exceptions = mapOf(
            monday.plusDays(2) to DayType.ANNUAL_LEAVE,
            monday.plusDays(3) to DayType.WFH,
        )
        // today Monday: 5 - 2 = 3
        val p = progress(today = monday, exceptions = exceptions)
        assertEquals(3.0, p.daysLeft, 0.0)
    }

    @Test
    fun `half day leave subtracts half`() {
        val exceptions = mapOf(
            monday.plusDays(1) to DayType.HALF_DAY_LEAVE,
            monday.plusDays(2) to DayType.UNPAID_LEAVE,
        )
        // today Monday: 5 - 0.5 - 1 = 3.5
        val p = progress(today = monday, exceptions = exceptions)
        assertEquals(3.5, p.daysLeft, 0.0)
    }

    @Test
    fun `exceptions before today do not affect daysLeft`() {
        val exceptions = mapOf(monday to DayType.PUBLIC_HOLIDAY)
        // today Tuesday: Tue..Fri = 4, monday exception ignored
        val p = progress(today = monday.plusDays(1), exceptions = exceptions)
        assertEquals(4.0, p.daysLeft, 0.0)
    }

    @Test
    fun `perDayNeeded rounds up`() {
        // remaining 1000, 3 days left -> 334
        val summaries = mapOf(monday to summary(total = 1400, office = 1400))
        val p = progress(today = monday.plusDays(2), summaries = summaries)
        assertEquals(1000, p.remainingMinutes)
        assertEquals(3.0, p.daysLeft, 0.0)
        assertEquals(334, p.perDayNeededMinutes)
    }

    @Test
    fun `daysLeft floors at zero with many exceptions`() {
        val exceptions = (0..4).associate { monday.plusDays(it.toLong()) to DayType.ANNUAL_LEAVE }
        val p = progress(today = monday.plusDays(2), exceptions = exceptions)
        assertEquals(0.0, p.daysLeft, 0.0)
        assertEquals(0, p.perDayNeededMinutes)
    }

    @Test
    fun `previous week data not counted after monday reset`() {
        val lastWeekFriday = monday.minusDays(3)
        val summaries = mapOf(lastWeekFriday to summary(total = 600, office = 600))
        val p = progress(today = monday, summaries = summaries) // summaries keyed outside week range
        assertEquals(0, p.weekTotalMinutes)
    }

    @Test
    fun `custom workdays include saturday`() {
        val monSat = monFri + DayOfWeek.SATURDAY
        // today Wednesday: Wed, Thu, Fri, Sat = 4
        val p = progress(today = monday.plusDays(2), workdays = monSat)
        assertEquals(4.0, p.daysLeft, 0.0)
        // today Saturday: Sat = 1
        assertEquals(1.0, progress(today = monday.plusDays(5), workdays = monSat).daysLeft, 0.0)
    }

    @Test
    fun `custom workdays can exclude weekdays`() {
        val tueThu = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        // today Monday: Tue, Thu = 2
        val p = progress(today = monday, workdays = tueThu)
        assertEquals(2.0, p.daysLeft, 0.0)
        assertEquals(1200, p.perDayNeededMinutes) // 2400 / 2
    }

    @Test
    fun `empty workdays means no days left`() {
        val p = progress(today = monday, workdays = emptySet())
        assertEquals(0.0, p.daysLeft, 0.0)
        assertEquals(0, p.perDayNeededMinutes)
    }

    @Test
    fun `past week viewed from its sunday has zero days left`() {
        val sunday = monday.plusDays(6)
        val p = progress(today = sunday)
        assertEquals(0.0, p.daysLeft, 0.0)
        assertEquals(0, p.perDayNeededMinutes)
    }
}
