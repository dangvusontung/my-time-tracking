package com.example.mypersonaltimetracker.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AttendanceRulesTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = LocalDate.parse("2026-10-23")
    private fun at(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun row(id: Long, start: String, end: String?) = SessionSpan(id, at(start), end?.let(::at))
    private fun summary(rows: List<SessionSpan>, now: String = "19:00") =
        DaySummaryCalculator.compute(rows, null, day, at(now), 690, 840, 60, zone)

    @Test fun earlyCheckInCountsOnlyAfter0730ButRawSpanKeepsOriginalTime() {
        val rows = listOf(row(1, "06:45", "12:00"), row(2, "13:00", "17:30"))
        assertEquals(540, summary(rows).totalMinutes)
        val span = AttendanceSpan.compute(rows, day, at("19:00"), zone)!!
        assertEquals(at("06:45"), span.firstIn)
        assertEquals(645, span.minutes)
        assertFalse(span.running)
    }

    @Test fun multipleEarlySessionsNeverEarnTimeBefore0730() {
        assertEquals(30, summary(listOf(row(1, "06:00", "07:00"), row(2, "07:15", "08:00"))).totalMinutes)
        assertEquals(0, summary(listOf(row(1, "06:00", null)), "07:29").totalMinutes)
        assertEquals(0, summary(listOf(row(1, "06:00", null)), "07:30").totalMinutes)
        assertEquals(1, summary(listOf(row(1, "06:00", null)), "07:31").totalMinutes)
    }

    @Test fun earlyUncreditedSessionStillProvidesEvidenceOfLunchOuting() {
        val rows = listOf(row(1, "06:00", "07:00"), row(2, "13:00", "17:00"))
        assertEquals(240, summary(rows).totalMinutes)
    }

    @Test fun rawSpanIncludesAllOutingsAndRunningEndsAtNow() {
        val rows = listOf(row(1, "08:00", "10:00"), row(2, "13:30", null))
        val span = AttendanceSpan.compute(rows, day, at("17:00"), zone)!!
        assertEquals(540, span.minutes)
        assertTrue(span.running)
        assertNull(AttendanceSpan.compute(emptyList(), day, at("17:00"), zone))
    }

    @Test fun monthlyAverageSeparatesCreditedTimeAndRawSpan() {
        val previous = day.minusDays(1)
        val totals = mapOf(previous to DaySummary(480, 0, 0), day to DaySummary(540, 0, 0), day.minusDays(2) to DaySummary(0, 0, 0))
        val spans = mapOf(previous to AttendanceSpan(at("08:00"), at("18:00"), false),
            day to AttendanceSpan(at("07:00"), at("18:00"), false))
        val month = MonthTotalsCalculator.aggregate(totals, 12, spans).single()
        assertEquals(510, month.avgPerDayMinutes)
        assertEquals(630, month.avgSpanMinutes)
        assertEquals(2, month.daysWithData)
    }

    @Test fun fridayProjectionUses0730EvenWhenCheckedInAt0600() {
        assertEquals(at("18:30"), FridayLeaveCalculator.compute(listOf(row(1, "06:00", null)), null,
            day, at("07:00"), 1800, 690, 840, 60, zone))
    }
}
