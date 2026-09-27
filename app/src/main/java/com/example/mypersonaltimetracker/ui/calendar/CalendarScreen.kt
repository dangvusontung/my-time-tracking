package com.example.mypersonaltimetracker.ui.calendar

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mypersonaltimetracker.domain.DayType
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Lưới lịch theo tháng: mỗi ô là một ngày với tổng giờ, chạm để xem chi tiết. */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val today = LocalDate.now()

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Điều hướng tháng.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Tháng trước")
            }
            Text(
                "Tháng ${month.monthValue}/${month.year}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Tháng sau")
            }
        }

        // Hàng tiêu đề thứ trong tuần (bắt đầu từ Thứ Hai).
        Row(Modifier.fillMaxWidth()) {
            listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEachIndexed { i, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i >= 5) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Lưới các ngày.
        val firstOffset = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
        val cellCount = firstOffset + month.lengthOfMonth()
        val weeks = (cellCount + 6) / 7
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0..6) {
                    val dayNumber = week * 7 + col - firstOffset + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Box(Modifier.weight(1f).height(64.dp))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            day = state.days[date],
                            isToday = date == today,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: CalendarDay?,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val worked = day != null && day.totalMinutes > 0
    val background = when {
        day?.exception != null -> MaterialTheme.colorScheme.tertiaryContainer
        worked -> {
            // Đậm dần theo số giờ, chuẩn hoá theo ngày 8h.
            val intensity = (day.totalMinutes / 480f).coerceIn(0f, 1f)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + 0.65f * intensity)
        }
        else -> Color.Transparent
    }
    val contentColor = when {
        day?.exception != null -> MaterialTheme.colorScheme.onTertiaryContainer
        worked -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(64.dp)
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                else Modifier,
            ),
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Column(
            Modifier.fillMaxSize().clickable(onClick = onClick).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
            if (worked) {
                Text(
                    formatMinutes(day.totalMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            } else if (day?.exception != null) {
                Text(
                    exceptionShortLabel(day.exception),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
        }
    }
}

private fun exceptionShortLabel(type: DayType): String = when (type) {
    DayType.ANNUAL_LEAVE -> "Phép"
    DayType.HALF_DAY_LEAVE -> "Nửa ngày"
    DayType.PUBLIC_HOLIDAY -> "Lễ"
    DayType.WFH -> "WFH"
    DayType.UNPAID_LEAVE -> "KL"
}
