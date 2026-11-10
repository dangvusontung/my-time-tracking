package com.example.mypersonaltimetracker.wear

import androidx.compose.runtime.Immutable
import com.example.mypersonaltimetracker.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** All calculation and formatting happens upstream, off the UI thread. */
@Immutable
data class TodayUi(
    val needsSetup: Boolean = true,
    val date: String = "Hôm nay",
    val inOffice: Boolean = false,
    val credited: String = "",
    val hasTarget: Boolean = false,
    val progress: Float = 0f,
    val target: String = "",
    val remaining: String? = null,
    val since: String? = null,
    val leave: String? = null,
    val sync: String = "",
    val queueFull: Boolean = false,
    val error: String? = null,
)

fun todayUi(state: LocalState, now: Instant): TodayUi {
    val snapshot = state.snapshot
    val zone = snapshot?.zone?.let(ZoneId::of) ?: ZoneId.systemDefault()
    val today = LocalDate.ofInstant(now, zone)
    val date = "Hôm nay · ${today.format(DATE_FORMAT)}"
    if (snapshot == null) return TodayUi(date = date, error = state.error)
    val sessions = state.projected(now.toEpochMilli())
    val running = sessions.firstOrNull { it.outAt == null }
    val spans = sessions.mapIndexed { index, s ->
        SessionSpan(index.toLong(), Instant.ofEpochMilli(s.inAt), s.outAt?.let(Instant::ofEpochMilli))
    }
    val exception = snapshot.takeIf { it.date == today.toString() }?.exception?.let(DayType::valueOf)
    val summary = DaySummaryCalculator.compute(spans, exception, today, now,
        snapshot.lunchStart, snapshot.lunchEnd, snapshot.lunchDeduct, zone)
    val progress = DailyGoalProgress(summary.totalMinutes, snapshot.target)
    val leave = if (running == null) null else EarliestLeaveCalculator.compute(spans, exception, today, now,
        snapshot.lunchStart, snapshot.lunchEnd, snapshot.lunchDeduct, zone)
    fun time(at: Instant) = TIME_FORMAT.format(at.atZone(zone))
    return TodayUi(
        needsSetup = false,
        date = date,
        inOffice = running != null,
        credited = minutesText(summary.totalMinutes),
        hasTarget = progress.hasTarget,
        progress = progress.fraction,
        target = if (progress.hasTarget) "${progress.percent}% / ${minutesText(snapshot.target)}" else "Chưa có mục tiêu ngày",
        remaining = when {
            !progress.hasTarget -> null
            progress.extraMinutes > 0 -> "Đã đạt · +${minutesText(progress.extraMinutes)}"
            progress.remainingMinutes == 0 -> "Đã đủ giờ hôm nay"
            else -> "Còn ${minutesText(progress.remainingMinutes!!)}"
        },
        since = running?.let { "Vào lúc ${time(Instant.ofEpochMilli(it.inAt))}" },
        leave = if (running == null) null else leave?.let { "Đủ 8h30 lúc: ${time(it)}" } ?: "Chưa tính được giờ đủ 8h30",
        sync = when {
            state.pending.isNotEmpty() -> "${state.pending.size} thao tác đã lưu · chờ đồng bộ"
            snapshot.date != today.toString() -> "Chưa đồng bộ hôm nay · giờ tạm tính"
            !state.connected -> "Mất kết nối · dữ liệu đã lưu"
            else -> "Cập nhật ${time(Instant.ofEpochMilli(snapshot.updatedAt))}"
        },
        queueFull = state.pending.size >= 200,
        error = state.error,
    )
}

private fun minutesText(minutes: Int): String = watchMinutes(minutes)
