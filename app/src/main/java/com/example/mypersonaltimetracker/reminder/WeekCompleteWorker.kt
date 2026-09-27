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
import com.example.mypersonaltimetracker.domain.WeekProgressCalculator
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Chạy Thứ Sáu 16:30: nếu tuần đã đủ mục tiêu giờ thì thông báo mừng
 * (tối đa một lần mỗi tuần, ghi nhớ bằng SharedPreferences).
 */
class WeekCompleteWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = App.get(applicationContext).container.settings.settings.first()
        if (settings.remindersEnabled) {
            maybeNotify()
            ReminderScheduler.scheduleNextWeekComplete(applicationContext)
        }
        return Result.success()
    }

    private suspend fun maybeNotify() {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val weekStart = WeekProgressCalculator.weekStartOf(today)
        if (prefs.getString(KEY_LAST_NOTIFIED, null) == weekStart.toString()) return

        val progress = ReminderScheduler.currentWeekProgress(applicationContext, today)
        if (progress.weekTotalMinutes < progress.targetMinutes) return

        notify(
            "Đã đủ ${formatMinutes(progress.targetMinutes)} tuần này" +
                " (tổng ${formatMinutes(progress.weekTotalMinutes)}) — nghỉ ngơi thôi!",
        )
        prefs.edit().putString(KEY_LAST_NOTIFIED, weekStart.toString()).apply()
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
            .setContentTitle("Đủ giờ tuần này!")
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notification)
    }

    companion object {
        private const val PREFS = "week_complete"
        private const val KEY_LAST_NOTIFIED = "last_notified_week"
        private const val NOTIF_ID = 4
    }
}
