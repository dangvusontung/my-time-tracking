package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DaySummaryTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day: LocalDate = LocalDate.of(2026, 8, 25)

    private fun at(time: String, d: LocalDate = day): Instant =
        LocalDateTime.parse("${d}T$time").atZone(zone).toInstant()

    private fun span(id: Long, inT: String, outT: String?, d: LocalDate = day) =
        SessionSpan(id, at(inT, d), outT?.let { at(it, d) })

    private fun summary(
        sessions: List<SessionSpan>,
        exception: DayType? = null,
        now: Instant = at("18:00"),
    ) = DaySummaryCalculator.compute(
        sessions, exception, day, now,
        lunchStartMin = 690, lunchEndMin = 840, lunchMaxDeductMin = 60, zone = zone,
    )

    @Test
    fun `office minutes sum completed sessions`() {
        val sessions = listOf(span(1, "08:00", "12:00"), span(2, "13:00", "17:00"))
        val s = summary(sessions)
        assertEquals(480, s.officeMinutes)
        assertEquals(0, s.lunchAdjustmentMinutes) // gap 12:00-13:00 is the actual lunch
        assertEquals(0, s.exceptionCreditMinutes)
        assertEquals(480, s.totalMinutes)
    }

    @Test
    fun `continuous session has lunch deducted`() {
        val s = summary(listOf(span(1, "08:00", "17:00")))
        assertEquals(540, s.officeMinutes)
        assertEquals(-60, s.lunchAdjustmentMinutes)
        assertEquals(480, s.totalMinutes)
    }

    @Test
    fun `running session counts up to now`() {
        val sessions = listOf(span(1, "08:00", null))
        val s = summary(sessions, now = at("11:00"))
        assertEquals(180, s.officeMinutes)
        assertEquals(180, s.totalMinutes)
    }

    @Test
    fun `exception credit is added to total`() {
        val sessions = listOf(span(1, "08:00", "11:00"))
        val s = summary(sessions, exception = DayType.HALF_DAY_LEAVE)
        assertEquals(180, s.officeMinutes)
        assertEquals(0, s.lunchAdjustmentMinutes) // session ends before the lunch window
        assertEquals(240, s.exceptionCreditMinutes)
        assertEquals(420, s.totalMinutes)
    }

    @Test
    fun `empty day with full-day exception`() {
        val s = summary(emptyList(), exception = DayType.PUBLIC_HOLIDAY)
        assertEquals(0, s.officeMinutes)
        assertEquals(480, s.totalMinutes)
    }

    @Test
    fun `overnight session belongs to local date of inAt`() {
        // Monday 22:00 -> Tuesday 02:00 belongs to Monday
        val monday = day.minusDays(1)
        val mondaySummary = DaySummaryCalculator.compute(
            listOf(span(1, "22:00", "02:00".let { null }, monday)), null, monday,
            at("23:00", monday), 690, 840, 60, zone,
        )
        assertEquals(60, mondaySummary.officeMinutes) // 22:00 -> 23:00 now
        // Tuesday sees nothing of it
        val tuesdaySummary = summary(listOf(SessionSpan(1, at("22:00", monday), at("02:00"))))
        assertEquals(0, tuesdaySummary.officeMinutes)
    }

    @Test
    fun `time outside the counting window is not counted`() {
        // 07:00 -> 17:30 with window 7:30-16:30: only 7:30-16:30 counts = 540.
        val s = DaySummaryCalculator.compute(
            listOf(span(1, "07:00", "17:30")), null, day, at("18:00"),
            lunchStartMin = 690, lunchEndMin = 840, lunchMaxDeductMin = 60, zone = zone,
            countFromMin = 450, countUntilMin = 990,
        )
        assertEquals(540, s.officeMinutes)
        assertEquals(-60, s.lunchAdjustmentMinutes) // continuous through the lunch window
        assertEquals(480, s.totalMinutes)
    }

    @Test
    fun `session fully before the window counts nothing`() {
        val s = DaySummaryCalculator.compute(
            listOf(span(1, "05:00", "07:00")), null, day, at("18:00"),
            lunchStartMin = 690, lunchEndMin = 840, lunchMaxDeductMin = 60, zone = zone,
            countFromMin = 450, countUntilMin = 990,
        )
        assertEquals(0, s.officeMinutes)
        assertEquals(0, s.totalMinutes)
    }
}
