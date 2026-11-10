package com.example.mypersonaltimetracker.wear

import com.example.mypersonaltimetracker.companion.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HistoryUiTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(date: String, time: String) = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    private fun local() = LocalState(WatchState(1, "2026-10-01", zone.id,
        listOf(WatchSession(at("2026-10-01", "08:00"), at("2026-10-01", "17:00"))), null,
        480, 690, 840, 60, 0, 1440), emptyList(), null)
    private val now = Instant.ofEpochMilli(at("2026-10-01", "18:00"))

    @Test fun weekAcrossTwoMonthsUsesBothCachedMonthsAndCurrentDay() {
        val september = WatchHistoryMonth("2026-09", 1, zone.id, listOf(
            WatchHistoryDay("2026-09-29", emptyList(), null, 480, 480, 0)
        ), 2400, listOf(1, 2, 3, 4, 5), mapOf("2026-09-28" to 2100))
        val october = september.copy(month = "2026-10", days = emptyList())
        val ui = historyUi(local(), mapOf("2026-09" to september, "2026-10" to october), now)
        assertEquals(480, ui.days.getValue("2026-09-29").creditedMinutes)
        assertEquals(480, ui.days.getValue("2026-10-01").creditedMinutes)
        assertEquals(2100, ui.targets["2026-09-28"])
        assertEquals(listOf("2026-10", "2026-09"), ui.months)
    }

    @Test fun disconnectedPreviousDayActionsAppearWithoutWaitingForPhone() {
        val start = at("2026-09-30", "08:00")
        val end = at("2026-09-30", "17:00")
        val state = local().copy(pending = listOf(WatchAction("in", start, true, start), WatchAction("out", end, false, start)))
        val ui = historyUi(state, emptyMap(), now)
        assertEquals(480, ui.days.getValue("2026-09-30").creditedMinutes)
        assertEquals(listOf("08:00 → 17:00"), ui.days.getValue("2026-09-30").sessions)
        assertTrue(ui.pending)
        assertTrue("2026-09" in ui.months)
    }

    @Test fun futurePlannedMonthsDoNotReplaceCurrentHistoryMonth() {
        val future = WatchHistoryMonth("2027-01", 1, zone.id, emptyList(), 2400, listOf(1, 2, 3, 4, 5), emptyMap())
        assertEquals(listOf("2026-10"), historyUi(local(), mapOf("2027-01" to future), now).months)
    }

    @Test fun fridayEstimateUsesEarlierWeekHistoryAndNeverPrecedes1630() {
        val state = local().copy(snapshot = local().snapshot!!.copy(date = "2026-10-23",
            sessions = listOf(WatchSession(at("2026-10-23", "08:30"), null))))
        val month = WatchHistoryMonth("2026-10", 1, zone.id,
            listOf(WatchHistoryDay("2026-10-19", emptyList(), null, 2027, 2027, 0)),
            2400, listOf(1, 2, 3, 4, 5), emptyMap())
        val ui = historyUi(state, mapOf(month.month to month), Instant.ofEpochMilli(at("2026-10-23", "09:00")))
        assertEquals("16:30", ui.fridayLeave)
    }

    @Test fun cachedEarlyArrivalUses0730RuleOfflineAndRetainsRawSpan() {
        val month = WatchHistoryMonth("2026-09", 1, zone.id,
            listOf(WatchHistoryDay("2026-09-30", listOf(WatchSession(at("2026-09-30", "06:30"), at("2026-09-30", "17:00"))),
                null, 630, 570, 0)), 2400, listOf(1, 2, 3, 4, 5), emptyMap())
        val ui = historyUi(local(), mapOf(month.month to month), now)
        val day = ui.days.getValue("2026-09-30")
        assertEquals(510, day.creditedMinutes)
        assertEquals(630, day.attendance!!.minutes)
        assertEquals(510, ui.monthStats.getValue("2026-09").average)
        assertEquals(630, ui.monthStats.getValue("2026-09").averageSpan)
    }
}
