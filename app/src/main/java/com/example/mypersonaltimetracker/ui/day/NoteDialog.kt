package com.example.mypersonaltimetracker.ui.day

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Ghi chú của một ngày. Lưu chuỗi trống sẽ xoá ghi chú. */
@Composable
fun NoteDialog(
    existing: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember { mutableStateOf(existing.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi chú") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nội dung ghi chú") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        },
    )
}
