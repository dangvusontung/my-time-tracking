package com.example.mypersonaltimetracker.wear

import com.example.mypersonaltimetracker.companion.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TodayUiTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(time: String) = LocalDate.of(2026, 10, 22).atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun state(vararg sessions: WatchSession) = LocalState(WatchState(
        at("18:00").toEpochMilli(), "2026-10-22", zone.id, sessions.toList(), null,
        480, 690, 840, 60, 0, 1440,
    ), emptyList(), null, connected = true)

    @Test fun unchangedCompletedDayDoesNotInvalidateUiEverySecond() {
        val state = state(WatchSession(at("08:00").toEpochMilli(), at("17:00").toEpochMilli()))
        assertEquals(todayUi(state, at("18:00:01")), todayUi(state, at("18:00:59")))
        assertNull(todayUi(state, at("18:00:01")).leave)
    }

    @Test fun runningTimeAdvancesOnMinuteUpdates() {
        val state = state(WatchSession(at("08:00").toEpochMilli(), null))
        assertEquals("8h00", todayUi(state, at("17:00")).credited)
        assertEquals("8h01", todayUi(state, at("17:01")).credited)
    }

    @Test fun offlineActionImmediatelyChangesStatusWithoutWaitingForNextTick() {
        val state = state().copy(pending = listOf(WatchAction("start", at("08:00").toEpochMilli(), true, at("08:00").toEpochMilli())))
        val ui = todayUi(state, at("08:00"))
        assertTrue(ui.inOffice)
        assertTrue(ui.sync.contains("chờ đồng bộ"))
    }

    @Test fun missingSnapshotShowsSetupWithoutInventingProgress() {
        val ui = todayUi(LocalState(null, emptyList(), null), at("08:00"))
        assertTrue(ui.needsSetup)
        assertFalse(ui.hasTarget)
    }
}
