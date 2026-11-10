package com.example.mypersonaltimetracker.wear

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek

@Composable
private fun HistoryList(title: String, onBack: () -> Unit, content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
            contentPadding = PaddingValues(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null) {
            item { Text(title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center) }
            content()
            item { Chip(onClick = onBack, label = { Text("Quay lại") }, modifier = Modifier.fillMaxWidth(), colors = ChipDefaults.secondaryChipColors()) }
        }
    }
}

@Composable
fun WeekContent(history: HistoryUi, onDay: (String) -> Unit, onBack: () -> Unit) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val monday = LocalDate.parse(history.today).with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
    val dates = (0..6).map { monday.plusDays(it.toLong()) }
    val total = dates.sumOf { history.days[it.toString()]?.creditedMinutes ?: 0 }
    val target = history.targets[monday.toString()] ?: history.weeklyTarget
    val segments = dates.flatMap { history.days[it.toString()]?.timeline.orEmpty() }
    val (start, end) = timelineBounds(segments)
    HistoryList(if (offset == 0) "Tuần này" else "Tuần ${monday.dayOfMonth}/${monday.monthValue}", onBack) {
        item { Text("${watchMinutes(total)} / ${watchMinutes(target)}", style = MaterialTheme.typography.title2, textAlign = TextAlign.Center) }
        item { Text("Còn ${watchMinutes((target - total).coerceAtLeast(0))}", style = MaterialTheme.typography.caption1) }
        if (offset == 0) history.fridayLeave?.let { leave -> item {
            Text("Đủ 40h tuần: $leave · ≥16:30", style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
        } }
        item { TimelineLegend() }
        if (!history.synced) item { Text("Đang chờ đồng bộ lịch sử từ điện thoại", textAlign = TextAlign.Center) }
        if (history.pending) item { Text("Có thao tác đang chờ đồng bộ", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
        for (date in dates) item {
            val day = history.days[date.toString()]
            Chip(onClick = { onDay(date.toString()) }, label = { Text(day?.label ?: "${date.dayOfMonth}/${date.monthValue}") },
                secondaryLabel = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(watchMinutes(day?.creditedMinutes ?: 0))
                    TimelineLine(day?.timeline.orEmpty(), start, end)
                } }, modifier = Modifier.fillMaxWidth().height(68.dp),
                colors = if (date.toString() == history.today) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors())
        }
        item { Chip(onClick = { offset-- }, enabled = history.months.any { it <= YearMonth.from(monday.minusDays(1)).toString() },
            label = { Text("Tuần trước") }, modifier = Modifier.fillMaxWidth()) }
        if (offset < 0) item { Chip(onClick = { offset++ }, label = { Text("Tuần sau") }, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
fun HistoryContent(history: HistoryUi, onDay: (String) -> Unit, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val month = selected?.takeIf { it in history.months } ?: history.months.firstOrNull() ?: history.today.take(7)
    val index = history.months.indexOf(month)
    val days = history.days.values.filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    val (axisStart, axisEnd) = timelineBounds(days.flatMap { it.timeline })
    HistoryList("Tháng ${month.takeLast(2)}/${month.take(4)}", onBack) {
        history.monthStats[month]?.let { stats ->
            item { Text("TB ${watchMinutes(stats.average)}/ngày", style = MaterialTheme.typography.title2, textAlign = TextAlign.Center) }
            stats.averageSpan?.let { span -> item { Text("TB vào đầu → ra cuối: ${watchMinutes(span)}", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) } }
            item { Text("${stats.days} ngày có giờ · Tổng ${watchMinutes(stats.total)}", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
        }
        if (index >= 0 && index + 1 < history.months.size) item {
            Chip(onClick = { selected = history.months[index + 1] }, label = { Text("Tháng trước") }, modifier = Modifier.fillMaxWidth())
        }
        if (index > 0) item {
            Chip(onClick = { selected = history.months[index - 1] }, label = { Text("Tháng sau") }, modifier = Modifier.fillMaxWidth())
        }
        if (!history.synced) item { Text("Mở ứng dụng trên điện thoại để đồng bộ lịch sử", textAlign = TextAlign.Center) }
        item { TimelineLegend() }
        for (day in days) item {
            Chip(onClick = { onDay(day.date) }, label = { Text(day.label) },
                secondaryLabel = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(watchMinutes(day.creditedMinutes))
                    TimelineLine(day.timeline, axisStart, axisEnd)
                } }, modifier = Modifier.fillMaxWidth().height(68.dp))
        }
        if (days.isEmpty()) item { Text("Chưa có dữ liệu", textAlign = TextAlign.Center) }
    }
}

@Composable
fun DayContent(date: String, history: HistoryUi, onBack: () -> Unit) {
    val day = history.days[date]
    HistoryList(day?.label ?: date, onBack) {
        item { Text(watchMinutes(day?.creditedMinutes ?: 0), style = MaterialTheme.typography.display3) }
        item { Text("/ ${watchMinutes(history.dailyTarget)}", style = MaterialTheme.typography.caption1) }
        if (day != null) {
            item { TimelineLegend() }
            item {
                val (start, end) = timelineBounds(day.timeline)
                TimelineLine(day.timeline, start, end, labels = true)
            }
            day.attendance?.let { span -> item {
                Text("Vào đầu → ${if (span.running) "hiện tại" else "ra cuối"}: ${watchMinutes(span.minutes)}",
                    style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
            } }
            item { Text("Tại văn phòng: ${watchMinutes(day.officeMinutes)}", style = MaterialTheme.typography.caption1) }
            item { Text(if (day.lunchExtraMinutes > 0) "Nghỉ trưa quá ${watchMinutes(day.lunchExtraMinutes)}" else "Nghỉ trưa trong mức 1h",
                style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
            day.exception?.let { exception -> item { Text(when (exception) {
                "ANNUAL_LEAVE" -> "Nghỉ phép"; "HALF_DAY_LEAVE" -> "Nghỉ nửa ngày"; "PUBLIC_HOLIDAY" -> "Ngày lễ"
                "WFH" -> "Làm ở nhà"; else -> "Nghỉ không lương"
            }, style = MaterialTheme.typography.caption1) } }
            for (session in day.sessions) item { Text(session, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center) }
            if (day.sessions.isEmpty()) item { Text("Chưa có phiên vào–ra", textAlign = TextAlign.Center) }
        } else item { Text(if (history.synced) "Chưa có dữ liệu ngày này" else "Chưa đồng bộ ngày này", textAlign = TextAlign.Center) }
    }
}
