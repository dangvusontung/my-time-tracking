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

/** Manual DI container shared by the app, the Glance widget and WorkManager workers. */
class AppContainer(context: Context) {
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()
    val repository: TimeRepository = TimeRepository(db)
    val settings: SettingsRepository = SettingsRepository(context)
}

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Recover the "Đang trong office" notification if the app launches while a session is open.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { OfficeStatusService.sync(this@App) }
    }

    companion object {
        fun get(context: Context): App = context.applicationContext as App
    }
}
