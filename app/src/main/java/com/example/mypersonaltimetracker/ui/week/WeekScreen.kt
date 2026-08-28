package com.example.mypersonaltimetracker.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.ui.common.formatDate
import com.example.mypersonaltimetracker.ui.common.formatMinutes

@Composable
fun WeekScreen(viewModel: WeekViewModel, onShowHistory: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTargetDialog by remember { mutableStateOf(false) }
    val progress = state.progress

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (progress == null) return@Column

        Text(
            "Tuần ${formatDate(progress.weekStart)} – ${formatDate(progress.weekStart.plusDays(6))}",
            style = MaterialTheme.typography.titleMedium,
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = {
                        if (progress.targetMinutes <= 0) 0f
                        else (progress.weekTotalMinutes.toFloat() / progress.targetMinutes).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatMinutes(progress.weekTotalMinutes)} / ${formatMinutes(progress.targetMinutes)}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (progress.hasCustomTarget) {
                        AssistChip(onClick = { showTargetDialog = true }, label = { Text("Mục tiêu tuỳ chỉnh") })
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Giờ từng ngày trong tuần", style = MaterialTheme.typography.titleSmall)
                WeekChart(
                    dayTotals = state.dayTotals,
                    perDayTargetMinutes = state.perDayTargetMinutes,
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Line("Giờ ở office", formatMinutes(progress.officeMinutes))
                Line("Cộng nghỉ trưa", "+${formatMinutes(progress.lunchCreditMinutes)}")
                Line("Cộng loại ngày (phép/lễ/WFH)", "+${formatMinutes(progress.exceptionCreditMinutes)}")
                Line("Tổng tuần", formatMinutes(progress.weekTotalMinutes))
                Line("Mục tiêu", formatMinutes(progress.targetMinutes))
                Line("Còn lại", formatMinutes(progress.remainingMinutes))
                Line(
                    "Số ngày làm còn lại",
                    if (progress.daysLeft % 1.0 == 0.0) progress.daysLeft.toInt().toString() else progress.daysLeft.toString(),
                )
                Line("Cần mỗi ngày", formatMinutes(progress.perDayNeededMinutes))
            }
        }

        TextButton(onClick = { showTargetDialog = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Đặt mục tiêu tuần này")
        }
        TextButton(onClick = onShowHistory, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Các tuần trước")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showTargetDialog) {
        TargetDialog(
            hasCustom = progress?.hasCustomTarget == true,
            currentHours = (progress?.targetMinutes ?: 2400) / 60,
            onDismiss = { showTargetDialog = false },
            onSet = { hours -> viewModel.setCustomTarget(hours * 60); showTargetDialog = false },
            onClear = { viewModel.clearCustomTarget(); showTargetDialog = false },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value)
    }
}

@Composable
internal fun TargetDialog(
    hasCustom: Boolean,
    currentHours: Int,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember { mutableStateOf(currentHours.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mục tiêu tuần này (giờ)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let { if (it > 0) onSet(it) } }) { Text("Lưu") }
        },
        dismissButton = {
            Row {
                if (hasCustom) TextButton(onClick = onClear) { Text("Xoá tuỳ chỉnh") }
                TextButton(onClick = onDismiss) { Text("Huỷ") }
            }
        },
    )
}
