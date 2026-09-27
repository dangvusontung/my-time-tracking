package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LunchAdjustmentTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day: LocalDate = LocalDate.of(2026, 8, 25) // a Tuesday
    private val endOfDay: Instant = at("23:59")

    private fun at(time: String, d: LocalDate = day): Instant =
        LocalDateTime.parse("${d}T$time").atZone(zone).toInstant()

    private fun span(id: Long, inT: String, outT: String?): SessionSpan =
        SessionSpan(id, at(inT), outT?.let { at(it) })

    // window 11:30 - 14:00, cap 60
    private fun adjustment(
        sessions: List<SessionSpan>,
        start: Int = 690,
        end: Int = 840,
        cap: Int = 60,
        now: Instant = endOfDay,
    ) = LunchAdjustment.compute(sessions, day, start, end, cap, now, zone)

    @Test
    fun `continuous session through lunch deducts the full cap`() {
        assertEquals(-60, adjustment(listOf(span(1, "08:00", "17:00"))))
    }

    @Test
    fun `deduction is limited to the covered part of the window`() {
        // 08:00 -> 12:00 covers 11:30-12:00 = 30 min of the window
        assertEquals(-30, adjustment(listOf(span(1, "08:00", "12:00"))))
    }

    @Test
    fun `session ending before the window deducts nothing`() {
        assertEquals(0, adjustment(listOf(span(1, "08:00", "11:00"))))
    }

    @Test
    fun `gap matching the standard lunch deducts nothing extra`() {
        // actual lunch 12:00-13:00 = 60 min, already excluded by the gap
        val sessions = listOf(span(1, "08:00", "12:00"), span(2, "13:00", "17:00"))
        assertEquals(0, adjustment(sessions))
    }

    @Test
    fun `short gap tops up the deduction to the standard`() {
        // actual lunch 30 min -> deduct the remaining 30
        val sessions = listOf(span(1, "08:00", "12:00"), span(2, "12:30", "17:00"))
        assertEquals(-30, adjustment(sessions))
    }

    @Test
    fun `gap longer than the standard deducts nothing extra`() {
        // actual lunch 11:45-14:00 (135 min): covered 15 min, gap longer -> 0
        val sessions = listOf(span(1, "08:00", "11:45"), span(2, "14:00", "17:00"))
        assertEquals(0, adjustment(sessions))
    }

    @Test
    fun `gap starting at window end is not lunch`() {
        // gap 14:00-15:00 starts at window end -> treat as continuous: -60
        val sessions = listOf(span(1, "08:00", "14:00"), span(2, "15:00", "17:00"))
        assertEquals(-60, adjustment(sessions))
    }

    @Test
    fun `gap before the window is not lunch`() {
        // out 11:00, back 12:00 -> deduction from coverage of 12:00-14:00
        val sessions = listOf(span(1, "08:00", "11:00"), span(2, "12:00", "17:00"))
        assertEquals(-60, adjustment(sessions))
    }

    @Test
    fun `only the first qualifying gap counts`() {
        // gaps 12:00-12:10 (10 min) and 13:00-14:00; coverage = 30+50 = 80 -> standard 60
        val sessions = listOf(
            span(1, "08:00", "12:00"),
            span(2, "12:10", "13:00"),
            span(3, "14:00", "17:00"),
        )
        assertEquals(-50, adjustment(sessions))
    }

    @Test
    fun `running session counts toward coverage but not gap detection`() {
        val sessions = listOf(span(1, "08:00", "12:00"), SessionSpan(2, at("13:00"), null))
        assertEquals(-60, adjustment(sessions, now = at("17:00")))
    }

    @Test
    fun `no sessions deducts nothing`() {
        assertEquals(0, adjustment(emptyList()))
    }

    @Test
    fun `cap is configurable`() {
        assertEquals(-45, adjustment(listOf(span(1, "08:00", "17:00")), cap = 45))
    }

    @Test
    fun `cap zero disables the deduction`() {
        assertEquals(0, adjustment(listOf(span(1, "08:00", "17:00")), cap = 0))
    }

    @Test
    fun `overnight session belongs to its start day`() {
        // Monday 22:00 -> Tuesday 12:00 belongs to Monday; Tuesday only sees 13:00-17:00
        val monday = day.minusDays(1)
        val sessions = listOf(
            SessionSpan(1, at("22:00", monday), at("12:00")),
            span(2, "13:00", "17:00"),
        )
        // Tuesday coverage: 13:00-14:00 = 60 min
        assertEquals(-60, adjustment(sessions))
    }
}
