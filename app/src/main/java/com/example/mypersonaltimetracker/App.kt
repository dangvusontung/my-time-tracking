package com.example.mypersonaltimetracker

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.mypersonaltimetracker.data.AppDatabase
import com.example.mypersonaltimetracker.data.SettingsRepository
import com.example.mypersonaltimetracker.data.TimeRepository
import com.example.mypersonaltimetracker.office.OfficeStatusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mypersonaltimetracker.companion.WatchSync
import com.example.mypersonaltimetracker.widget.observeWidgetInputs
import com.example.mypersonaltimetracker.widget.updateAllWidgets
import kotlinx.coroutines.CancellationException
import android.util.Log

/** Manual DI container shared by the app, the Glance widget and WorkManager workers. */
class AppContainer(context: Context) {
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
        .addMigrations(object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS watch_receipts (id TEXT NOT NULL PRIMARY KEY, error TEXT)")
            }
        }).build()
    val repository: TimeRepository = TimeRepository(db)
    val settings: SettingsRepository = SettingsRepository(context)
}

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Recover the "Đang ở văn phòng" notification if the app launches while a session is open.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { OfficeStatusService.sync(this@App) }
                .onFailure { Log.w("OfficeStatus", "Cannot restore notification from background", it) }
        }
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val inputs = observeWidgetInputs(container.repository.observeSessions(), container.repository.observeExceptions(),
            container.repository.observeWeekTargets(), container.settings.settings)
        watchScope.launch {
            inputs.collect {
                runCatching { updateAllWidgets(this@App) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        Log.w("WidgetUpdate", "Cannot refresh home screen widgets", it)
                    }
            }
        }
        watchScope.launch {
            runCatching { WatchSync.drain(this@App) }
                .onFailure { Log.w("WatchSync", "Initial watch sync unavailable", it) }
        }
        watchScope.launch {
            inputs.collect {
                runCatching { WatchSync.publish(this@App) }
                    .onFailure { Log.w("WatchSync", "Watch snapshot unavailable", it) }
            }
        }
    }

    companion object {
        fun get(context: Context): App = context.applicationContext as App
    }
}
