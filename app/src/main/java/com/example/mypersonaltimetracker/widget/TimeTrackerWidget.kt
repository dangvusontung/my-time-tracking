package com.example.mypersonaltimetracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
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
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 150.dp), DpSize(300.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = App.get(context)
        val repo = app.container.repository
        val inputs = observeWidgetInputs(repo.observeSessions(), repo.observeExceptions(),
            repo.observeWeekTargets(), app.container.settings.settings)
        val initial = inputs.first()
        provideContent {
            // update() can reuse an active Glance composition. Observe data inside it too.
            val current by inputs.collectAsState(initial)
            GlanceTheme { WidgetContent(current) }
        }
    }

    @Composable
    private fun WidgetContent(inputs: WidgetInputs) {
        val (sessions, exceptions, targets, settings) = inputs

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

        val weekStart = WeekProgressCalculator.weekStartOf(today)
        val weeklyTarget = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
            ?: settings.defaultWeeklyTargetMinutes
        val dailyTarget = if (settings.workdays.isEmpty()) null else weeklyTarget / settings.workdays.size

        val statusText = if (running != null) {
            "Đang làm · ${formatTime(Instant.ofEpochMilli(running.inAt), zone)}"
        } else {
            "Ngoài văn phòng"
        }

        WidgetLayout(
            statusText = statusText,
            totalMinutes = summary.totalMinutes,
            dailyTargetMinutes = dailyTarget,
            isRunning = running != null,
        )
    }

    @Composable
    private fun WidgetLayout(
        statusText: String,
        totalMinutes: Int,
        dailyTargetMinutes: Int?,
        isRunning: Boolean,
    ) {
        val context = LocalContext.current
        val large = LocalSize.current.width >= 250.dp
        val ringSize = if (large) 96.dp else 64.dp
        val target = dailyTargetMinutes ?: 0
        val ratio = if (target > 0) totalMinutes.toFloat() / target else 0f
        val ring = remember(totalMinutes, target) { widgetRingBitmap(totalMinutes, target) }
        Row(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp)
                .background(GlanceTheme.colors.background)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(ringSize), contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(ring), contentDescription = "Tiến độ hôm nay: ${(ratio * 100).toInt()}%",
                    modifier = GlanceModifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatMinutes(totalMinutes), style = TextStyle(color = GlanceTheme.colors.onBackground,
                        fontSize = if (large) 22.sp else 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                    Text(if (target > 0) "${(ratio * 100).toInt()}%" else "Hôm nay",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(if (large) 12.dp else 8.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = statusText, style = TextStyle(color = GlanceTheme.colors.onBackground,
                    fontSize = if (large) 13.sp else 11.sp), maxLines = 2)
                if (target > 0) {
                    Text("Mục tiêu ${formatMinutes(target)}", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp), maxLines = 1)
                }
                if (large) Spacer(GlanceModifier.height(6.dp))
                Button(text = if (isRunning) "Ra ngoài" else "Vào làm", onClick = actionRunCallback<ToggleAction>(),
                    modifier = GlanceModifier.fillMaxWidth())
            }
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
        updateAllWidgets(context)
        OfficeStatusService.sync(context)
    }
}

class TimeTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeTrackerWidget()
}
