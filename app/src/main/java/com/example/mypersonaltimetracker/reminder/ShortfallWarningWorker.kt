package com.example.mypersonaltimetracker.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.MainActivity
import com.example.mypersonaltimetracker.R
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.domain.ShortfallWarning
import com.example.mypersonaltimetracker.domain.WeekProgressCalculator
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * US-24: chạy mỗi ngày một lần; nếu tuần đang thiếu giờ và số giờ cần bù mỗi ngày
 * vượt quá 8h thì thông báo (tối đa một lần mỗi ngày, ghi nhớ bằng SharedPreferences).
 */
class ShortfallWarningWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = App.get(applicationContext).container.settings.settings.first()
        if (settings.remindersEnabled) {
            maybeWarn()
            ReminderScheduler.scheduleNextShortfall(applicationContext)
        }
        return Result.success()
    }

    private suspend fun maybeWarn() {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        if (prefs.getString(KEY_LAST_WARNED, null) == today.toString()) return

        val repo = App.get(applicationContext).container.repository
        val settings = App.get(applicationContext).container.settings.settings.first()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()

        val sessions = repo.observeSessions().first()
        val exceptions = repo.observeExceptions().first()
        val targets = repo.observeWeekTargets().first()

        val weekStart = WeekProgressCalculator.weekStartOf(today)
        val spans = sessions.map {
            SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli))
        }
        val exceptionByDate = exceptions.associateBy({ LocalDate.parse(it.date) }, { it.type })
        val summaries = (0..6).associate { i ->
            val d = weekStart.plusDays(i.toLong())
            d to DaySummaryCalculator.compute(
                spans, exceptionByDate[d], d, now,
                settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
                zone,
            )
        }
        val custom = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
        val progress = WeekProgressCalculator.compute(
            today, weekStart, summaries, exceptionByDate, custom,
            settings.defaultWeeklyTargetMinutes, settings.workdays,
        )

        if (!ShortfallWarning.shouldWarn(today, progress)) return

        val hours = progress.remainingMinutes / 60
        val minutes = progress.remainingMinutes % 60
        val missing = if (minutes > 0) "${hours}h${minutes.toString().padStart(2, '0')}" else "${hours}h"
        val days = if (progress.daysLeft % 1.0 == 0.0) {
            progress.daysLeft.toInt().toString()
        } else {
            progress.daysLeft.toString()
        }
        notify("Tuần này còn thiếu $missing — còn $days ngày để bù")
        prefs.edit().putString(KEY_LAST_WARNED, today.toString()).apply()
    }

    private fun notify(text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ReminderScheduler.ensureChannel(applicationContext)
        val intent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Cảnh báo thiếu giờ tuần")
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notification)
    }

    companion object {
        private const val PREFS = "shortfall_warning"
        private const val KEY_LAST_WARNED = "last_warned_date"
        private const val NOTIF_ID = 3
    }
}
