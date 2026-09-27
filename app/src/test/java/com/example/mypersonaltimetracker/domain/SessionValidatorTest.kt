package com.example.mypersonaltimetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SessionValidatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day: LocalDate = LocalDate.of(2026, 8, 25)

    private fun at(time: String): Instant =
        LocalDateTime.parse("${day}T$time").atZone(zone).toInstant()

    private fun span(id: Long, inT: String, outT: String?) =
        SessionSpan(id, at(inT), outT?.let { at(it) })

    private val now = at("18:00")

    @Test
    fun `valid session has no errors`() {
        val errors = SessionValidator.validate(span(0, "13:00", "14:00"), listOf(span(1, "08:00", "12:00")), now, zone)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `out before in is rejected`() {
        val errors = SessionValidator.validate(span(0, "13:00", "12:00"), emptyList(), now, zone)
        assertEquals(listOf(SessionValidationError.OUT_NOT_AFTER_IN), errors)
    }

    @Test
    fun `out equal to in is rejected`() {
        val errors = SessionValidator.validate(span(0, "13:00", "13:00"), emptyList(), now, zone)
        assertEquals(listOf(SessionValidationError.OUT_NOT_AFTER_IN), errors)
    }

    @Test
    fun `overlapping session is rejected`() {
        val existing = listOf(span(1, "08:00", "12:00"))
        assertTrue(SessionValidator.validate(span(0, "11:00", "13:00"), existing, now, zone).contains(SessionValidationError.OVERLAP))
        assertTrue(SessionValidator.validate(span(0, "07:00", "09:00"), existing, now, zone).contains(SessionValidationError.OVERLAP))
        assertTrue(SessionValidator.validate(span(0, "09:00", "10:00"), existing, now, zone).contains(SessionValidationError.OVERLAP))
        assertTrue(SessionValidator.validate(span(0, "07:00", "13:00"), existing, now, zone).contains(SessionValidationError.OVERLAP))
    }

    @Test
    fun `touching sessions do not overlap`() {
        val existing = listOf(span(1, "08:00", "12:00"))
        assertTrue(SessionValidator.validate(span(0, "12:00", "13:00"), existing, now, zone).isEmpty())
        assertTrue(SessionValidator.validate(span(0, "06:00", "08:00"), existing, now, zone).isEmpty())
    }

    @Test
    fun `running session treated as in to now for overlap`() {
        val existing = listOf(span(1, "16:00", null))
        // 15:00 -> 17:00 overlaps 16:00 -> now(18:00)
        assertTrue(SessionValidator.validate(span(0, "15:00", "17:00"), existing, now, zone).contains(SessionValidationError.OVERLAP))
    }

    @Test
    fun `second running session is rejected`() {
        val existing = listOf(span(1, "08:00", null))
        val errors = SessionValidator.validate(SessionSpan(0, at("20:00"), null), existing, now, zone)
        assertTrue(errors.contains(SessionValidationError.DUPLICATE_RUNNING))
    }

    @Test
    fun `editing a session does not conflict with itself`() {
        val existing = listOf(span(1, "08:00", null))
        val errors = SessionValidator.validate(span(1, "08:00", "17:00"), existing, now, zone)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `open session starting today is allowed`() {
        val errors = SessionValidator.validate(SessionSpan(0, at("17:00"), null), emptyList(), now, zone)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `open session starting on a past day is rejected`() {
        val yesterday = day.minusDays(1)
        val candidate = SessionSpan(
            0,
            LocalDateTime.parse("${yesterday}T08:00").atZone(zone).toInstant(),
            null,
        )
        val errors = SessionValidator.validate(candidate, emptyList(), now, zone)
        assertTrue(errors.contains(SessionValidationError.OPEN_SESSION_IN_PAST))
    }

    @Test
    fun `closed session on a past day is allowed`() {
        val yesterday = day.minusDays(1)
        fun atYesterday(time: String) = LocalDateTime.parse("${yesterday}T$time").atZone(zone).toInstant()
        val candidate = SessionSpan(0, atYesterday("08:00"), atYesterday("17:00"))
        val errors = SessionValidator.validate(candidate, emptyList(), now, zone)
        assertTrue(errors.isEmpty())
    }
}
