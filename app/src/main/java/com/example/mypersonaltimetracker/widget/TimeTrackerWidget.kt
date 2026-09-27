package com.example.mypersonaltimetracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.MainActivity
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.domain.WeekProgressCalculator
import com.example.mypersonaltimetracker.office.OfficeStatusService
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import com.example.mypersonaltimetracker.ui.common.formatTime
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
        val targets = app.container.repository.observeWeekTargets().first()

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
            settings.workdayCountStartMin, settings.workdayCountEndMin,
        )

        val weekStart = WeekProgressCalculator.weekStartOf(today)
        val weeklyTarget = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
            ?: settings.defaultWeeklyTargetMinutes
        val dailyTarget = if (settings.workdays.isEmpty()) null else weeklyTarget / settings.workdays.size

        val statusText = if (running != null) {
            "Đang trong office · vào lúc ${formatTime(Instant.ofEpochMilli(running.inAt), zone)}"
        } else {
            "Ngoài office"
        }
        val bigText = if (running != null) {
            formatMinutes(((now.toEpochMilli() - running.inAt) / 60000).toInt())
        } else {
            formatMinutes(summary.totalMinutes)
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    statusText = statusText,
                    bigText = bigText,
                    totalMinutes = summary.totalMinutes,
                    dailyTargetMinutes = dailyTarget,
                    isRunning = running != null,
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        statusText: String,
        bigText: String,
        totalMinutes: Int,
        dailyTargetMinutes: Int?,
        isRunning: Boolean,
    ) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.background)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusText,
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = bigText,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (dailyTargetMinutes != null && dailyTargetMinutes > 0) {
                val ratio = totalMinutes.toFloat() / dailyTargetMinutes
                Spacer(GlanceModifier.height(8.dp))
                LinearProgressIndicator(
                    progress = ratio.coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth(),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = "Hôm nay: ${formatMinutes(totalMinutes)} / ${formatMinutes(dailyTargetMinutes)}" +
                        " (${(ratio * 100).toInt()}%)",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Button(
                text = if (isRunning) "Ra office" else "Vào office",
                onClick = actionRunCallback<ToggleAction>(),
                modifier = GlanceModifier.fillMaxWidth(),
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
