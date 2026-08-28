package com.example.mypersonaltimetracker.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.domain.MonthTotal
import com.example.mypersonaltimetracker.ui.common.formatMinutes

/** US-26: tổng giờ theo tháng (12 tháng gần nhất có dữ liệu). */
@Composable
fun MonthScreen(viewModel: MonthViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.months.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Chưa có dữ liệu", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.months, key = { it.yearMonth.toString() }) { month ->
            MonthRow(month)
        }
    }
}

@Composable
private fun MonthRow(month: MonthTotal) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Tháng ${month.yearMonth.monthValue}/${month.yearMonth.year}")
                Text(
                    "${month.daysWithData} ngày có dữ liệu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(formatMinutes(month.totalMinutes), style = MaterialTheme.typography.titleMedium)
        }
    }
}
