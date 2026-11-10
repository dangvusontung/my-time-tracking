package com.example.mypersonaltimetracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.mypersonaltimetracker.domain.*
import java.time.*

private val OfficeGreen = Color(0xFF50D890)
private val OutsideRed = Color(0xFFFF6B6B)
private val LunchYellow = Color(0xFFFFD166)

@Composable
fun TimelineLine(segments: List<OfficeSegment>, start: Float = 420f, end: Float = 1200f, labels: Boolean = false) {
    fun clock(minute: Float): String = "%02d:%02d".format(minute.toInt() / 60, minute.toInt() % 60)
    val description = segments.joinToString("; ") {
        "${clock(it.start)}–${clock(it.end)}: ${when (it.kind) { OfficeKind.OFFICE -> "ở văn phòng"; OfficeKind.OUTSIDE -> "ra ngoài"; OfficeKind.LUNCH -> "ăn trưa" }}"
    }.ifEmpty { "Chưa có phiên vào–ra" }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawLine(Color.DarkGray, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
            segments.forEach {
                val left = ((it.start - start) / (end - start)).coerceIn(0f, 1f) * size.width
                val right = ((it.end - start) / (end - start)).coerceIn(0f, 1f) * size.width
                if (right > left) drawLine(when (it.kind) { OfficeKind.OFFICE -> OfficeGreen; OfficeKind.OUTSIDE -> OutsideRed; OfficeKind.LUNCH -> LunchYellow },
                    Offset(left, size.height / 2), Offset(right, size.height / 2), size.height)
            }
        }
        if (labels) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(start), style = MaterialTheme.typography.labelSmall)
            Text(clock(end), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun TimelineLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text("● Văn phòng", color = OfficeGreen, style = MaterialTheme.typography.labelSmall)
        Text("● Ra ngoài", color = OutsideRed, style = MaterialTheme.typography.labelSmall)
        Text("● Nghỉ trưa", color = LunchYellow, style = MaterialTheme.typography.labelSmall)
    }
}

fun timelineBounds(segments: List<OfficeSegment>): Pair<Float, Float> =
    minOf(420f, kotlin.math.floor((segments.minOfOrNull { it.start } ?: 420f) / 60) * 60) to
        maxOf(1200f, kotlin.math.ceil((segments.maxOfOrNull { it.end } ?: 1200f) / 60) * 60)
