package com.example.mypersonaltimetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Re-arms the daily reminders after a reboot (WorkManager schedules survive
 *  reboots for periodic work, but these are one-time self-rescheduling requests). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ReminderScheduler.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
