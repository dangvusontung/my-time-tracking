package com.example.mypersonaltimetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SessionValidationError {
    OUT_NOT_AFTER_IN,
    OVERLAP,
    DUPLICATE_RUNNING,
    OPEN_SESSION_IN_PAST,
}

object SessionValidator {

    /**
     * Validates a candidate session against the other existing sessions.
     * A running session is treated as in..now for overlap checks.
     * An open session (no outAt) is only allowed to start today — past days
     * must have an end time.
     */
    fun validate(
        candidate: SessionSpan,
        others: List<SessionSpan>,
        now: Instant,
        zone: ZoneId,
    ): List<SessionValidationError> {
        val errors = mutableListOf<SessionValidationError>()
        if (candidate.outAt != null && !candidate.outAt.isAfter(candidate.inAt)) {
            errors += SessionValidationError.OUT_NOT_AFTER_IN
        }
        val candEnd = candidate.endFor(now)
        val overlaps = others.any { o ->
            o.id != candidate.id &&
                candidate.inAt.isBefore(o.endFor(now)) &&
                o.inAt.isBefore(candEnd)
        }
        if (overlaps) errors += SessionValidationError.OVERLAP
        if (candidate.outAt == null) {
            val today: LocalDate = now.atZone(zone).toLocalDate()
            if (candidate.inAt.atZone(zone).toLocalDate() < today) {
                errors += SessionValidationError.OPEN_SESSION_IN_PAST
            }
            if (others.any { it.id != candidate.id && it.outAt == null }) {
                errors += SessionValidationError.DUPLICATE_RUNNING
            }
        }
        return errors
    }
}
