package com.example.mypersonaltimetracker.widget

import com.example.mypersonaltimetracker.data.AppSettings
import com.example.mypersonaltimetracker.data.DayExceptionEntity
import com.example.mypersonaltimetracker.data.SessionEntity
import com.example.mypersonaltimetracker.data.WeekTargetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class WidgetInputs(
    val sessions: List<SessionEntity>,
    val exceptions: List<DayExceptionEntity>,
    val targets: List<WeekTargetEntity>,
    val settings: AppSettings,
)

/** The same live inputs drive widget composition and refresh requests from any mutation source. */
fun observeWidgetInputs(
    sessions: Flow<List<SessionEntity>>,
    exceptions: Flow<List<DayExceptionEntity>>,
    targets: Flow<List<WeekTargetEntity>>,
    settings: Flow<AppSettings>,
): Flow<WidgetInputs> = combine(sessions, exceptions, targets, settings, ::WidgetInputs)
    .distinctUntilChanged()
