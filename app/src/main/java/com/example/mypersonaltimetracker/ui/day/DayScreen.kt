package com.example.mypersonaltimetracker.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.domain.JUNGLE_LAW_DAILY_MINUTES
import com.example.mypersonaltimetracker.ui.common.InfoChip
import com.example.mypersonaltimetracker.ui.common.SectionHeader
import com.example.mypersonaltimetracker.ui.common.SummaryStatRow
import com.example.mypersonaltimetracker.ui.common.formatDateLongVi
import com.example.mypersonaltimetracker.ui.common.formatMinutes
import com.example.mypersonaltimetracker.ui.common.formatTime
import java.time.Duration
import java.time.Instant
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "Có phiên chưa đóng từ ngày trước!",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        state.staleSessions.forEach { s ->
                            val d = s.inAt.atZone(zone).toLocalDate()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "${formatDateLongVi(d)} · vào ${formatTime(s.inAt, zone)}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
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

        // Hero: live status + the big check-in/out action (today only).
        if (state.isToday || state.isRunning) {
            item {
                HeroCard(state = state, zone = zone, onToggle = viewModel::toggle)
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (state.isToday) "Tổng hôm nay" else formatDateLongVi(state.date),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SummaryStatRow(Icons.Default.Schedule, "Giờ ở office", formatMinutes(state.summary.officeMinutes))
                    SummaryStatRow(Icons.Default.LunchDining, "Trừ nghỉ trưa", formatMinutes(state.summary.lunchAdjustmentMinutes))
                    state.exception?.let {
                        SummaryStatRow(Icons.Default.BeachAccess, it.type.labelVi(), "+${formatMinutes(state.summary.exceptionCreditMinutes)}")
                    }
                    HorizontalDivider()
                    SummaryStatRow(
                        Icons.Default.Functions, "Tổng", formatMinutes(state.summary.totalMinutes),
                        emphasized = true,
                    )
                    if (state.overTenHours) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Tổng vượt quá 10h — kiểm tra lại cờ nghỉ phép?",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.BeachAccess,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Loại ngày (nghỉ, lễ, WFH)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            state.exception?.let { it.type.labelVi() + (it.note?.let { n -> " · $n" } ?: "") }
                                ?: "Ngày làm việc bình thường",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = { showException = true }) {
                        Text(if (state.exception == null) "Đặt" else "Đổi")
                    }
                }
            }
        }

        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Ghi chú", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            state.note ?: "Chưa có",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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
                SectionHeader("Các phiên", modifier = Modifier.weight(1f))
                TextButton(onClick = { editErrors = emptyList(); showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Thêm phiên")
                }
            }
        }

        items(state.sessions, key = { it.id }) { s ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${formatTime(s.inAt, zone)} → ${s.outAt?.let { formatTime(it, zone) } ?: "đang mở"}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                formatMinutes(s.durationMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (s.isEstimated) {
                                InfoChip("ước tính")
                            }
                        }
                    }
                    IconButton(onClick = { editErrors = emptyList(); editing = s }) {
                        Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { viewModel.deleteSession(s) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
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
            initialInMin = state.now.atZone(zone).toLocalTime().let { it.hour * 60 + it.minute },
            initialOutMin = null, // mặc định phiên đang mở — đặt giờ ra sau
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
private fun HeroCard(state: DayUiState, zone: ZoneId, onToggle: () -> Unit) {
    val running = state.runningSession
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (running != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (running != null) {
                Text(
                    "Đang trong office",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    formatElapsed(Duration.between(running.inAt, state.now)),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Vào lúc ${formatTime(running.inAt, zone)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text(
                    "Hôm nay đã làm",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatMinutes(state.summary.totalMinutes),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Tiến độ so với mục tiêu ngày (8h30 mặc định).
            val target = state.dailyTargetMinutes
            if (state.isToday && target != null && target > 0) {
                val ratio = state.summary.totalMinutes.toFloat() / target
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (ratio >= 1f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        "Mục tiêu ngày: ${formatMinutes(state.summary.totalMinutes)} / ${formatMinutes(target)} (${(ratio * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (running != null) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Chỉ số phụ: "luật rừng" 8h30/ngày.
            if (state.isToday) {
                val total = state.summary.totalMinutes
                val met = total >= JUNGLE_LAW_DAILY_MINUTES
                val jungleRatio = total.toFloat() / JUNGLE_LAW_DAILY_MINUTES
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { jungleRatio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        "Luật rừng 8h30: ${formatMinutes(total)} / 8h30 (${(jungleRatio * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            met -> MaterialTheme.colorScheme.tertiary
                            running != null -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    val leaveText = when {
                        met -> "Đủ luật rừng — về được rồi"
                        state.earliestLeaveAt != null -> "Về sớm nhất: ${formatTime(state.earliestLeaveAt!!, zone)}"
                        else -> null
                    }
                    if (leaveText != null) {
                        Text(
                            leaveText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                met -> MaterialTheme.colorScheme.tertiary
                                running != null -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            if (state.isToday) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = if (running != null) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Icon(
                        if (running != null) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (running != null) "Ra office" else "Vào office",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
