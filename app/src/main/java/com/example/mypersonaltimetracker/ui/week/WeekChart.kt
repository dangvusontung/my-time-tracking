package com.example.mypersonaltimetracker.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.mypersonaltimetracker.ui.common.formatMinutes

/** US-28: biểu đồ cột giờ làm từng ngày trong tuần (T2..CN). */
@Composable
fun WeekChart(
    dayTotals: List<Int>,
    perDayTargetMinutes: Int?,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val valueColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val labelHeight = 20.dp.toPx()
        val valueHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - valueHeight
        val maxMinutes = maxOf(dayTotals.maxOrNull() ?: 0, perDayTargetMinutes ?: 0, 1)
        val slotWidth = size.width / 7f
        val barWidth = slotWidth * 0.55f

        fun barTop(minutes: Int): Float =
            valueHeight + chartHeight * (1f - minutes.toFloat() / maxMinutes)

        // Đường mục tiêu mỗi ngày (tuỳ chọn).
        if (perDayTargetMinutes != null && perDayTargetMinutes > 0) {
            val y = barTop(perDayTargetMinutes)
            drawLine(targetColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        val textPaint = android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 11.dp.toPx()
            isAntiAlias = true
        }

        dayTotals.forEachIndexed { i, minutes ->
            val left = slotWidth * i + (slotWidth - barWidth) / 2f
            val centerX = slotWidth * i + slotWidth / 2f
            if (minutes > 0) {
                drawRect(
                    color = barColor,
                    topLeft = Offset(left, barTop(minutes)),
                    size = Size(barWidth, size.height - labelHeight - barTop(minutes)),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    formatMinutes(minutes), centerX, barTop(minutes) - 2.dp.toPx(),
                    textPaint.apply { color = valueColor },
                )
            }
            val dayLabel = if (i == 6) "CN" else "T${i + 2}"
            drawContext.canvas.nativeCanvas.drawText(
                dayLabel, centerX, size.height - 4.dp.toPx(),
                textPaint.apply { color = labelColor },
            )
        }
    }
}
