package com.example.mypersonaltimetracker.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.example.mypersonaltimetracker.data.DayExceptionEntity
import com.example.mypersonaltimetracker.domain.DayType
import com.example.mypersonaltimetracker.ui.common.formatMinutes

fun DayType.labelVi(): String = when (this) {
    DayType.ANNUAL_LEAVE -> "Nghỉ phép"
    DayType.HALF_DAY_LEAVE -> "Nghỉ nửa ngày"
    DayType.PUBLIC_HOLIDAY -> "Nghỉ lễ"
    DayType.WFH -> "Làm từ xa (WFH)"
    DayType.UNPAID_LEAVE -> "Nghỉ không lương"
}

@Composable
fun ExceptionDialog(
    existing: DayExceptionEntity?,
    onDismiss: () -> Unit,
    onSet: (DayType, String?) -> Unit,
    onClear: () -> Unit,
) {
    var selected by remember { mutableStateOf(existing?.type ?: DayType.ANNUAL_LEAVE) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loại ngày (nghỉ, lễ, WFH)") },
        text = {
            Column {
                DayType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = selected == type, onClick = { selected = type })
                        Column {
                            Text(type.labelVi())
                            Text(
                                "Cộng ${formatMinutes(type.creditMinutes)} vào tổng giờ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú (tuỳ chọn)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(selected, note) }) { Text("Lưu") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onClear) { Text("Xoá") }
                }
                TextButton(onClick = onDismiss) { Text("Huỷ") }
            }
        },
    )
}
