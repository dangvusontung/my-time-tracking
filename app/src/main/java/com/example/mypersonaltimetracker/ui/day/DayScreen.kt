package com.example.mypersonaltimetracker.ui.day

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.ui.common.formatDateLongVi
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import com.example.mypersonaltimetracker.ui.common.formatTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun formatElapsed(duration: Duration): String {
    val s = duration.seconds
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Shared content for the Today screen and the history day-detail screen. */
@Composable
fun DayContent(
    viewModel: DayViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    var editing by remember { mutableStateOf<SessionUi?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showException by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var editErrors by remember { mutableStateOf(emptyList<com.example.mypersonaltimetracker.domain.SessionValidationError>()) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (state.staleSessions.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Có phiên chưa đóng từ ngày trước!",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        state.staleSessions.forEach { s ->
                            val d = s.inAt.atZone(zone).toLocalDate()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "${formatDateLongVi(d)} · vào ${formatTime(s.inAt, zone)}",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(onClick = { editErrors = emptyList(); editing = s }) {
                                    Text("Đóng phiên")
                                }
                                TextButton(onClick = { viewModel.deleteSession(s) }) { Text("Xoá") }
                            }
                        }
                    }
                }
            }
        }

        if (state.isToday) {
            item {
                Button(
                    onClick = { viewModel.toggle() },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Text(
                        if (state.isRunning) "Ra office" else "Vào office",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }

        state.runningSession?.let { running ->
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Đang trong office", style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatElapsed(Duration.between(running.inAt, state.now)),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text("Vào lúc ${formatTime(running.inAt, zone)}")
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (state.isToday) "Hôm nay" else formatDateLongVi(state.date),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TotalLine("Giờ ở office", formatMinutes(state.summary.officeMinutes))
                    TotalLine("Cộng nghỉ trưa", "+${formatMinutes(state.summary.lunchCreditMinutes)}")
                    state.exception?.let {
                        TotalLine("${it.type.labelVi()}", "+${formatMinutes(state.summary.exceptionCreditMinutes)}")
                    }
                    HorizontalDivider()
                    TotalLine("Tổng", formatMinutes(state.summary.totalMinutes), bold = true)
                    if (state.overTenHours) {
                        Text(
                            "Tổng vượt quá 10h — kiểm tra lại cờ nghỉ phép?",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.exception?.let { "Loại ngày: ${it.type.labelVi()}" + (it.note?.let { n -> " · $n" } ?: "") }
                            ?: "Chưa đặt loại ngày",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showException = true }) {
                        Text(if (state.exception == null) "Đặt" else "Đổi")
                    }
                }
            }
        }

        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.note?.let { "Ghi chú: $it" } ?: "Chưa có ghi chú",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showNote = true }) {
                        Text(if (state.note == null) "Thêm" else "Sửa")
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Các phiên", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { editErrors = emptyList(); showAdd = true }) { Text("Thêm phiên") }
            }
        }

        items(state.sessions, key = { it.id }) { s ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${formatTime(s.inAt, zone)} → ${s.outAt?.let { formatTime(it, zone) } ?: "đang mở"}" +
                                " · ${formatMinutes(s.durationMinutes)}",
                        )
                        if (s.isEstimated) {
                            Text("ước tính", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    IconButton(onClick = { editErrors = emptyList(); editing = s }) {
                        Icon(Icons.Default.Edit, contentDescription = "Sửa")
                    }
                    IconButton(onClick = { viewModel.deleteSession(s) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xoá")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAdd) {
        SessionEditDialog(
            title = "Thêm phiên",
            initialDate = state.date,
            initialInMin = 8 * 60,
            initialOutMin = 17 * 60,
            initialEstimated = false,
            errors = editErrors,
            onDismiss = { showAdd = false },
            onSave = { date, inMin, outMin, estimated ->
                val inAt = date.atTime(inMin / 60, inMin % 60).atZone(zone).toInstant()
                val outAt = outMin?.let { date.atTime(it / 60, it % 60).atZone(zone).toInstant() }
                viewModel.saveSession(0, inAt, outAt, estimated,
                    onError = { editErrors = it },
                    onSuccess = { showAdd = false })
            },
        )
    }

    editing?.let { s ->
        val d = s.inAt.atZone(zone).toLocalDate()
        SessionEditDialog(
            title = if (s.isRunning) "Đóng / sửa phiên" else "Sửa phiên",
            initialDate = d,
            initialInMin = s.inAt.atZone(zone).toLocalTime().toSecondOfDay() / 60,
            initialOutMin = s.outAt?.atZone(zone)?.toLocalTime()?.toSecondOfDay()?.div(60),
            initialEstimated = s.isEstimated,
            errors = editErrors,
            onDismiss = { editing = null },
            onSave = { date, inMin, outMin, estimated ->
                val inAt = date.atTime(inMin / 60, inMin % 60).atZone(zone).toInstant()
                val outAt = outMin?.let { date.atTime(it / 60, it % 60).atZone(zone).toInstant() }
                viewModel.saveSession(s.id, inAt, outAt, estimated,
                    onError = { editErrors = it },
                    onSuccess = { editing = null })
            },
        )
    }

    if (showException) {
        ExceptionDialog(
            existing = state.exception,
            onDismiss = { showException = false },
            onSet = { type, note -> viewModel.setException(type, note); showException = false },
            onClear = { viewModel.clearException(); showException = false },
        )
    }

    if (showNote) {
        NoteDialog(
            existing = state.note,
            onDismiss = { showNote = false },
            onSave = { viewModel.saveNote(it); showNote = false },
        )
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
    }
}
