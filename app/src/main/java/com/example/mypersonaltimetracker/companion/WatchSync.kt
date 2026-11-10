package com.example.mypersonaltimetracker.companion

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.data.SessionEntity
import com.example.mypersonaltimetracker.data.WatchReceiptEntity
import com.example.mypersonaltimetracker.domain.WeekProgressCalculator
import com.example.mypersonaltimetracker.office.OfficeStatusService
import com.example.mypersonaltimetracker.widget.updateAllWidgets
import com.google.android.gms.wearable.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.example.mypersonaltimetracker.domain.DaySummaryCalculator
import com.example.mypersonaltimetracker.domain.LunchBreakStatus
import com.example.mypersonaltimetracker.domain.SessionSpan
import java.time.YearMonth

object WatchSync {
    private val mutex = Mutex()

    suspend fun publish(context: Context) = mutex.withLock { publishUnlocked(context) }

    private suspend fun state(context: Context): WatchState {
        val c = App.get(context).container
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val settings = c.settings.settings.first()
        val weekly = c.repository.observeWeekTargets().first()
            .firstOrNull { it.weekStart == WeekProgressCalculator.weekStartOf(today).toString() }
            ?.targetMinutes ?: settings.defaultWeeklyTargetMinutes
        return WatchState(System.currentTimeMillis(), today.toString(), zone.id,
            c.repository.observeSessions().first().filter {
                it.outAt == null || Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() == today
            }.map { WatchSession(it.inAt, it.outAt) },
            c.repository.observeExceptions().first().firstOrNull { it.date == today.toString() }?.type?.name,
            weekly / settings.workdays.size.coerceAtLeast(1), settings.lunchWindowStartMin,
            settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes,
            0, 24 * 60)
    }

    private suspend fun put(context: Context, path: String, payload: String) {
        Wearable.getDataClient(context).putDataItem(PutDataMapRequest.create(path).apply {
            dataMap.putString(PAYLOAD, payload)
        }.asPutDataRequest().setUrgent()).await()
    }

    private suspend fun publishUnlocked(context: Context) {
        put(context, STATE_PATH, state(context).encode())
        publishHistory(context)
    }

    private suspend fun publishHistory(context: Context) {
        val c = App.get(context).container
        val settings = c.settings.settings.first()
        val sessions = c.repository.observeSessions().first()
        val exceptions = c.repository.observeExceptions().first().associateBy { LocalDate.parse(it.date) }
        val targets = c.repository.observeWeekTargets().first()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.ofInstant(now, zone)
        val byDay = sessions.groupBy { Instant.ofEpochMilli(it.inAt).atZone(zone).toLocalDate() }
        val dates = (byDay.keys + exceptions.keys + today).sorted()
        val existingItems = Wearable.getDataClient(context).dataItems.await()
        val existing = try { existingItems.filter { it.uri.path?.startsWith(HISTORY_PREFIX) == true }
            .associate { it.uri.path!! to (it.uri to DataMapItem.fromDataItem(it).dataMap.getString(PAYLOAD)) } }
        finally { existingItems.release() }
        val paths = mutableSetOf<String>()
        for ((month, monthDates) in dates.groupBy(YearMonth::from)) {
            val days = monthDates.map { day ->
                val rows = byDay[day].orEmpty().sortedBy { it.inAt }
                val spans = rows.map { SessionSpan(it.id, Instant.ofEpochMilli(it.inAt), it.outAt?.let(Instant::ofEpochMilli)) }
                val exception = exceptions[day]?.type
                val summary = DaySummaryCalculator.compute(spans, exception, day, now,
                    settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes, zone)
                val lunch = LunchBreakStatus.compute(spans, day, now, zone,
                    settings.lunchWindowStartMin, settings.lunchWindowEndMin, settings.lunchMaxCreditMinutes)
                WatchHistoryDay(day.toString(), rows.map { WatchSession(it.inAt, it.outAt) }, exception?.name,
                    summary.officeMinutes, summary.totalMinutes, lunch.extraMinutes)
            }
            val history = WatchHistoryMonth(month.toString(), 0, zone.id, days, settings.defaultWeeklyTargetMinutes,
                settings.workdays.map { it.value }.sorted(), targets.filter {
                    it.weekStart >= WeekProgressCalculator.weekStartOf(month.atDay(1)).toString() && it.weekStart <= month.atEndOfMonth().toString()
                }.associate { it.weekStart to it.targetMinutes })
            val path = HISTORY_PREFIX + month
            paths += path
            val previous = existing[path]?.second?.let { runCatching { WatchHistoryMonth.decode(it).copy(updatedAt = 0) }.getOrNull() }
            if (history != previous) put(context, path, history.copy(updatedAt = now.toEpochMilli()).encode())
        }
        // A restore/delete on the phone also removes obsolete cached months from the watch.
        for ((path, item) in existing) if (path !in paths) Wearable.getDataClient(context).deleteDataItems(item.first).await()
    }

    // Scan the durable Data Layer queue on startup as well as callbacks: a killed process can retry.
    suspend fun drain(context: Context) = mutex.withLock {
        val items = Wearable.getDataClient(context).dataItems.await()
        val queues = try { items.filter { it.uri.path?.startsWith(QUEUE_PREFIX) == true }
            .map { it.uri.path!! to DataMapItem.fromDataItem(it).dataMap.getString(PAYLOAD) } }
        finally { items.release() }
        for ((path, raw) in queues) {
            if (raw == null) continue
            val actions = runCatching { decodeActions(raw) }.getOrNull() ?: continue
            if (actions.size > 200 || actions.map { it.id }.distinct().size != actions.size) continue
            val db = App.get(context).container.db
            val results = JSONArray()
            db.withTransaction {
                for (action in actions) {
                    val key = path + "/" + action.id
                    val previous = db.watchReceiptDao().get(key)
                    val error = if (previous != null) previous.error else {
                        val existing = db.sessionDao().observeAll().first()
                        val result = WatchActions.apply(existing.map { WatchSession(it.inAt, it.outAt) }, action, System.currentTimeMillis())
                        if (result.error == null) {
                            if (action.start && existing.none { it.inAt == action.at }) {
                                db.sessionDao().insert(SessionEntity(inAt = action.at))
                            } else if (!action.start) {
                                existing.firstOrNull { it.inAt == action.sessionStart && it.outAt == null }?.let {
                                    db.sessionDao().update(it.copy(outAt = action.at))
                                }
                            }
                        }
                        db.watchReceiptDao().insert(WatchReceiptEntity(key, result.error))
                        result.error
                    }
                    results.put(JSONObject().put("id", action.id).put("error", error ?: JSONObject.NULL))
                }
            }
            // Commit records and receipts before acknowledging. Re-delivery is harmless after a crash.
            val snapshot = state(context).encode()
            put(context, ACK_PREFIX + path.removePrefix(QUEUE_PREFIX), JSONObject()
                .put("state", snapshot).put("results", results).toString())
        }
        publishUnlocked(context)
        updateAllWidgets(context)
        runCatching { OfficeStatusService.sync(context) }
            .onFailure { Log.w("WatchSync", "Cannot start office status from background", it) }
    }
}

class PhoneWatchListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        if (events.any { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith(QUEUE_PREFIX) == true }) {
            scheduleDrain()
        }
    }
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == REQUEST_PATH) scheduleDrain()
    }
    private fun scheduleDrain() {
        WorkManager.getInstance(this).enqueueUniqueWork("phone-watch-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<PhoneWatchWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
    }
}

class PhoneWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        WatchSync.drain(applicationContext)
        Result.success()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w("WatchSync", "Retrying phone sync", e)
        Result.retry()
    }
}
