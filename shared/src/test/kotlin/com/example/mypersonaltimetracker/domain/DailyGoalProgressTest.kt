package com.example.mypersonaltimetracker.domain

import org.junit.Assert.*
import org.junit.Test

class DailyGoalProgressTest {
    @Test fun partialDayUsesCreditedMinutesAndOfficialTarget() {
        val goal = DailyGoalProgress(300, 480)
        assertEquals(0.625f, goal.fraction, 0.0001f)
        assertEquals(62, goal.percent)
        assertEquals(180, goal.remainingMinutes)
    }

    @Test fun completedDayClampsRingAndShowsExtraTime() {
        val goal = DailyGoalProgress(540, 480)
        assertEquals(1f, goal.fraction, 0f)
        assertEquals(112, goal.percent)
        assertEquals(0, goal.remainingMinutes)
        assertEquals(60, goal.extraMinutes)
    }

    @Test fun missingTargetDoesNotClaimGoalReached() {
        for (target in listOf(0, -1)) {
            val goal = DailyGoalProgress(100, target)
            assertFalse(goal.hasTarget)
            assertNull(goal.remainingMinutes)
            assertNull(goal.percent)
            assertEquals(0f, goal.fraction, 0f)
        }
    }

    @Test fun negativeCreditCannotCreateNegativeProgress() {
        val goal = DailyGoalProgress(-10, 480)
        assertEquals(0f, goal.fraction, 0f)
        assertEquals(0, goal.percent)
        assertEquals(480, goal.remainingMinutes)
    }
}
