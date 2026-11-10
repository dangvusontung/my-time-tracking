package com.example.mypersonaltimetracker.wear

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.mypersonaltimetracker.companion.*
import com.google.android.gms.wearable.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class WatchApp : Application() {
    val actions = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var store: WatchStore
        private set
    override fun onCreate() {
        super.onCreate()
        store = WatchStore(this)
        store.schedule()
        // Recover the tiny crash window between committing a tap and enqueueing its immediate worker.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("watch-queue-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchSyncWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf("pendingOnly" to true)).build())
    }
}

data class LocalState(
    val snapshot: WatchState?,
    val pending: List<WatchAction>,
    val error: String?,
    val connected: Boolean = false,
) {
    fun projected(now: Long): List<WatchSession> = pending.fold(snapshot?.sessions.orEmpty()) { sessions, action ->
        WatchActions.apply(sessions, action, now).sessions
    }
}

class WatchStore(private val context: Context) {
    private val syncMutex = Mutex()
    private val prefs = context.getSharedPreferences("watch_tracker", Context.MODE_PRIVATE)
    private val device = prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
        check(prefs.edit().putString("device", it).commit())
    }
    val state = MutableStateFlow(LocalState(
        prefs.getString("snapshot", null)?.let { runCatching { WatchState.decode(it) }.getOrNull() },
        prefs.getString("pending", null)?.let { decodeActions(it) }.orEmpty(), prefs.getString("error", null)))
    val history = MutableStateFlow<Map<String, WatchHistoryMonth>>(emptyMap())

    init {
        (context.applicationContext as WatchApp).actions.launch {
            val cached = prefs.all.filterKeys { it.startsWith("history_") }.values.mapNotNull {
                (it as? String)?.let { raw -> runCatching { WatchHistoryMonth.decode(raw) }.getOrNull() }
            }
            synchronized(this@WatchStore) {
                history.value = (cached + history.value.values).groupBy { it.month }
                    .mapValues { (_, versions) -> versions.maxBy { it.updatedAt } }
            }
        }
    }

    suspend fun shortcut(start: Boolean, expectedStart: Long?, requestId: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@WatchStore) {
            if (requestId.length !in 1..64 || prefs.getBoolean("shortcut_$requestId", false)) return@synchronized false
            val current = state.value
            if (current.snapshot == null || current.pending.size >= 200) return@synchronized false
            val now = System.currentTimeMillis()
            val action = WatchActions.shortcut(current.projected(now), start, expectedStart, now, requestId)
            if (action == null) {
                check(prefs.edit().putBoolean("shortcut_$requestId", true).commit())
                return@synchronized false
            }
            save(current.copy(pending = current.pending + action), requestId)
            schedule()
            true
        }
    }

    suspend fun toggle() = withContext(Dispatchers.IO) {
        synchronized(this@WatchStore) {
            val current = state.value
            if (current.snapshot == null || current.pending.size >= 200) return@synchronized
            val now = System.currentTimeMillis()
            val running = current.projected(now).firstOrNull { it.outAt == null }
            val action = WatchAction(UUID.randomUUID().toString(), now, running == null, running?.inAt ?: now)
            save(current.copy(pending = current.pending + action))
            schedule()
        }
    }

    private fun save(next: LocalState, consumedShortcut: String? = null) {
        val previous = state.value
        if (next == previous) return
        val edit = prefs.edit().putString("snapshot", next.snapshot?.encode())
            .putString("pending", encodeActions(next.pending)).putString("error", next.error)
        // Commit the receipt with the queue, so a repeated Tile refresh or process restart cannot reapply a tap.
        consumedShortcut?.let { edit.putBoolean("shortcut_$it", true) }
        check(edit.commit())
        state.value = next
        if (previous.pending != next.pending || previous.snapshot?.sessions != next.snapshot?.sessions || previous.snapshot?.date != next.snapshot?.date) {
            runCatching { refreshShortcuts(context) }.onFailure { Log.w("WatchShortcuts", "Refresh unavailable", it) }
        }
    }

    @Synchronized
    fun accept(path: String, raw: String) {
        val old = state.value
        if (path.startsWith(HISTORY_PREFIX)) {
            val month = WatchHistoryMonth.decode(raw)
            if (path != HISTORY_PREFIX + month.month || month.updatedAt < (history.value[month.month]?.updatedAt ?: 0)) return
            if (history.value[month.month] == month) return
            check(prefs.edit().putString("history_${month.month}", raw).commit())
            history.value = history.value + (month.month to month)
        } else if (path == STATE_PATH) {
            val snapshot = WatchState.decode(raw)
            if (snapshot.updatedAt >= (old.snapshot?.updatedAt ?: 0)) save(old.copy(snapshot = snapshot))
        } else if (path == ACK_PREFIX + device) {
            val ack = JSONObject(raw)
            val snapshot = WatchState.decode(ack.getString("state"))
            val results = ack.getJSONArray("results")
            val ids = mutableSetOf<String>()
            var error = old.error
            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                val id = result.getString("id")
                ids += id
                if (!result.isNull("error") && old.pending.any { it.id == id }) {
                    val failed = old.pending.first { it.id == id }
                    // Keep rejected actions on disk for diagnosis; never silently discard their timestamps.
                    check(prefs.edit().putString("failed_$id", encodeActions(listOf(failed))).commit())
                    val timestamp = java.time.Instant.ofEpochMilli(failed.at)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                    error = "${if (failed.start) "Vào làm" else "Ra ngoài"} $timestamp: ${result.getString("error")}"
                }
            }
            save(old.copy(snapshot = if (snapshot.updatedAt >= (old.snapshot?.updatedAt ?: 0)) snapshot else old.snapshot,
                pending = old.pending.filterNot { it.id in ids }, error = error))
        }
    }

    @Synchronized
    fun removeHistory(path: String) {
        if (!path.startsWith(HISTORY_PREFIX)) return
        val month = path.removePrefix(HISTORY_PREFIX)
        check(prefs.edit().remove("history_$month").commit())
        history.value = history.value - month
    }

    fun schedule() {
        WorkManager.getInstance(context).enqueueUniqueWork("watch-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WatchSyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val client = Wearable.getDataClient(context)
            val items = client.dataItems.await()
            try {
                // Acknowledgements contain a snapshot and must be consumed even if no UI was alive on arrival.
                for (item in items) {
                    val path = item.uri.path ?: continue
                    if (path == STATE_PATH || path == ACK_PREFIX + device || path.startsWith(HISTORY_PREFIX)) {
                        DataMapItem.fromDataItem(item).dataMap.getString(PAYLOAD)?.let { accept(path, it) }
                    }
                }
            } finally { items.release() }
            val pending = state.value.pending
            client.putDataItem(PutDataMapRequest.create(QUEUE_PREFIX + device).apply {
                dataMap.putString(PAYLOAD, encodeActions(pending))
            }.asPutDataRequest().setUrgent()).await()
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            synchronized(this@WatchStore) { state.value = state.value.copy(connected = nodes.isNotEmpty()) }
            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(node.id, REQUEST_PATH, byteArrayOf()).await()
            }
        }
    }
}

class WatchSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val store = (applicationContext as WatchApp).store
        if (!inputData.getBoolean("pendingOnly", false) || store.state.value.pending.isNotEmpty()) store.sync()
        Result.success()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w("WatchSync", "Will retry durable queue", e)
        Result.retry()
    }
}

class WatchListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val store = (application as WatchApp).store
        for (event in events) {
            val item = event.dataItem
            val path = item.uri.path ?: continue
            if (event.type == DataEvent.TYPE_DELETED && path.startsWith(HISTORY_PREFIX)) {
                store.removeHistory(path)
            } else if (event.type == DataEvent.TYPE_CHANGED && (path == STATE_PATH || path.startsWith(ACK_PREFIX) || path.startsWith(HISTORY_PREFIX))) {
                runCatching { DataMapItem.fromDataItem(item).dataMap.getString(PAYLOAD)?.let { store.accept(path, it) } }
                    .onFailure { Log.w("WatchSync", "Invalid snapshot", it) }
            }
        }
    }
}
