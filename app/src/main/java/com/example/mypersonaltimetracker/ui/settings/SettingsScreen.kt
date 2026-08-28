package com.example.mypersonaltimetracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypersonaltimetracker.ui.common.AppTimePickerDialog
import com.example.mypersonaltimetracker.ui.common.dayOfWeekShortVi
import com.example.mypersonaltimetracker.ui.common.dayOfWeekVi
import com.example.mypersonaltimetracker.ui.common.minuteOfDayToTime
import kotlinx.coroutines.launch
import java.time.DayOfWeek

private enum class TimeField { MORNING, EVENING, LUNCH_START, LUNCH_END }
private enum class NumberField { LUNCH_MAX_CREDIT, WEEKLY_TARGET }

private fun formatDays(days: Double): String =
    if (days % 1.0 == 0.0) days.toInt().toString() else days.toString()

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val usedLeaveDays by viewModel.usedLeaveDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var timeField by remember { mutableStateOf<TimeField?>(null) }
    var numberField by remember { mutableStateOf<NumberField?>(null) }
    var showWorkdaysDialog by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(it) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Đã sao lưu dữ liệu" else "Sao lưu thất bại",
                    )
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestoreUri = uri }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun ensureNotifPermissionThenEnable() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.setRemindersEnabled(true)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Nhắc vào/ra office hằng ngày", Modifier.weight(1f))
                    Switch(
                        checked = settings.remindersEnabled,
                        onCheckedChange = { if (it) ensureNotifPermissionThenEnable() else viewModel.setRemindersEnabled(false) },
                    )
                }
                HorizontalDivider()
                SettingRow("Giờ nhắc buổi sáng", minuteOfDayToTime(settings.morningReminderMin)) {
                    timeField = TimeField.MORNING
                }
                SettingRow("Giờ nhắc buổi chiều", minuteOfDayToTime(settings.eveningReminderMin)) {
                    timeField = TimeField.EVENING
                }
            }
        }

        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingRow("Bắt đầu khung nghỉ trưa", minuteOfDayToTime(settings.lunchWindowStartMin)) {
                    timeField = TimeField.LUNCH_START
                }
                SettingRow("Kết thúc khung nghỉ trưa", minuteOfDayToTime(settings.lunchWindowEndMin)) {
                    timeField = TimeField.LUNCH_END
                }
                SettingRow("Cộng nghỉ trưa tối đa", "${settings.lunchMaxCreditMinutes} phút") {
                    numberField = NumberField.LUNCH_MAX_CREDIT
                }
            }
        }

        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingRow("Mục tiêu tuần mặc định", "${settings.defaultWeeklyTargetMinutes / 60} giờ") {
                    numberField = NumberField.WEEKLY_TARGET
                }
                SettingRow(
                    "Ngày làm việc trong tuần",
                    settings.workdays.sortedBy(DayOfWeek::getValue).joinToString(", ") { dayOfWeekShortVi(it) },
                ) {
                    showWorkdaysDialog = true
                }
            }
        }

        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                val remaining = settings.annualLeaveQuotaDays - usedLeaveDays
                SettingRow("Phép năm", "${formatDays(settings.annualLeaveQuotaDays)} ngày/năm") {
                    showQuotaDialog = true
                }
                Text(
                    "Đã dùng ${formatDays(usedLeaveDays)} / ${formatDays(settings.annualLeaveQuotaDays)} ngày" +
                        " (còn ${formatDays(remaining)})",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingRow("Xuất CSV", "Toàn bộ dữ liệu") {
                    csvLauncher.launch("time_tracker_export.csv")
                }
                HorizontalDivider()
                SettingRow("Sao lưu dữ liệu", "File JSON") {
                    backupLauncher.launch("time_tracker_backup.json")
                }
                SettingRow("Khôi phục dữ liệu", "Từ file JSON") {
                    restoreLauncher.launch(arrayOf("application/json"))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showWorkdaysDialog) {
        WorkdaysDialog(
            selected = settings.workdays,
            onDismiss = { showWorkdaysDialog = false },
            onSave = { viewModel.setWorkdays(it); showWorkdaysDialog = false },
        )
    }

    timeField?.let { field ->
        val config: Pair<Int, (Int) -> Unit> = when (field) {
            TimeField.MORNING -> Pair(settings.morningReminderMin, { v: Int ->
                viewModel.setReminderTimes(v, settings.eveningReminderMin)
            })
            TimeField.EVENING -> Pair(settings.eveningReminderMin, { v: Int ->
                viewModel.setReminderTimes(settings.morningReminderMin, v)
            })
            TimeField.LUNCH_START -> Pair(settings.lunchWindowStartMin, { v: Int ->
                viewModel.setLunchWindow(v, settings.lunchWindowEndMin)
            })
            TimeField.LUNCH_END -> Pair(settings.lunchWindowEndMin, { v: Int ->
                viewModel.setLunchWindow(settings.lunchWindowStartMin, v)
            })
        }
        val (initial, onPick) = config
        AppTimePickerDialog(
            initialMinuteOfDay = initial,
            onDismiss = { timeField = null },
            onConfirm = { onPick(it); timeField = null },
        )
    }

    numberField?.let { field ->
        val config: Triple<String, Int, (Int) -> Unit> = when (field) {
            NumberField.LUNCH_MAX_CREDIT -> Triple(
                "Cộng nghỉ trưa tối đa (phút)",
                settings.lunchMaxCreditMinutes,
                { v: Int -> viewModel.setLunchMaxCreditMinutes(v) },
            )
            NumberField.WEEKLY_TARGET -> Triple(
                "Mục tiêu tuần mặc định (giờ)",
                settings.defaultWeeklyTargetMinutes / 60,
                { v: Int -> viewModel.setDefaultWeeklyTargetMinutes(v * 60) },
            )
        }
        val (title, current, onSave) = config
        NumberDialog(
            title = title,
            current = current,
            onDismiss = { numberField = null },
            onSave = { onSave(it); numberField = null },
        )
    }

    if (showQuotaDialog) {
        QuotaDialog(
            current = settings.annualLeaveQuotaDays,
            onDismiss = { showQuotaDialog = false },
            onSave = { viewModel.setAnnualLeaveQuotaDays(it); showQuotaDialog = false },
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Khôi phục dữ liệu?") },
            text = { Text("Toàn bộ dữ liệu và cài đặt hiện tại sẽ bị THAY THẾ bằng nội dung file sao lưu.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    viewModel.importBackup(uri) { ok ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) "Đã khôi phục dữ liệu" else "Khôi phục thất bại — kiểm tra lại file",
                            )
                        }
                    }
                }) { Text("Khôi phục") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Huỷ") }
            },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun WorkdaysDialog(
    selected: Set<DayOfWeek>,
    onDismiss: () -> Unit,
    onSave: (Set<DayOfWeek>) -> Unit,
) {
    var checked by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ngày làm việc trong tuần") },
        text = {
            Column {
                DayOfWeek.entries.forEach { day ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = day in checked,
                            onCheckedChange = { on ->
                                checked = if (on) checked + day else checked - day
                            },
                        )
                        Text(dayOfWeekVi(day))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(checked) }, enabled = checked.isNotEmpty()) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        },
    )
}

@Composable
private fun QuotaDialog(
    current: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(formatDays(current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hạn mức phép năm (ngày)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toDoubleOrNull()?.let { if (it > 0) onSave(it) } }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        },
    )
}

@Composable
private fun NumberDialog(
    title: String,
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let { if (it > 0) onSave(it) } }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        },
    )
}
