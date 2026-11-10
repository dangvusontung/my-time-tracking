package com.example.mypersonaltimetracker.wear

import androidx.compose.runtime.Immutable
import com.example.mypersonaltimetracker.companion.*
import com.example.mypersonaltimetracker.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Immutable
data class HistoryDayUi(
    val date: String,
    val label: String,
    val creditedMinutes: Int,
    val officeMinutes: Int,
    val lunchExtraMinutes: Int,
    val sessions: List<String>,
    val exception: String?,
    val timeline: List<OfficeSegment> = emptyList(),
    val attendance: AttendanceSpan? = null,
)

@Immutable
data class HistoryMonthUi(val total: Int, val average: Int, val days: Int, val averageSpan: Int? = null)

@Immutable
data class HistoryUi(
    val today: String = LocalDate.now().toString(),
    val days: Map<String, HistoryDayUi> = emptyMap(),
    val months: List<String> = emptyList(),
    val weeklyTarget: Int = 2400,
    val targets: Map<String, Int> = emptyMap(),
    val synced: Boolean = false,
    val pending: Boolean = false,
    val dailyTarget: Int = 480,
    val fridayLeave: String? = null,
    val monthStats: Map<String, HistoryMonthUi> = emptyMap(),
)

fun historyUi(local: LocalState, months: Map<String, WatchHistoryMonth>, now: Instant): HistoryUi {
    val snapshot = local.snapshot
    val zone = snapshot?.zone?.let(ZoneId::of) ?: ZoneId.systemDefault()
    val today = LocalDate.ofInstant(now, zone)
    val cached = months.values.flatMap { it.days }.associateBy { it.date }
    val pendingDates = local.pending.map { Instant.ofEpochMilli(it.sessionStart).atZone(zone).toLocalDate().toString() }.toSet()
    val dates = cached.keys + pendingDates + today.toString()
    val dateFormat = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("vi"))
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val days = dates.associateWith { date ->
        val day = LocalDate.parse(date)
        val old = cached[date]
        var sessions = old?.sessions.orEmpty()
        if (day == today && snapshot != null) sessions = local.projected(now.toEpochMilli())
        else if (date in pendingDates) {
            sessions = local.pending.filter { Instant.ofEpochMilli(it.sessionStart).atZone(zone).toLocalDate() == day }
                .fold(sessions) { current, action -> WatchActions.apply(current, action, now.toEpochMilli()).sessions }
        }
        val exception = if (day == today) snapshot?.takeIf { it.date == date }?.exception else old?.exception
        val recalculate = snapshot != null && (day == today || date in pendingDates || sessions.isNotEmpty())
        val spans = sessions.mapIndexed { i, s -> SessionSpan(i.toLong(), Instant.ofEpochMilli(s.inAt), s.outAt?.let(Instant::ofEpochMilli)) }
        val summary = if (recalculate) DaySummaryCalculator.compute(spans, exception?.let(DayType::valueOf), day, now,
            snapshot!!.lunchStart, snapshot.lunchEnd, snapshot.lunchDeduct, zone) else null
        val lunch = if (recalculate) LunchBreakStatus.compute(spans, day, now, zone,
            snapshot!!.lunchStart, snapshot.lunchEnd, snapshot.lunchDeduct).extraMinutes else old?.lunchExtraMinutes ?: 0
        HistoryDayUi(date, day.format(dateFormat), summary?.totalMinutes ?: old?.creditedMinutes ?: 0,
            summary?.officeMinutes ?: old?.officeMinutes ?: 0, lunch,
            sessions.filter { Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() == day }.sortedBy { it.inAt }.map {
                "${timeFormat.format(Instant.ofEpochMilli(it.inAt))} → ${it.outAt?.let { end -> timeFormat.format(Instant.ofEpochMilli(end)) } ?: "đang làm"}"
            }, exception, officeTimeline(sessions, day, now, zone, snapshot?.lunchStart ?: 690, snapshot?.lunchEnd ?: 840), AttendanceSpan.compute(spans, day, now, zone))
    }
    val monthStats = days.values.filter { it.date <= today.toString() && it.creditedMinutes > 0 }
        .groupBy { it.date.take(7) }.mapValues { (_, rows) ->
            val total = rows.sumOf { it.creditedMinutes }
            HistoryMonthUi(total, total / rows.size, rows.size,
                rows.mapNotNull { it.attendance?.minutes }.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size })
        }
    val metadata = months[YearMonth.from(today).toString()] ?: months.values.maxByOrNull { it.month }
    val fridayLeave = if (snapshot != null && months.isNotEmpty() && today.dayOfWeek == DayOfWeek.FRIDAY) {
        val monday = today.with(DayOfWeek.MONDAY)
        val prior = (0..3).sumOf { days[monday.plusDays(it.toLong()).toString()]?.creditedMinutes ?: 0 }
        val spans = local.projected(now.toEpochMilli()).mapIndexed { index, session ->
            SessionSpan(index.toLong(), Instant.ofEpochMilli(session.inAt), session.outAt?.let(Instant::ofEpochMilli))
        }
        FridayLeaveCalculator.compute(spans, snapshot.takeIf { it.date == today.toString() }?.exception?.let(DayType::valueOf),
            today, now, prior, snapshot.lunchStart, snapshot.lunchEnd, snapshot.lunchDeduct, zone)?.let(timeFormat::format)
    } else null
    return HistoryUi(today.toString(), days, (months.keys + dates.map { it.take(7) } + YearMonth.from(today).toString()).filter { it <= YearMonth.from(today).toString() }.sortedDescending(),
        metadata?.weeklyTarget ?: 2400, months.values.flatMap { it.weekTargets.entries }.associate { it.toPair() },
        months.isNotEmpty(), local.pending.isNotEmpty(), snapshot?.target ?: ((metadata?.weeklyTarget ?: 2400) / (metadata?.workdays?.size ?: 5).coerceAtLeast(1)), fridayLeave, monthStats)
}

fun watchMinutes(minutes: Int): String = if (minutes < 60) "${minutes}p" else "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
