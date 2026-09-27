package com.example.mypersonaltimetracker.domain

import java.time.LocalDate
import java.time.YearMonth

data class MonthTotal(
    val yearMonth: YearMonth,
    val totalMinutes: Int,
    /** Số ngày có dữ liệu (có phiên hoặc loại ngày được cộng giờ) trong tháng. */
    val daysWithData: Int,
    /** Trung bình tổng giờ mỗi ngày có dữ liệu. */
    val avgPerDayMinutes: Int,
)

/**
 * US-26: nhóm tổng giờ theo tháng từ các DaySummary đã tính (office − nghỉ trưa
 * + cộng loại ngày — cùng định nghĩa với tổng tuần).
 */
object MonthTotalsCalculator {

    /**
     * @param daySummaries DaySummary theo ngày (đã qua DaySummaryCalculator)
     * @param maxMonths số tháng gần nhất (có dữ liệu) được trả về, mới nhất trước
     */
    fun aggregate(daySummaries: Map<LocalDate, DaySummary>, maxMonths: Int): List<MonthTotal> =
        daySummaries
            .filterValues { it.totalMinutes > 0 }
            .entries
            .groupBy({ YearMonth.from(it.key) }, { it.value })
            .map { (yearMonth, summaries) ->
                val total = summaries.sumOf { it.totalMinutes }
                MonthTotal(
                    yearMonth = yearMonth,
                    totalMinutes = total,
                    daysWithData = summaries.size,
                    avgPerDayMinutes = total / summaries.size,
                )
            }
            .sortedByDescending { it.yearMonth }
            .take(maxMonths)
}
