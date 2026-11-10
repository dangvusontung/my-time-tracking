package com.example.mypersonaltimetracker.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun GoalRing(total: Int, target: Int, label: String) {
    val fraction = if (target > 0) total.toFloat() / target else 0f
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val ringColor = if (target > 0 && total >= target) androidx.compose.ui.graphics.Color(0xFF16834A)
            else androidx.compose.ui.graphics.Color(0xFF50D890)
        val track = MaterialTheme.colorScheme.outline
        androidx.compose.foundation.Canvas(Modifier.size(190.dp)) {
            val stroke = 10.dp.toPx()
            drawCircle(track, radius = size.minDimension / 2 - stroke / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            if (fraction > 0f) drawArc(ringColor, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
        Column(Modifier.width(164.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(formatMinutes(total), style = MaterialTheme.typography.headlineSmall)
            Text(if (target > 0) "/ ${formatMinutes(target)} · ${(fraction * 100).toInt()}%" else "Chưa đặt mục tiêu",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
