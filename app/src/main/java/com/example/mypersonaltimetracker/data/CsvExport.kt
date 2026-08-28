package com.example.mypersonaltimetracker.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds the CSV export. Layout (documented in header comment rows, lines starting with '#'):
 *  - Section "sessions": one row per session: `date,in,out,duration_minutes,is_estimated`
 *    (times in local timezone; `out` empty for a still-open session, duration counted up to now)
 *  - Section "exceptions": one row per day exception: `date,type,note`
 */
object CsvExport {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        sessions: List<SessionEntity>,
        exceptions: List<DayExceptionEntity>,
        now: Instant,
        zone: ZoneId,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Office Time Tracker - CSV export")
        sb.appendLine("# sessions: date,in,out,duration_minutes,is_estimated (gio dia phuong; out rong = phien dang mo)")
        sessions.sortedBy { it.inAt }.forEach { s ->
            val inAt = Instant.ofEpochMilli(s.inAt)
            val outAt = s.outAt?.let(Instant::ofEpochMilli)
            val date = inAt.atZone(zone).format(dateFormatter)
            val inTime = inAt.atZone(zone).format(timeFormatter)
            val outTime = outAt?.atZone(zone)?.format(timeFormatter) ?: ""
            val durationMin = maxOf(0L, ((outAt ?: now).toEpochMilli() - s.inAt) / 60000)
            sb.appendLine("$date,$inTime,$outTime,$durationMin,${s.isEstimated}")
        }
        sb.appendLine("# exceptions: date,type,note")
        exceptions.sortedBy { it.date }.forEach { e ->
            sb.appendLine("${e.date},${e.type.name},${escape(e.note ?: "")}")
        }
        return sb.toString()
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
