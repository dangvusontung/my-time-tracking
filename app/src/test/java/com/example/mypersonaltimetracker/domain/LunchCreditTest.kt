package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LunchCreditTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day: LocalDate = LocalDate.of(2026, 8, 25) // a Tuesday

    private fun at(time: String, d: LocalDate = day): Instant =
        LocalDateTime.parse("${d}T$time").atZone(zone).toInstant()

    private fun span(id: Long, inT: String, outT: String?): SessionSpan =
        SessionSpan(id, at(inT), outT?.let { at(it) })

    // window 11:30 - 14:00, cap 60
    private fun credit(sessions: List<SessionSpan>, start: Int = 690, end: Int = 840, cap: Int = 60) =
        LunchCredit.compute(sessions, day, start, end, cap, zone)

    @Test
    fun `gap fully inside window earns full gap length`() {
        val sessions = listOf(span(1, "08:00", "12:00"), span(2, "13:00", "17:00"))
        assertEquals(60, credit(sessions)) // 60 min gap, capped at 60
    }

    @Test
    fun `short gap inside window earns gap length below cap`() {
        val sessions = listOf(span(1, "08:00", "12:00"), span(2, "12:30", "17:00"))
        assertEquals(30, credit(sessions))
    }

    @Test
    fun `gap clipped to window end`() {
        // gap 13:30 -> 14:30 starts inside window, clipped to 13:30-14:00 = 30 min
        val sessions = listOf(span(1, "08:00", "13:30"), span(2, "14:30", "17:00"))
        assertEquals(30, credit(sessions))
    }

    @Test
    fun `gap starting exactly at window start earns credit`() {
        // 11:30 -> 14:00 = 150 min, capped at 60
        val sessions = listOf(span(1, "08:00", "11:30"), span(2, "14:00", "17:00"))
        assertEquals(60, credit(sessions))
    }

    @Test
    fun `gap starting at window end earns nothing`() {
        val sessions = listOf(span(1, "08:00", "14:00"), span(2, "15:00", "17:00"))
        assertEquals(0, credit(sessions))
    }

    @Test
    fun `gap before window earns nothing even if it overlaps window`() {
        // spec example: out 11:00, back 12:00 -> 0
        val sessions = listOf(span(1, "08:00", "11:00"), span(2, "12:00", "17:00"))
        assertEquals(0, credit(sessions))
    }

    @Test
    fun `only the first qualifying gap earns credit`() {
        // first gap 12:00-12:10 (10 min), second gap 13:00-14:00 (would be 60)
        val sessions = listOf(
            span(1, "08:00", "12:00"),
            span(2, "12:10", "13:00"),
            span(3, "14:00", "17:00"),
        )
        assertEquals(10, credit(sessions))
    }

    @Test
    fun `first gap not qualifying does not block a later qualifying gap`() {
        // first gap starts before window (0), second gap inside window earns credit
        val sessions = listOf(
            span(1, "08:00", "10:00"),
            span(2, "10:30", "12:00"),
            span(3, "13:00", "17:00"),
        )
        assertEquals(60, credit(sessions))
    }

    @Test
    fun `running session is excluded from gap detection`() {
        val sessions = listOf(span(1, "08:00", "12:00"), SessionSpan(2, at("13:00"), null))
        assertEquals(0, credit(sessions))
    }

    @Test
    fun `single session earns no credit`() {
        assertEquals(0, credit(listOf(span(1, "08:00", "17:00"))))
    }

    @Test
    fun `no sessions earns no credit`() {
        assertEquals(0, credit(emptyList()))
    }

    @Test
    fun `overlapping sessions produce no gap`() {
        val sessions = listOf(span(1, "08:00", "13:00"), span(2, "12:00", "17:00"))
        assertEquals(0, credit(sessions))
    }

    @Test
    fun `cap is configurable`() {
        val sessions = listOf(span(1, "08:00", "12:00"), span(2, "13:30", "17:00"))
        assertEquals(45, credit(sessions, cap = 45))
    }

    @Test
    fun `gap on a different day is ignored`() {
        // overnight first session: in Monday 22:00, out Tuesday 12:00 belongs to Monday
        val monday = day.minusDays(1)
        val sessions = listOf(
            SessionSpan(1, at("22:00", monday), at("12:00")),
            span(2, "13:00", "17:00"),
        )
        // day sessions only include session 2 -> no gap for `day`
        assertEquals(0, credit(sessions))
    }
}
