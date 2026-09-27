package com.example.mypersonaltimetracker.ui.settings

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.data.AppSettings
import com.example.mypersonaltimetracker.data.Backup
import com.example.mypersonaltimetracker.data.CsvExport
import com.example.mypersonaltimetracker.domain.LeaveQuotaCalculator
import com.example.mypersonaltimetracker.office.OfficeStatusService
import com.example.mypersonaltimetracker.reminder.ReminderScheduler
import com.example.mypersonaltimetracker.widget.updateAllWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SettingsViewModel(private val app: App) : ViewModel() {

    private val settingsRepo = app.container.settings

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** US-39: số ngày phép năm đã dùng trong năm dương lịch hiện tại. */
    val usedLeaveDays: StateFlow<Double> =
        app.container.repository.observeExceptions().map { exceptions ->
            LeaveQuotaCalculator.usedDays(
                exceptions.associateBy({ LocalDate.parse(it.date) }, { it.type }),
                LocalDate.now().year,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private fun reschedule() {
        viewModelScope.launch { ReminderScheduler.reschedule(app) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setRemindersEnabled(enabled) }
        reschedule()
    }

    fun setReminderTimes(morningMin: Int, eveningMin: Int) {
        viewModelScope.launch { settingsRepo.setReminderTimes(morningMin, eveningMin) }
        reschedule()
    }

    fun setLunchWindow(startMin: Int, endMin: Int) {
        viewModelScope.launch { settingsRepo.setLunchWindow(startMin, endMin) }
    }

    fun setLunchMaxCreditMinutes(value: Int) {
        viewModelScope.launch { settingsRepo.setLunchMaxCreditMinutes(value) }
    }

    fun setWorkdayCountWindow(startMin: Int, endMin: Int) {
        viewModelScope.launch {
            settingsRepo.setWorkdayCountWindow(startMin, endMin)
            reschedule()
        }
    }

    fun setDefaultWeeklyTargetMinutes(value: Int) {
        viewModelScope.launch { settingsRepo.setDefaultWeeklyTargetMinutes(value) }
    }

    fun setWorkdays(days: Set<DayOfWeek>) {
        viewModelScope.launch { settingsRepo.setWorkdays(days) }
    }

    fun setAnnualLeaveQuotaDays(value: Double) {
        viewModelScope.launch { settingsRepo.setAnnualLeaveQuotaDays(value) }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = app.container.repository
            val csv = CsvExport.build(
                sessions = repo.observeSessions().first(),
                exceptions = repo.observeExceptions().first(),
                now = Instant.now(),
                zone = ZoneId.systemDefault(),
            )
            app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }
    }

    /** US-31: xuất toàn bộ dữ liệu + settings ra file JSON. */
    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = app.container.repository
                val json = Backup.build(
                    sessions = repo.observeSessions().first(),
                    exceptions = repo.observeExceptions().first(),
                    weekTargets = repo.observeWeekTargets().first(),
                    dayNotes = repo.observeDayNotes().first(),
                    settings = settingsRepo.settings.first(),
                )
                app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    ?: throw IllegalStateException("Không mở được file để ghi")
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    /** Ghi bản sao lưu vào cache rồi trả về content Uri để chia sẻ (máy khác -> Khôi phục). */
    fun shareBackup(onReady: (Uri?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = app.container.repository
                val json = Backup.build(
                    sessions = repo.observeSessions().first(),
                    exceptions = repo.observeExceptions().first(),
                    weekTargets = repo.observeWeekTargets().first(),
                    dayNotes = repo.observeDayNotes().first(),
                    settings = settingsRepo.settings.first(),
                )
                val file = File(app.cacheDir, "time_tracker_backup.json")
                file.writeText(json)
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) { onReady(uri) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onReady(null) }
            }
        }
    }

    /** US-31: khôi phục — THAY THẾ toàn bộ dữ liệu hiện tại, rồi refresh reminder/service/widget. */
    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Không đọc được file")
                val data = Backup.parse(text)
                app.container.repository.replaceAll(
                    data.sessions, data.exceptions, data.weekTargets, data.dayNotes,
                )
                data.settings?.let { settingsRepo.restore(it) }
                ReminderScheduler.reschedule(app)
                OfficeStatusService.sync(app)
                updateAllWidgets(app)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
