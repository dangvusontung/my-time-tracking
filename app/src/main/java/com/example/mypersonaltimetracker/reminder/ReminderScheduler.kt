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
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.JUNGLE_LAW_DAILY_MINUTES
import com.example.mypersonaltimetracker.domain.SessionSpan
import com.example.mypersonaltimetracker.domain.WeekProgress
import com.example.mypersonaltimetracker.domain.WeekProgressCalculator
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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

    /** Chúc mừng đủ giờ tuần: Thứ Sáu lúc 16:30. */
    const val WORK_WEEK_COMPLETE = "reminder_week_complete"
    const val WEEK_COMPLETE_MINUTE = 16 * 60 + 30
    val WEEK_COMPLETE_DAY: DayOfWeek = DayOfWeek.FRIDAY

    /** Thông báo khi hôm nay đạt mục tiêu ngày / luật rừng 8h30 (đúng thời điểm chạm mốc). */
    const val WORK_DAILY_TARGET = "reminder_daily_target"

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
        wm.cancelUniqueWork(WORK_WEEK_COMPLETE)
        if (!settings.remindersEnabled) return
        ensureChannel(context)
        enqueue(wm, WORK_MORNING, KIND_MORNING, delayToNext(settings.morningReminderMin))
        enqueue(wm, WORK_EVENING, KIND_EVENING, delayToNext(settings.eveningReminderMin))
        enqueueShortfall(wm, delayToNext(SHORTFALL_CHECK_MINUTE))
        enqueueWeekComplete(wm, delayToNextWeekly(WEEK_COMPLETE_MINUTE, WEEK_COMPLETE_DAY))
        armDailyTarget(context)
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

    /** Schedules the next Friday 16:30 week-complete check (called by the worker after running). */
    fun scheduleNextWeekComplete(context: Context) {
        enqueueWeekComplete(
            WorkManager.getInstance(context),
            delayToNextWeekly(WEEK_COMPLETE_MINUTE, WEEK_COMPLETE_DAY),
        )
    }

    private fun enqueueWeekComplete(wm: WorkManager, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<WeekCompleteWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_WEEK_COMPLETE, ExistingWorkPolicy.REPLACE, request)
    }

    /** Tiến độ tuần hiện tại — dùng chung cho các worker nhắc giờ tuần. */
    suspend fun currentWeekProgress(context: Context, today: LocalDate = LocalDate.now()): WeekProgress {
        val app = App.get(context)
        val repo = app.container.repository
        val settings = app.container.settings.settings.first()
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
                settings.workdayCountStartMin, settings.workdayCountEndMin,
            )
        }
        val custom = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
        return WeekProgressCalculator.compute(
            today, weekStart, summaries, exceptionByDate, custom,
            settings.defaultWeeklyTargetMinutes, settings.workdays,
        )
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

    /** Tổng giờ hôm nay (phiên đang mở tính đến hiện tại) và trạng thái đang trong office. */
    suspend fun todayTotal(context: Context): Pair<Int, Boolean> {
        val app = App.get(context)
        val repo = app.container.repository
        val settings = app.container.settings.settings.first()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val sessions = repo.observeSessions().first()
        val exceptions = repo.observeExceptions().first()
        val summary = DaySummaryCalculator.compute(
            sessions.map { SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli)) },
            exceptions.firstOrNull { it.date == today.toString() }?.type,
            today, now,
            settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
            zone,
            settings.workdayCountStartMin, settings.workdayCountEndMin,
        )
        return summary.totalMinutes to sessions.any { it.outAt == null }
    }

    /**
     * (Re)arms the one-time worker that fires exactly when today's total reaches the next
     * milestone (mục tiêu ngày, rồi luật rừng 8h30). No-op khi không có phiên đang mở,
     * vì tổng giờ chỉ tăng khi đang trong office.
     */
    suspend fun armDailyTarget(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_DAILY_TARGET)
        val app = App.get(context)
        val settings = app.container.settings.settings.first()
        if (!settings.remindersEnabled) return
        val (total, running) = todayTotal(context)
        if (!running) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val targets = app.container.repository.observeWeekTargets().first()
        val weekStart = WeekProgressCalculator.weekStartOf(today)
        val weekly = targets.firstOrNull { it.weekStart == weekStart.toString() }?.targetMinutes
            ?: settings.defaultWeeklyTargetMinutes
        val daily = if (settings.workdays.isEmpty()) null else weekly / settings.workdays.size
        val notified = context.getSharedPreferences(DailyTargetWorker.PREFS, Context.MODE_PRIVATE)
            .getStringSet(DailyTargetWorker.KEY_NOTIFIED, emptySet()).orEmpty()
        val milestones = listOfNotNull(daily, JUNGLE_LAW_DAILY_MINUTES).distinct().sorted()
        val next = milestones.firstOrNull { "$today|$it" !in notified } ?: return

        val delayMillis = maxOf(0, next - total) * 60_000L
        val request = OneTimeWorkRequestBuilder<DailyTargetWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putInt(DailyTargetWorker.KEY_MILESTONE, next).build())
            .build()
        wm.enqueueUniqueWork(WORK_DAILY_TARGET, ExistingWorkPolicy.REPLACE, request)
    }

    /** Delay to the next [dayOfWeek] at [minuteOfDay] (next week if today's slot has passed). */
    fun delayToNextWeekly(minuteOfDay: Int, dayOfWeek: DayOfWeek, now: LocalDateTime = LocalDateTime.now()): Duration {
        val zone = ZoneId.systemDefault()
        var date = now.toLocalDate()
        while (date.dayOfWeek != dayOfWeek) date = date.plusDays(1)
        var next = date.atTime(LocalTime.ofSecondOfDay(minuteOfDay * 60L))
        if (!next.isAfter(now)) next = next.plusWeeks(1)
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
