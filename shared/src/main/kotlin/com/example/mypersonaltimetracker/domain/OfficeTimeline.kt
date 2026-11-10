package com.example.mypersonaltimetracker.domain

import com.example.mypersonaltimetracker.companion.WatchSession
import java.time.*

enum class OfficeKind { OFFICE, OUTSIDE, LUNCH }
data class OfficeSegment(val start: Float, val end: Float, val kind: OfficeKind)

/** Only observed time is colored; no invented lunch interval or future office time. */
fun officeTimeline(sessions: List<WatchSession>, day: LocalDate, now: Instant, zone: ZoneId,
                   lunchStart: Int, lunchEnd: Int): List<OfficeSegment> {
    val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val until = minOf(now.toEpochMilli(), dayEnd)
    val spans = sessions.filter { it.inAt < until && (it.outAt ?: until) > midnight }
        .map { maxOf(it.inAt, midnight) to minOf(it.outAt ?: until, until) }
        .filter { it.second > it.first }.sortedBy { it.first }
    val merged = mutableListOf<Pair<Long, Long>>()
    for (span in spans) {
        val last = merged.lastOrNull()
        if (last != null && span.first <= last.second) merged[merged.lastIndex] = last.first to maxOf(last.second, span.second)
        else merged += span
    }
    fun minute(at: Long) = (at - midnight) / 60_000f
    val gaps = merged.zipWithNext().map { (a, b) -> a.second to b.first }.toMutableList()
    // The current outing is visible too, without filling the rest of the day red.
    if (day == now.atZone(zone).toLocalDate() && merged.isNotEmpty() && sessions.none { it.outAt == null } && merged.last().second < until)
        gaps += merged.last().second to until
    val lunch = gaps.filter { minute(it.first) < lunchEnd && minute(it.second) > lunchStart }
        .maxByOrNull { it.second - it.first }
    return (merged.map { OfficeSegment(minute(it.first), minute(it.second), OfficeKind.OFFICE) } +
        gaps.map { OfficeSegment(minute(it.first), minute(it.second), if (it == lunch) OfficeKind.LUNCH else OfficeKind.OUTSIDE) })
        .sortedBy { it.start }
}
