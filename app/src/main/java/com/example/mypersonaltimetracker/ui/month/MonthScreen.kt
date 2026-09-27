package com.example.mypersonaltimetracker.ui.month

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.domain.JUNGLE_LAW_DAILY_MINUTES
import com.example.mypersonaltimetracker.domain.MonthTotal
import com.example.mypersonaltimetracker.ui.common.EmptyState
import com.example.mypersonaltimetracker.ui.common.NumberBadge
import com.example.mypersonaltimetracker.ui.common.formatMinutes

/** US-26: tổng giờ theo tháng (12 tháng gần nhất có dữ liệu). */
@Composable
fun MonthScreen(viewModel: MonthViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.months.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Default.CalendarMonth,
                message = "Chưa có dữ liệu",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(state.months, key = { it.yearMonth.toString() }) { month ->
            MonthRow(month)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MonthRow(month: MonthTotal) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NumberBadge("T${month.yearMonth.monthValue}")
            Column(Modifier.weight(1f)) {
                Text("Tháng ${month.yearMonth.monthValue}/${month.yearMonth.year}", style = MaterialTheme.typography.bodyLarge)
                val jungleMet = month.avgPerDayMinutes >= JUNGLE_LAW_DAILY_MINUTES
                Text(
                    "${month.daysWithData} ngày có dữ liệu · TB ${formatMinutes(month.avgPerDayMinutes)}/ngày" +
                        " · ${if (jungleMet) "đạt luật rừng" else "chưa đạt luật rừng"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (jungleMet) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatMinutes(month.totalMinutes), style = MaterialTheme.typography.titleMedium)
        }
    }
}
