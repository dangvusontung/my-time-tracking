package com.example.mypersonaltimetracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.ui.calendar.CalendarScreen
import com.example.mypersonaltimetracker.ui.calendar.CalendarViewModel
import com.example.mypersonaltimetracker.ui.common.EmptyState
import com.example.mypersonaltimetracker.ui.common.InfoChip
import com.example.mypersonaltimetracker.ui.common.NumberBadge
import com.example.mypersonaltimetracker.ui.common.SectionHeader
import com.example.mypersonaltimetracker.ui.common.appViewModel
import com.example.mypersonaltimetracker.ui.common.formatDate
import com.example.mypersonaltimetracker.ui.common.formatDateLongVi
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import com.example.mypersonaltimetracker.ui.day.labelVi
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onDayClick: (LocalDate) -> Unit,
    onShowMonths: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var calendarMode by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        // Thanh công cụ: xem theo tháng + chuyển lưới lịch / danh sách.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onShowMonths) {
                Text("Xem theo tháng")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { calendarMode = !calendarMode }) {
                Icon(
                    if (calendarMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.CalendarMonth,
                    contentDescription = if (calendarMode) "Xem danh sách" else "Xem lịch",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (calendarMode) {
            val app = App.get(LocalContext.current)
            val calVm: CalendarViewModel = appViewModel { CalendarViewModel(app) }
            val calState by calVm.uiState.collectAsStateWithLifecycle()
            CalendarScreen(state = calState, onDayClick = onDayClick)
        } else if (state.rows.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Default.CalendarMonth,
                    message = "Chưa có dữ liệu",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // Nhóm theo tuần (bắt đầu Thứ Hai); rows đã sắp xếp mới nhất trước.
            val weeks = state.rows.groupBy { it.date.with(DayOfWeek.MONDAY) }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weeks.forEach { (weekStart, rows) ->
                    item(key = "header-$weekStart") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionHeader(
                                "Tuần ${formatDate(weekStart)} – ${formatDate(weekStart.plusDays(6))}",
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatMinutes(rows.sumOf { it.totalMinutes }),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(rows, key = { it.date.toString() }) { row ->
                        Card(Modifier.fillMaxWidth().clickable { onDayClick(row.date) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                NumberBadge("${row.date.dayOfMonth}")
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(formatDateLongVi(row.date), style = MaterialTheme.typography.bodyLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.exception?.let { InfoChip(it.labelVi()) }
                                        if (row.hasNote) InfoChip("Có ghi chú")
                                    }
                                }
                                Text(formatMinutes(row.totalMinutes), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
