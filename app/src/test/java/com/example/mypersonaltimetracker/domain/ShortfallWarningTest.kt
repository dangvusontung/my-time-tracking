package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShortfallWarningTest {

    // Week of Mon 2026-08-24 .. Sun 2026-08-30
    private val thursday: LocalDate = LocalDate.of(2026, 8, 27)

    private fun progress(remainingMinutes: Int, daysLeft: Double, perDayNeededMinutes: Int) =
        WeekProgress(
            weekStart = WeekProgressCalculator.weekStartOf(thursday),
            officeMinutes = 0,
            lunchCreditMinutes = 0,
            exceptionCreditMinutes = 0,
            weekTotalMinutes = 0,
            targetMinutes = remainingMinutes,
            hasCustomTarget = false,
            remainingMinutes = remainingMinutes,
            daysLeft = daysLeft,
            perDayNeededMinutes = perDayNeededMinutes,
        )

    @Test
    fun `warns from Thursday when per-day needed exceeds 8h`() {
        val p = progress(remainingMinutes = 1800, daysLeft = 2.0, perDayNeededMinutes = 900)
        assertTrue(ShortfallWarning.shouldWarn(thursday, p))
        assertTrue(ShortfallWarning.shouldWarn(thursday.plusDays(1), p)) // Friday
        assertTrue(ShortfallWarning.shouldWarn(thursday.plusDays(3), p)) // Sunday
    }

    @Test
    fun `no warning before Thursday even when behind`() {
        val p = progress(remainingMinutes = 3000, daysLeft = 3.0, perDayNeededMinutes = 1000)
        assertFalse(ShortfallWarning.shouldWarn(LocalDate.of(2026, 8, 24), p)) // Monday
        assertFalse(ShortfallWarning.shouldWarn(LocalDate.of(2026, 8, 26), p)) // Wednesday
    }

    @Test
    fun `no warning when recoverable within 8h per day`() {
        val p = progress(remainingMinutes = 960, daysLeft = 2.0, perDayNeededMinutes = 480)
        assertFalse(ShortfallWarning.shouldWarn(thursday, p))
    }

    @Test
    fun `no warning when week is complete`() {
        val p = progress(remainingMinutes = 0, daysLeft = 2.0, perDayNeededMinutes = 0)
        assertFalse(ShortfallWarning.shouldWarn(thursday, p))
    }

    @Test
    fun `boundary at exactly 8h per day does not warn`() {
        val p = progress(remainingMinutes = 960, daysLeft = 2.0, perDayNeededMinutes = 480)
        assertFalse(ShortfallWarning.shouldWarn(thursday, p))
    }
}
