package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthTotalsTest {

    private fun summary(total: Int) =
        DaySummary(officeMinutes = total, lunchCreditMinutes = 0, exceptionCreditMinutes = 0)

    @Test
    fun `groups and sums per month`() {
        val summaries = mapOf(
            LocalDate.of(2026, 8, 3) to summary(480),
            LocalDate.of(2026, 8, 4) to summary(540),
            LocalDate.of(2026, 7, 31) to summary(300),
        )
        val months = MonthTotalsCalculator.aggregate(summaries, maxMonths = 12)
        assertEquals(2, months.size)
        assertEquals(YearMonth.of(2026, 8), months[0].yearMonth)
        assertEquals(1020, months[0].totalMinutes)
        assertEquals(2, months[0].daysWithData)
        assertEquals(YearMonth.of(2026, 7), months[1].yearMonth)
        assertEquals(300, months[1].totalMinutes)
        assertEquals(1, months[1].daysWithData)
    }

    @Test
    fun `days without data do not count`() {
        val summaries = mapOf(
            LocalDate.of(2026, 8, 3) to summary(480),
            LocalDate.of(2026, 8, 4) to summary(0),
        )
        val months = MonthTotalsCalculator.aggregate(summaries, maxMonths = 12)
        assertEquals(1, months.size)
        assertEquals(1, months[0].daysWithData)
        assertEquals(480, months[0].totalMinutes)
    }

    @Test
    fun `totals include lunch and exception credits`() {
        val summaries = mapOf(
            LocalDate.of(2026, 8, 3) to DaySummary(officeMinutes = 420, lunchCreditMinutes = 60, exceptionCreditMinutes = 0),
            LocalDate.of(2026, 8, 4) to DaySummary(officeMinutes = 0, lunchCreditMinutes = 0, exceptionCreditMinutes = 480),
        )
        val months = MonthTotalsCalculator.aggregate(summaries, maxMonths = 12)
        assertEquals(960, months[0].totalMinutes)
        assertEquals(2, months[0].daysWithData)
    }

    @Test
    fun `sorted newest first and limited to maxMonths`() {
        val summaries = (1..14).associate { i ->
            // 14 months back, one day each
            val d = LocalDate.of(2026, 8, 15).minusMonths(i.toLong())
            d to summary(60)
        }
        val months = MonthTotalsCalculator.aggregate(summaries, maxMonths = 12)
        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2026, 7), months.first().yearMonth)
        assertTrue(months.zipWithNext().all { (a, b) -> a.yearMonth > b.yearMonth })
    }

    @Test
    fun `empty input gives empty output`() {
        assertTrue(MonthTotalsCalculator.aggregate(emptyMap(), maxMonths = 12).isEmpty())
    }
}
