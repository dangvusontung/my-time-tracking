package com.example.mypersonaltimetracker.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.mypersonaltimetracker.domain.SessionValidationError
import com.example.mypersonaltimetracker.ui.common.AppDatePickerDialog
import com.example.mypersonaltimetracker.ui.common.AppTimePickerDialog
import com.example.mypersonaltimetracker.ui.common.formatDateLongVi
import com.example.mypersonaltimetracker.ui.common.minuteOfDayToTime
import java.time.LocalDate

fun SessionValidationError.messageVi(): String = when (this) {
    SessionValidationError.OUT_NOT_AFTER_IN -> "Giờ ra phải sau giờ vào"
    SessionValidationError.OVERLAP -> "Trùng với một phiên khác"
    SessionValidationError.DUPLICATE_RUNNING -> "Đã có một phiên đang mở"
    SessionValidationError.OPEN_SESSION_IN_PAST -> "Ngày trong quá khứ phải có giờ ra"
}

/**
 * Used for add, edit, add-to-past-day and closing stale sessions.
 * [onSave] receives (date, inMinuteOfDay, outMinuteOfDay or null when still open, isEstimated).
 */
@Composable
fun SessionEditDialog(
    title: String,
    initialDate: LocalDate,
    initialInMin: Int,
    initialOutMin: Int?,
    initialEstimated: Boolean,
    errors: List<SessionValidationError>,
    onDismiss: () -> Unit,
    onSave: (date: LocalDate, inMin: Int, outMin: Int?, isEstimated: Boolean) -> Unit,
) {
    var date by remember { mutableStateOf(initialDate) }
    var inMin by remember { mutableStateOf(initialInMin) }
    var outMin by remember { mutableStateOf(initialOutMin) }
    var estimated by remember { mutableStateOf(initialEstimated) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingIn by remember { mutableStateOf(false) }
    var pickingOut by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(formatDateLongVi(date))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingIn = true }, modifier = Modifier.weight(1f)) {
                        Text("Vào: ${minuteOfDayToTime(inMin)}")
                    }
                    OutlinedButton(
                        onClick = { pickingOut = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(outMin?.let { "Ra: ${minuteOfDayToTime(it)}" } ?: "Ra: —")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = estimated, onCheckedChange = { estimated = it })
                    Text("Giờ ước tính")
                }
                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    errors.forEach {
                        Text(it.messageVi(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, inMin, outMin, estimated) }) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        },
    )

    if (pickingDate) {
        AppDatePickerDialog(
            initial = date,
            onDismiss = { pickingDate = false },
            onConfirm = { date = it; pickingDate = false },
        )
    }
    if (pickingIn) {
        AppTimePickerDialog(
            initialMinuteOfDay = inMin,
            onDismiss = { pickingIn = false },
            onConfirm = { inMin = it; pickingIn = false },
        )
    }
    if (pickingOut) {
        AppTimePickerDialog(
            initialMinuteOfDay = outMin ?: (inMin + 60).coerceAtMost(1439),
            onDismiss = { pickingOut = false },
            onConfirm = { outMin = it; pickingOut = false },
        )
    }
}
