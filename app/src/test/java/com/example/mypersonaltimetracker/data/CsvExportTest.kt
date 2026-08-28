package com.example.mypersonaltimetracker.data

import com.example.mypersonaltimetracker.domain.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CsvExportTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun at(day: LocalDate, time: String): Long =
        day.atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `sessions exported with local times duration and isEstimated`() {
        val day = LocalDate.of(2026, 8, 28)
        val sessions = listOf(
            SessionEntity(id = 1, inAt = at(day, "08:15"), outAt = at(day, "12:05"), isEstimated = false),
            SessionEntity(id = 2, inAt = at(day, "13:00"), outAt = null, isEstimated = true),
        )
        val csv = CsvExport.build(sessions, emptyList(), now = Instant.ofEpochMilli(at(day, "14:30")), zone)
        val lines = csv.lines()
        assertTrue(lines[0].startsWith("#"))
        assertEquals("2026-08-28,08:15,12:05,230,false", lines[2])
        // open session: empty out, duration counted up to now
        assertEquals("2026-08-28,13:00,,90,true", lines[3])
    }

    @Test
    fun `exceptions exported in appended section`() {
        val exceptions = listOf(
            DayExceptionEntity("2026-08-27", DayType.HALF_DAY_LEAVE, "nghi, som"),
        )
        val csv = CsvExport.build(emptyList(), exceptions, Instant.now(), zone)
        val lines = csv.lines()
        assertTrue(lines.any { it == "# exceptions: date,type,note" })
        // note containing a comma is quoted
        assertTrue(lines.any { it == "2026-08-27,HALF_DAY_LEAVE,\"nghi, som\"" })
    }
}
