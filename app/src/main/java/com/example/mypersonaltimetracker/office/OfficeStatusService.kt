package com.example.mypersonaltimetracker.office

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.MainActivity
import com.example.mypersonaltimetracker.R
import com.example.mypersonaltimetracker.ui.common.formatTime
import com.example.mypersonaltimetracker.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Shows an ongoing "Đang trong office" notification while a session is open.
 * The service observes the open-session flow and stops itself once no open
 * session remains, so callers only ever need to [sync] (start) it.
 */
class OfficeStatusService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        scope.launch {
            App.get(this@OfficeStatusService).container.repository.observeOpenSessions().collect { open ->
                if (open.isEmpty()) {
                    stopSelf()
                } else {
                    updateNotification(open.minBy { it.inAt }.inAt)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOCK_OUT) {
            scope.launch {
                App.get(applicationContext).container.repository.clockOut(Instant.now())
                updateAllWidgets(applicationContext)
                // observeOpenSessions() emits empty -> self-stop.
            }
        } else if (!foregroundStarted) {
            // startForegroundService() requires a prompt startForeground() call;
            // the open-session flow above replaces this with the real start time.
            updateNotification(System.currentTimeMillis())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(inAtMillis: Long) {
        val notification = buildNotification(inAtMillis)
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
            foregroundStarted = true
        } else if (Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(inAtMillis: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val clockOutIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OfficeStatusService::class.java).setAction(ACTION_CLOCK_OUT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val since = formatTime(Instant.ofEpochMilli(inAtMillis), ZoneId.systemDefault())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Đang trong office")
            .setContentText("Từ $since")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Ra office", clockOutIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Trạng thái trong office", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val ACTION_CLOCK_OUT = "com.example.mypersonaltimetracker.action.CLOCK_OUT"
        private const val CHANNEL_ID = "office_status"
        private const val NOTIF_ID = 100

        /** Starts the service if a session is currently open; when the last open session
         *  closes the service stops itself, so callers never need to stop it explicitly. */
        suspend fun sync(context: Context) {
            val open = App.get(context).container.repository.observeOpenSessions().first()
            if (open.isNotEmpty()) {
                ContextCompat.startForegroundService(context, Intent(context, OfficeStatusService::class.java))
            }
        }
    }
}
