package com.example.mypersonaltimetracker.widget

import com.example.mypersonaltimetracker.data.*
import com.example.mypersonaltimetracker.domain.DayType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class WidgetInputsTest {
    @Test fun activeWidgetReceivesClockInClockOutAndPhoneEdits() = runBlocking {
        withTimeout(5000) {
            val sessions = MutableStateFlow(emptyList<SessionEntity>())
            val exceptions = MutableStateFlow(emptyList<DayExceptionEntity>())
            val targets = MutableStateFlow(emptyList<WeekTargetEntity>())
            val settings = MutableStateFlow(AppSettings())
            val emissions = Channel<WidgetInputs>(Channel.UNLIMITED)
            val collector = launch {
                observeWidgetInputs(sessions, exceptions, targets, settings).collect { emissions.send(it) }
            }
            try {
                assertTrue(emissions.receive().sessions.isEmpty())
                sessions.value = listOf(SessionEntity(id = 1, inAt = 1000))
                assertNull(emissions.receive().sessions.single().outAt)
                sessions.value = listOf(SessionEntity(id = 1, inAt = 1000, outAt = 2000))
                assertEquals(2000L, emissions.receive().sessions.single().outAt)
                // Editing an existing session must refresh even though the list size is unchanged.
                sessions.value = listOf(SessionEntity(id = 1, inAt = 900, outAt = 2100))
                assertEquals(900L, emissions.receive().sessions.single().inAt)
                exceptions.value = listOf(DayExceptionEntity("2026-10-22", DayType.HALF_DAY_LEAVE, null))
                assertEquals(DayType.HALF_DAY_LEAVE, emissions.receive().exceptions.single().type)
                targets.value = listOf(WeekTargetEntity("2026-10-19", 2100))
                assertEquals(2100, emissions.receive().targets.single().targetMinutes)
                settings.value = settings.value.copy(lunchMaxCreditMinutes = 30)
                assertEquals(30, emissions.receive().settings.lunchMaxCreditMinutes)
            } finally {
                collector.cancelAndJoin()
                emissions.close()
            }
        }
    }
}
