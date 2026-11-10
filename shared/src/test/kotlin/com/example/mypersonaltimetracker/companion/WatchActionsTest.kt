package com.example.mypersonaltimetracker.companion

import org.junit.Assert.*
import org.junit.Test

class WatchActionsTest {
    @Test fun staleShortcutCannotStopANewerSession() {
        assertNull(WatchActions.shortcut(listOf(WatchSession(3000, null)), false, 1000, 4000, "tap"))
    }

    @Test fun repeatedShortcutCannotStartAnotherOpenSession() {
        assertNull(WatchActions.shortcut(listOf(WatchSession(1000, null)), true, null, 4000, "tap"))
    }

    @Test fun shortcutStopsTheExactSessionShownOnTheFace() {
        assertEquals(WatchAction("tap", 4000, false, 1000),
            WatchActions.shortcut(listOf(WatchSession(1000, null)), false, 1000, 4000, "tap"))
    }
    private fun start(at: Long) = WatchAction("start-$at", at, true, at)
    private fun stop(at: Long, target: Long) = WatchAction("stop-$at", at, false, target)

    @Test fun disconnectedLunchBreakReplaysInOrder() {
        val actions = listOf(start(1000), stop(2000, 1000), start(3000), stop(4000, 3000))
        val sessions = actions.fold(emptyList<WatchSession>()) { current, action ->
            val result = WatchActions.apply(current, action, 5000)
            assertNull(result.error)
            result.sessions
        }
        assertEquals(listOf(WatchSession(1000, 2000), WatchSession(3000, 4000)), sessions)
    }

    @Test fun duplicateDeliveryDoesNotReverseState() {
        val started = WatchActions.apply(emptyList(), start(1000), 5000).sessions
        assertEquals(started, WatchActions.apply(started, start(1000), 5000).sessions)
        val closed = WatchActions.apply(started, stop(2000, 1000), 5000).sessions
        assertEquals(closed, WatchActions.apply(closed, stop(2000, 1000), 5000).sessions)
    }

    @Test fun delayedStopCannotCloseDifferentSession() {
        val sessions = listOf(WatchSession(1000, 2000), WatchSession(3000, null))
        val result = WatchActions.apply(sessions, stop(4000, 1000), 5000)
        assertNotNull(result.error)
        assertEquals(sessions, result.sessions)
    }

    @Test fun conflictingPhoneEditIsPreserved() {
        val sessions = listOf(WatchSession(1000, 3000))
        assertNotNull(WatchActions.apply(sessions, start(2000), 5000).error)
        assertNotNull(WatchActions.apply(sessions, stop(4000, 1000), 5000).error)
    }

    @Test fun reversedAndFutureTimestampsAreRejected() {
        assertNotNull(WatchActions.apply(listOf(WatchSession(1000, null)), stop(500, 1000), 5000).error)
        assertNotNull(WatchActions.apply(emptyList(), start(70_000), 5000).error)
    }

    @Test fun snapshotArrivingBeforeAcknowledgementCanReplayPendingActions() {
        val sessions = listOf(WatchSession(1000, 2000), WatchSession(3000, null))
        val replayed = listOf(start(1000), stop(2000, 1000), start(3000))
            .fold(sessions) { current, action -> WatchActions.apply(current, action, 5000).sessions }
        assertEquals(sessions, replayed)
    }
}
