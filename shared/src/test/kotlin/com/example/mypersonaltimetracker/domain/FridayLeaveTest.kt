package com.example.mypersonaltimetracker.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class FridayLeaveTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val friday = LocalDate.parse("2026-10-23")
    private fun at(time: String) = friday.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun estimate(prior: Int, now: String = "09:00", sessions: List<SessionSpan> = listOf(SessionSpan(1, at("08:30"), null))) =
        FridayLeaveCalculator.compute(sessions, null, friday, at(now), prior, 690, 840, 60, zone)

    @Test fun neverBeforeFriday1630EvenWhen40HoursReachedEarlier() {
        assertEquals(at("16:30"), estimate(2027))
        assertEquals(at("16:30"), estimate(2400))
    }

    @Test fun projectsBeyond1630WhenWeekStillShortIncludingLunch() {
        assertEquals(at("19:30"), estimate(1800))
    }

    @Test fun excessLunchAndOtherOutingsDelayLeave() {
        val sessions = listOf(SessionSpan(1, at("08:30"), at("12:00")), SessionSpan(2, at("13:30"), null))
        assertEquals(at("20:00"), estimate(1800, "14:00", sessions))
    }

    @Test fun noEstimateWhileOutsideAndWeekUnfinished() {
        assertNull(estimate(1800, sessions = listOf(SessionSpan(1, at("08:30"), at("08:45")))))
    }

    @Test fun after1630NeverReturnsPastTime() {
        assertEquals(at("18:00"), estimate(2400, "18:00"))
    }

    @Test fun notShownOnOtherDays() {
        val thursday = friday.minusDays(1)
        assertNull(FridayLeaveCalculator.compute(emptyList(), null, thursday, thursday.atTime(9, 0).atZone(zone).toInstant(),
            2400, 690, 840, 60, zone))
    }
}
