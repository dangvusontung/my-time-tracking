package com.example.mypersonaltimetracker.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.ui.common.formatDateLongVi
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import com.example.mypersonaltimetracker.ui.day.labelVi
import java.time.LocalDate

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onDayClick: (LocalDate) -> Unit,
    onShowMonths: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.rows.isEmpty()) {
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
        item {
            TextButton(onClick = onShowMonths, modifier = Modifier.fillMaxWidth()) {
                Text("Xem theo tháng")
            }
        }
        items(state.rows, key = { it.date.toString() }) { row ->
            Card(Modifier.fillMaxWidth().clickable { onDayClick(row.date) }) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDateLongVi(row.date))
                        row.exception?.let {
                            Text(it.labelVi(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (row.hasNote) {
                            Text("Có ghi chú", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text(formatMinutes(row.totalMinutes), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
