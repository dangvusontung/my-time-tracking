package com.example.mypersonaltimetracker.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.MainActivity
import com.example.mypersonaltimetracker.R
import com.example.mypersonaltimetracker.data.AppSettings
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    const val CHANNEL_ID = "daily_reminders"
    const val KIND_MORNING = "morning"
    const val KIND_EVENING = "evening"
    private const val WORK_MORNING = "reminder_morning"
    private const val WORK_EVENING = "reminder_evening"

    /** US-24: kiểm tra thiếu giờ tuần mỗi ngày lúc 09:00. */
    const val WORK_WEEKLY_SHORTFALL = "reminder_weekly_shortfall"
    const val SHORTFALL_CHECK_MINUTE = 9 * 60

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nhắc vào/ra office", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    /** Schedules both daily reminders as one-time requests that re-schedule themselves,
     *  so the fire times always follow the current settings. */
    suspend fun reschedule(context: Context) {
        val settings = App.get(context).container.settings.settings.first()
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_MORNING)
        wm.cancelUniqueWork(WORK_EVENING)
        wm.cancelUniqueWork(WORK_WEEKLY_SHORTFALL)
        if (!settings.remindersEnabled) return
        ensureChannel(context)
        enqueue(wm, WORK_MORNING, KIND_MORNING, delayToNext(settings.morningReminderMin))
        enqueue(wm, WORK_EVENING, KIND_EVENING, delayToNext(settings.eveningReminderMin))
        enqueueShortfall(wm, delayToNext(SHORTFALL_CHECK_MINUTE))
    }

    /** Schedules the next occurrence of a single kind (called by the worker after firing). */
    fun scheduleNext(context: Context, kind: String, minuteOfDay: Int) {
        val wm = WorkManager.getInstance(context)
        val name = if (kind == KIND_MORNING) WORK_MORNING else WORK_EVENING
        enqueue(wm, name, kind, delayToNext(minuteOfDay))
    }

    /** Schedules the next daily shortfall check (called by the worker after running). */
    fun scheduleNextShortfall(context: Context) {
        enqueueShortfall(WorkManager.getInstance(context), delayToNext(SHORTFALL_CHECK_MINUTE))
    }

    private fun enqueueShortfall(wm: WorkManager, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<ShortfallWarningWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_WEEKLY_SHORTFALL, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueue(wm: WorkManager, name: String, kind: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(ReminderWorker.KEY_KIND, kind).build())
            .build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    fun delayToNext(minuteOfDay: Int, now: LocalDateTime = LocalDateTime.now()): Duration {
        val zone = ZoneId.systemDefault()
        var next = now.toLocalDate().atTime(LocalTime.ofSecondOfDay(minuteOfDay * 60L))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now.atZone(zone).toInstant(), next.atZone(zone).toInstant())
    }
}

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_KIND = "kind"
    }

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: ReminderScheduler.KIND_MORNING
        val settings = App.get(applicationContext).container.settings.settings.first()
        if (settings.remindersEnabled) {
            notify(kind)
            // Re-schedule the next occurrence following current settings.
            val minute = if (kind == ReminderScheduler.KIND_MORNING) {
                settings.morningReminderMin
            } else {
                settings.eveningReminderMin
            }
            ReminderScheduler.scheduleNext(applicationContext, kind, minute)
        }
        return Result.success()
    }

    private fun notify(kind: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ReminderScheduler.ensureChannel(applicationContext)
        val text = if (kind == ReminderScheduler.KIND_MORNING) {
            "Đừng quên bấm vào office"
        } else {
            "Đừng quên bấm ra office"
        }
        val intent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time Tracker")
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        val id = if (kind == ReminderScheduler.KIND_MORNING) 1 else 2
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}
