package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LeaveQuotaTest {

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    @Test
    fun `annual leave counts 1 day and half day counts half`() {
        val used = LeaveQuotaCalculator.usedDays(
            mapOf(
                date(2026, 1, 5) to DayType.ANNUAL_LEAVE,
                date(2026, 3, 10) to DayType.ANNUAL_LEAVE,
                date(2026, 5, 20) to DayType.HALF_DAY_LEAVE,
            ),
            year = 2026,
        )
        assertEquals(2.5, used, 0.0001)
    }

    @Test
    fun `WFH holiday and unpaid leave do not consume quota`() {
        val used = LeaveQuotaCalculator.usedDays(
            mapOf(
                date(2026, 1, 1) to DayType.PUBLIC_HOLIDAY,
                date(2026, 2, 2) to DayType.WFH,
                date(2026, 3, 3) to DayType.UNPAID_LEAVE,
                date(2026, 4, 4) to DayType.ANNUAL_LEAVE,
            ),
            year = 2026,
        )
        assertEquals(1.0, used, 0.0001)
    }

    @Test
    fun `only exceptions of the given year count`() {
        val used = LeaveQuotaCalculator.usedDays(
            mapOf(
                date(2025, 12, 31) to DayType.ANNUAL_LEAVE,
                date(2026, 1, 2) to DayType.HALF_DAY_LEAVE,
                date(2027, 1, 1) to DayType.ANNUAL_LEAVE,
            ),
            year = 2026,
        )
        assertEquals(0.5, used, 0.0001)
    }

    @Test
    fun `empty input gives zero`() {
        assertEquals(0.0, LeaveQuotaCalculator.usedDays(emptyMap(), 2026), 0.0001)
    }
}
