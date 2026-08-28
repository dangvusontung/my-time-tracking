package com.example.mypersonaltimetracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

/** Re-renders every placed instance of [TimeTrackerWidget]. */
suspend fun updateAllWidgets(context: Context) {
    val widget = TimeTrackerWidget()
    GlanceAppWidgetManager(context).getGlanceIds(TimeTrackerWidget::class.java).forEach {
        widget.update(context, it)
    }
}
