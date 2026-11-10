package com.example.mypersonaltimetracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** A transparent RemoteViews-compatible ring; its text remains native Glance text. */
fun widgetRingBitmap(total: Int, target: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
    }
    val bounds = RectF(12f, 12f, 244f, 244f)
    paint.color = Color.rgb(119, 125, 132)
    canvas.drawOval(bounds, paint)
    if (target > 0 && total > 0) {
        paint.color = if (total >= target) Color.rgb(22, 131, 74) else Color.rgb(80, 216, 144)
        canvas.drawArc(bounds, -90f, 360f * (total.toFloat() / target).coerceIn(0f, 1f), false, paint)
    }
    return bitmap
}
