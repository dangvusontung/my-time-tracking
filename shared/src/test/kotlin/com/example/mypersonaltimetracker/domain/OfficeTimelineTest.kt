package com.example.mypersonaltimetracker.domain

import com.example.mypersonaltimetracker.companion.WatchSession
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class OfficeTimelineTest {
    private val day = LocalDate.parse("2026-10-22")
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun session(start: String, end: String?) = WatchSession(at(start).toEpochMilli(), end?.let { at(it).toEpochMilli() })

    @Test fun distinguishesLunchFromOtherOutingsAndPreservesExcessLunch() {
        val result = officeTimeline(listOf(session("08:00", "10:00"), session("10:15", "12:00"), session("13:30", "18:00")), day, at("19:00").plusSeconds(86400), zone, 690, 840)
        assertEquals(listOf(OfficeKind.OFFICE, OfficeKind.OUTSIDE, OfficeKind.OFFICE, OfficeKind.LUNCH, OfficeKind.OFFICE), result.map { it.kind })
        assertEquals(90f, result.single { it.kind == OfficeKind.LUNCH }.let { it.end - it.start })
        assertEquals(1080f, result.last().end)
    }

    @Test fun runningSessionStopsAtNowAndDoesNotInventLunch() {
        val result = officeTimeline(listOf(session("06:30", null)), day, at("14:30"), zone, 690, 840)
        assertEquals(listOf(OfficeSegment(390f, 870f, OfficeKind.OFFICE)), result)
    }

    @Test fun currentOutingIsObservedOnlyUntilNow() {
        val result = officeTimeline(listOf(session("08:00", "12:00")), day, at("12:30"), zone, 690, 840)
        assertEquals(OfficeSegment(720f, 750f, OfficeKind.LUNCH), result.last())
    }
}
