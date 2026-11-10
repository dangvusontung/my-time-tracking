package com.example.mypersonaltimetracker.companion

import org.junit.Assert.*
import org.junit.Test

class WatchHistoryTest {
    @Test fun monthlyHistorySurvivesSerializationAndKeepsRunningSessions() {
        val history = WatchHistoryMonth("2026-10", 123, "Asia/Ho_Chi_Minh", listOf(
            WatchHistoryDay("2026-10-22", listOf(WatchSession(1000, 2000), WatchSession(3000, null)),
                "HALF_DAY_LEAVE", 480, 720, 30)
        ), 2400, listOf(1, 2, 3, 4, 5), mapOf("2026-10-19" to 2100))
        assertEquals(history, WatchHistoryMonth.decode(history.encode()))
    }

    @Test fun anEmptyMonthCanReplaceOldDataAfterDeletion() {
        val history = WatchHistoryMonth("2026-10", 124, "Asia/Ho_Chi_Minh", emptyList(), 2400, listOf(1, 2, 3, 4, 5), emptyMap())
        assertEquals(history, WatchHistoryMonth.decode(history.encode()))
        assertTrue(WatchHistoryMonth.decode(history.encode()).days.isEmpty())
    }
}
