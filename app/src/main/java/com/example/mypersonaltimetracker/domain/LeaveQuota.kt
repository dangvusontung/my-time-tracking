package com.example.mypersonaltimetracker.domain

import java.time.LocalDate

/**
 * US-39: đếm số ngày phép năm đã dùng. Chỉ ANNUAL_LEAVE (1.0 ngày) và
 * HALF_DAY_LEAVE (0.5 ngày) trừ vào hạn mức — WFH / PUBLIC_HOLIDAY /
 * UNPAID_LEAVE không trừ (lý do enum được tách riêng).
 */
object LeaveQuotaCalculator {

    fun usedDays(exceptionTypes: Map<LocalDate, DayType>, year: Int): Double =
        exceptionTypes
            .filterKeys { it.year == year }
            .values
            .sumOf {
                when (it) {
                    DayType.ANNUAL_LEAVE -> 1.0
                    DayType.HALF_DAY_LEAVE -> 0.5
                    else -> 0.0
                }
            }
}
