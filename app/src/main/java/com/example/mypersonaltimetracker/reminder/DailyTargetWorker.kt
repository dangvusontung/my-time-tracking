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
import com.example.mypersonaltimetracker.domain.JUNGLE_LAW_DAILY_MINUTES
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Fires at the moment today's total crosses a milestone (mục tiêu ngày 8h,
 * luật rừng 8h30). Scheduled precisely by [ReminderScheduler.armDailyTarget];
 * each milestone notifies at most once per day.
 */
class DailyTargetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = App.get(applicationContext).container.settings.settings.first()
        if (settings.remindersEnabled) {
            val milestone = inputData.getInt(KEY_MILESTONE, -1)
            if (milestone > 0) {
                val (total, _) = ReminderScheduler.todayTotal(applicationContext)
                val today = LocalDate.now().toString()
                val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
                if (total >= milestone && "$today|$milestone" !in notified) {
                    notifyMilestone(milestone, total)
                    prefs.edit().putStringSet(KEY_NOTIFIED, notified + "$today|$milestone").apply()
                }
            }
            // Arm the next milestone (e.g. luật rừng sau mục tiêu chính thức).
            ReminderScheduler.armDailyTarget(applicationContext)
        }
        return Result.success()
    }

    private fun notifyMilestone(milestone: Int, total: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ReminderScheduler.ensureChannel(applicationContext)
        val jungle = milestone == JUNGLE_LAW_DAILY_MINUTES
        val title = if (jungle) "Đạt luật rừng 8h30!" else "Đủ giờ hôm nay!"
        val text = if (jungle) {
            "8h30 rồi (tổng ${formatMinutes(total)}) — về thôi!"
        } else {
            "Đã đủ ${formatMinutes(milestone)} hôm nay — luật rừng 8h30 còn" +
                " ${formatMinutes(JUNGLE_LAW_DAILY_MINUTES - milestone)} nữa"
        }
        val intent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        val id = if (jungle) NOTIF_ID_JUNGLE else NOTIF_ID_TARGET
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    companion object {
        const val PREFS = "daily_target"
        const val KEY_NOTIFIED = "notified_milestones"
        const val KEY_MILESTONE = "milestone_minutes"
        private const val NOTIF_ID_TARGET = 5
        private const val NOTIF_ID_JUNGLE = 6
    }
}
