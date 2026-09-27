package com.example.mypersonaltimetracker.ui.week

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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.ui.common.EmptyState
import com.example.mypersonaltimetracker.ui.common.InfoChip
import com.example.mypersonaltimetracker.ui.common.NumberBadge
import com.example.mypersonaltimetracker.ui.common.formatDate
import com.example.mypersonaltimetracker.ui.common.formatMinutes

@Composable
fun WeekHistoryScreen(viewModel: WeekHistoryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingWeek by remember { mutableStateOf<WeekHistoryRow?>(null) }

    if (state.rows.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Default.DateRange,
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
        items(state.rows, key = { it.weekStart.toString() }) { row ->
            Card(Modifier.fillMaxWidth().clickable { editingWeek = row }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NumberBadge("${row.weekStart.dayOfMonth}")
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tuần từ ${formatDate(row.weekStart)}", style = MaterialTheme.typography.bodyLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (row.isCurrentWeek) InfoChip("Tuần này")
                            Text(
                                "${formatMinutes(row.progress.weekTotalMinutes)} / ${formatMinutes(row.progress.targetMinutes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        statusText(row),
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            row.overMinutes > 0 -> MaterialTheme.colorScheme.tertiary
                            row.overMinutes < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    editingWeek?.let { row ->
        TargetDialog(
            hasCustom = row.progress.hasCustomTarget,
            currentHours = row.progress.targetMinutes / 60.0,
            onDismiss = { editingWeek = null },
            onSet = { hours -> viewModel.setCustomTarget(row.weekStart, (hours * 60).toInt()); editingWeek = null },
            onClear = { viewModel.clearCustomTarget(row.weekStart); editingWeek = null },
        )
    }
}

private fun statusText(row: WeekHistoryRow): String {
    val over = row.overMinutes
    return when {
        over > 0 -> "Vượt ${formatMinutes(over)}"
        over < 0 -> "Thiếu ${formatMinutes(-over)}"
        else -> "Đủ"
    }
}
