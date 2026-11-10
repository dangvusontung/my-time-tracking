package com.example.mypersonaltimetracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * US-24: cảnh báo sớm khi tuần đang thiếu giờ và gần như không thể bù kịp.
 * Từ Thứ Năm trở đi, nếu số giờ cần bù mỗi ngày làm việc còn lại vượt quá 8h
 * thì khớp lịch bình thường không cứu được nữa — nhắc người dùng sắp xếp bù.
 */
object ShortfallWarning {

    const val MAX_RECOVERABLE_PER_DAY_MINUTES = 8 * 60

    fun shouldWarn(today: LocalDate, progress: WeekProgress): Boolean =
        today.dayOfWeek >= DayOfWeek.THURSDAY &&
            progress.remainingMinutes > 0 &&
            progress.perDayNeededMinutes > MAX_RECOVERABLE_PER_DAY_MINUTES
}
