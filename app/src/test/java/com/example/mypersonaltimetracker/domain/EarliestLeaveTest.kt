package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EarliestLeaveTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day: LocalDate = LocalDate.of(2026, 8, 25)

    private fun at(time: String, d: LocalDate = day): Instant =
        LocalDateTime.parse("${d}T$time").atZone(zone).toInstant()

    private fun span(id: Long, inT: String, outT: String?, d: LocalDate = day) =
        SessionSpan(id, at(inT, d), outT?.let { at(it, d) })

    private fun earliest(
        sessions: List<SessionSpan>,
        exception: DayType? = null,
        now: Instant,
    ) = EarliestLeaveCalculator.compute(
        sessions, exception, day, now,
        lunchStartMin = 690, lunchEndMin = 840, lunchMaxDeductMin = 60, zone = zone,
    )

    @Test
    fun `no running session returns null`() {
        val sessions = listOf(span(1, "08:00", "12:00"))
        assertNull(earliest(sessions, now = at("14:00")))
    }

    @Test
    fun `leave now when jungle law already met`() {
        // 08:00 -> now 18:00 continuous: 600 office - 60 lunch = 540 >= 510 -> leave now.
        val sessions = listOf(span(1, "08:00", null))
        assertEquals(at("18:00"), earliest(sessions, now = at("18:00")))
    }

    @Test
    fun `running session without lunch deduction`() {
        // In at 08:00, now 10:00. No lunch coverage yet before 11:30 window? Window starts
        // 11:30, so deduction grows once the candidate crosses it — see next test.
        // Here in at 15:00 (after the window): 8h30 later = 23:30, no lunch deduction.
        val sessions = listOf(span(1, "15:00", null))
        assertEquals(at("23:30"), earliest(sessions, now = at("16:00")))
    }

    @Test
    fun `lunch deduction pushes leave time out`() {
        // In at 08:00 continuous. Total at t = (t - 08:00) - 60 (once lunch window covered).
        // 510 total needs 570 office minutes: 08:00 + 9h30 = 17:30.
        val sessions = listOf(span(1, "08:00", null))
        assertEquals(at("17:30"), earliest(sessions, now = at("09:00")))
    }

    @Test
    fun `actual lunch gap already excluded`() {
        // 08:00-12:00 completed, 13:00 running. Gap 12:00-13:00 = actual lunch, no deduction.
        // Office at t = 240 + (t - 13:00). Need 510 -> 270 more -> 17:30.
        val sessions = listOf(span(1, "08:00", "12:00"), span(2, "13:00", null))
        assertEquals(at("17:30"), earliest(sessions, now = at("14:00")))
    }

    @Test
    fun `half day exception credit counts toward the law`() {
        // Half-day leave (240) + one continuous session from 13:00 (lunch -60 once covered).
        // Need 330 office minutes: 240 + 330 - 60 = 510 -> 13:00 + 5h30 = 18:30.
        val sessions = listOf(span(1, "13:00", null))
        assertEquals(at("18:30"), earliest(sessions, DayType.HALF_DAY_LEAVE, now = at("14:00")))
    }

    private fun earliestClamped(
        sessions: List<SessionSpan>,
        exception: DayType? = null,
        now: Instant,
    ) = EarliestLeaveCalculator.compute(
        sessions, exception, day, now,
        lunchStartMin = 690, lunchEndMin = 840, lunchMaxDeductMin = 60, zone = zone,
        countFromMin = 450, countUntilMin = 990, // 7:30-16:30
    )

    @Test
    fun `check-in before the window counts from 7h30`() {
        // In at 07:00, window starts 7:30. With lunch -60, max total by 16:30 is
        // 540 - 60 = 480 < 510 -> unreachable -> null.
        val sessions = listOf(span(1, "07:00", null))
        assertNull(earliestClamped(sessions, now = at("08:00")))
    }

    @Test
    fun `leave time is capped by the window end`() {
        // Same day but lunch is only -30 worth... instead: half-day leave 240 +
        // session 7:30 running, lunch -60: 240 + (t-7:30) - 60 = 510 -> t = 13:00.
        val sessions = listOf(span(1, "07:30", null))
        assertEquals(at("13:00"), earliestClamped(sessions, DayType.HALF_DAY_LEAVE, now = at("08:00")))
    }

    @Test
    fun `unreachable within the window returns null`() {
        // In at 10:00 continuous: by 16:30 only 390 office - 60 lunch = 330 < 510.
        val sessions = listOf(span(1, "10:00", null))
        assertNull(earliestClamped(sessions, now = at("11:00")))
    }
}
