package com.example.mypersonaltimetracker.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LunchBreakStatusTest {
    private val day = LocalDate.of(2026, 10, 22)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun lunch(back: String) = LunchBreakStatus.compute(listOf(
        SessionSpan(1, at("08:00"), at("12:00")), SessionSpan(2, at(back), null)
    ), day, at("18:00"), zone, 690, 840, 60)

    @Test fun shortLunchIsWithinAllowance() {
        assertEquals(30, lunch("12:30").recordedMinutes)
        assertEquals(0, lunch("12:30").extraMinutes)
    }
    @Test fun oneHourLunchNeedsNoExtraTime() {
        assertEquals(60, lunch("13:00").recordedMinutes)
        assertEquals(0, lunch("13:00").extraMinutes)
    }
    @Test fun longLunchReportsExtraTimeEvenWithRunningAfternoon() {
        assertEquals(90, lunch("13:30").recordedMinutes)
        assertEquals(30, lunch("13:30").extraMinutes)
    }
    @Test fun noObservedLunchDoesNotInventADuration() {
        assertNull(LunchBreakStatus.compute(listOf(SessionSpan(1, at("08:00"), null)),
            day, at("18:00"), zone, 690, 840, 60).recordedMinutes)
    }
    @Test fun morningOutingIsNotLunch() {
        assertNull(LunchBreakStatus.compute(listOf(SessionSpan(1, at("08:00"), at("10:00")),
            SessionSpan(2, at("10:30"), at("18:00"))), day, at("18:00"), zone, 690, 840, 60).recordedMinutes)
    }
}
