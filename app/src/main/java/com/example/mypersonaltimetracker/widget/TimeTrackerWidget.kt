package com.example.mypersonaltimetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.office.OfficeStatusService
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimeTrackerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = App.get(context)
        val settings = app.container.settings.settings.first()
        val sessions = app.container.repository.observeSessions().first()
        val exceptions = app.container.repository.observeExceptions().first()

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val running = sessions.firstOrNull { it.outAt == null }
        val summary = DaySummaryCalculator.compute(
            sessions.map { SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli)) },
            exceptions.firstOrNull { it.date == today.toString() }?.type,
            today, now,
            settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
            zone,
        )
        val statusText = if (running != null) {
            val elapsedMin = ((now.toEpochMilli() - running.inAt) / 60000).toInt()
            "Đang trong office · ${formatMinutes(elapsedMin)}"
        } else {
            "Ngoài office · hôm nay ${formatMinutes(summary.totalMinutes)}"
        }

        provideContent {
            GlanceTheme {
                WidgetContent(statusText, running != null)
            }
        }
    }

    @Composable
    private fun WidgetContent(statusText: String, isRunning: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = statusText,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            Button(
                text = if (isRunning) "Ra office" else "Vào office",
                onClick = actionRunCallback<ToggleAction>(),
            )
        }
    }
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        App.get(context).container.repository.toggle(Instant.now())
        OfficeStatusService.sync(context)
        TimeTrackerWidget().update(context, glanceId)
    }
}

class TimeTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeTrackerWidget()
}
