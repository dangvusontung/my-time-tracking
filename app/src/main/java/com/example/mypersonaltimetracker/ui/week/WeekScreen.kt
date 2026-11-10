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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.domain.JUNGLE_LAW_DAILY_MINUTES
import com.example.mypersonaltimetracker.ui.common.SectionHeader
import com.example.mypersonaltimetracker.ui.common.SummaryStatRow
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

        SectionHeader("Tuần ${formatDate(progress.weekStart)} – ${formatDate(progress.weekStart.plusDays(6))}")

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.example.mypersonaltimetracker.ui.common.GoalRing(progress.weekTotalMinutes, progress.targetMinutes, "Tiến độ tuần")
                state.fridayLeaveAt?.let { leave ->
                    Text("Dự kiến về đủ 40h: ${com.example.mypersonaltimetracker.ui.common.formatTime(leave, java.time.ZoneId.systemDefault())}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Thứ Sáu sớm nhất 16:30 · nếu tiếp tục ở văn phòng", style = MaterialTheme.typography.bodySmall)
                }
                if (progress.hasCustomTarget) AssistChip(onClick = { showTargetDialog = true }, label = { Text("Mục tiêu tuỳ chỉnh") })
                val jungleAvgMet = state.avgPerDayMinutes >= JUNGLE_LAW_DAILY_MINUTES
                Text(
                    "Luật rừng 8h30/ngày: ${state.jungleLawDays} ngày đạt" +
                        if (state.avgPerDayMinutes > 0) {
                            " · TB ${formatMinutes(state.avgPerDayMinutes)}/ngày — ${if (jungleAvgMet) "đạt" else "chưa đạt"}"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.avgPerDayMinutes > 0 && jungleAvgMet) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Giờ từng ngày trong tuần", style = MaterialTheme.typography.titleSmall)
                com.example.mypersonaltimetracker.ui.common.TimelineLegend()
                val (axisStart, axisEnd) = com.example.mypersonaltimetracker.ui.common.timelineBounds(state.timelines.flatten())
                state.timelines.forEachIndexed { index, segments ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDate(state.weekStart.plusDays(index.toLong())), style = MaterialTheme.typography.labelMedium)
                        Text(formatMinutes(state.dayTotals[index]), style = MaterialTheme.typography.labelMedium)
                    }
                    com.example.mypersonaltimetracker.ui.common.TimelineLine(segments, axisStart, axisEnd, labels = index == 6)
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStatRow(Icons.Default.Schedule, "Giờ tại văn phòng", formatMinutes(progress.officeMinutes))
                SummaryStatRow(Icons.Default.LunchDining, "Nghỉ trưa quá giờ",
                    if (state.lunchOverrunDays == 0) "Không có" else "${state.lunchOverrunDays} ngày · ${formatMinutes(state.lunchOverrunMinutes)}")
                SummaryStatRow(Icons.Default.BeachAccess, "Cộng loại ngày (phép/lễ/WFH)", "+${formatMinutes(progress.exceptionCreditMinutes)}")
                SummaryStatRow(Icons.Default.Functions, "Tổng tuần", formatMinutes(progress.weekTotalMinutes), emphasized = true)
                SummaryStatRow(Icons.Default.Flag, "Mục tiêu", formatMinutes(progress.targetMinutes))
                SummaryStatRow(Icons.Default.History, "Còn lại", formatMinutes(progress.remainingMinutes))
                SummaryStatRow(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    "Cần mỗi ngày (còn ${
                        if (progress.daysLeft % 1.0 == 0.0) progress.daysLeft.toInt().toString() else progress.daysLeft.toString()
                    } ngày)",
                    formatMinutes(progress.perDayNeededMinutes),
                    emphasized = true,
                )
            }
        }

        OutlinedButton(
            onClick = { showTargetDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
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
            currentHours = (progress?.targetMinutes ?: 2400) / 60.0,
            onDismiss = { showTargetDialog = false },
            onSet = { hours -> viewModel.setCustomTarget((hours * 60).toInt()); showTargetDialog = false },
            onClear = { viewModel.clearCustomTarget(); showTargetDialog = false },
        )
    }
}

@Composable
internal fun TargetDialog(
    hasCustom: Boolean,
    currentHours: Double,
    onDismiss: () -> Unit,
    onSet: (Double) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember {
        mutableStateOf(if (currentHours % 1.0 == 0.0) currentHours.toInt().toString() else currentHours.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mục tiêu tuần này (giờ)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toDoubleOrNull()?.let { if (it > 0) onSet(it) } }) { Text("Lưu") }
        },
        dismissButton = {
            Row {
                if (hasCustom) TextButton(onClick = onClear) { Text("Xoá tuỳ chỉnh") }
                TextButton(onClick = onDismiss) { Text("Huỷ") }
            }
        },
    )
}
