package com.example.mypersonaltimetracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore(name = "settings")

val DEFAULT_WORKDAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

data class AppSettings(
    val defaultWeeklyTargetMinutes: Int = 2400, // 40h chính thức = 8h/ngày × 5 ngày
    val lunchWindowStartMin: Int = 690,  // 11:30
    val lunchWindowEndMin: Int = 840,    // 14:00
    val lunchMaxCreditMinutes: Int = 60,
    /** Khung giờ công ty công nhận: trước/sau khung này không tính giờ (7:30–16:30). */
    val workdayCountStartMin: Int = 450,  // 07:30
    val workdayCountEndMin: Int = 990,    // 16:30
    val morningReminderMin: Int = 510,   // 08:30
    val eveningReminderMin: Int = 1050,  // 17:30
    val remindersEnabled: Boolean = true,
    val workdays: Set<DayOfWeek> = DEFAULT_WORKDAYS,
    /** US-39: hạn mức phép năm (ngày), mặc định 12. */
    val annualLeaveQuotaDays: Double = 12.0,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_WEEKLY_TARGET = intPreferencesKey("defaultWeeklyTargetMinutes")
        val LUNCH_START = intPreferencesKey("lunchWindowStartMin")
        val LUNCH_END = intPreferencesKey("lunchWindowEndMin")
        val LUNCH_MAX_CREDIT = intPreferencesKey("lunchMaxCreditMinutes")
        val WORKDAY_COUNT_START = intPreferencesKey("workdayCountStartMin")
        val WORKDAY_COUNT_END = intPreferencesKey("workdayCountEndMin")
        val MORNING_REMINDER = intPreferencesKey("morningReminderMin")
        val EVENING_REMINDER = intPreferencesKey("eveningReminderMin")
        val REMINDERS_ENABLED = booleanPreferencesKey("remindersEnabled")
        val WORKDAYS = stringPreferencesKey("workdays")
        val ANNUAL_LEAVE_QUOTA = doublePreferencesKey("annualLeaveQuotaDays")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            defaultWeeklyTargetMinutes = p[Keys.DEFAULT_WEEKLY_TARGET] ?: 2400,
            lunchWindowStartMin = p[Keys.LUNCH_START] ?: 690,
            lunchWindowEndMin = p[Keys.LUNCH_END] ?: 840,
            lunchMaxCreditMinutes = p[Keys.LUNCH_MAX_CREDIT] ?: 60,
            workdayCountStartMin = p[Keys.WORKDAY_COUNT_START] ?: 450,
            workdayCountEndMin = p[Keys.WORKDAY_COUNT_END] ?: 990,
            morningReminderMin = p[Keys.MORNING_REMINDER] ?: 510,
            eveningReminderMin = p[Keys.EVENING_REMINDER] ?: 1050,
            remindersEnabled = p[Keys.REMINDERS_ENABLED] ?: true,
            workdays = p[Keys.WORKDAYS]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.mapNotNull { v -> DayOfWeek.entries.firstOrNull { it.value == v } }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_WORKDAYS,
            annualLeaveQuotaDays = p[Keys.ANNUAL_LEAVE_QUOTA] ?: 12.0,
        )
    }

    suspend fun setDefaultWeeklyTargetMinutes(value: Int) =
        context.dataStore.edit { it[Keys.DEFAULT_WEEKLY_TARGET] = value }

    suspend fun setLunchWindow(startMin: Int, endMin: Int) =
        context.dataStore.edit {
            it[Keys.LUNCH_START] = startMin
            it[Keys.LUNCH_END] = endMin
        }

    suspend fun setLunchMaxCreditMinutes(value: Int) =
        context.dataStore.edit { it[Keys.LUNCH_MAX_CREDIT] = value }

    suspend fun setWorkdayCountWindow(startMin: Int, endMin: Int) =
        context.dataStore.edit {
            it[Keys.WORKDAY_COUNT_START] = startMin
            it[Keys.WORKDAY_COUNT_END] = endMin
        }

    suspend fun setReminderTimes(morningMin: Int, eveningMin: Int) =
        context.dataStore.edit {
            it[Keys.MORNING_REMINDER] = morningMin
            it[Keys.EVENING_REMINDER] = eveningMin
        }

    suspend fun setRemindersEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }

    suspend fun setWorkdays(days: Set<DayOfWeek>) =
        context.dataStore.edit {
            it[Keys.WORKDAYS] = days.sortedBy(DayOfWeek::getValue).joinToString(",") { d -> d.value.toString() }
        }

    suspend fun setAnnualLeaveQuotaDays(value: Double) =
        context.dataStore.edit { it[Keys.ANNUAL_LEAVE_QUOTA] = value }

    /** Ghi đè toàn bộ settings từ bản sao lưu (US-31). */
    suspend fun restore(settings: AppSettings) =
        context.dataStore.edit {
            it[Keys.DEFAULT_WEEKLY_TARGET] = settings.defaultWeeklyTargetMinutes
            it[Keys.LUNCH_START] = settings.lunchWindowStartMin
            it[Keys.LUNCH_END] = settings.lunchWindowEndMin
            it[Keys.LUNCH_MAX_CREDIT] = settings.lunchMaxCreditMinutes
            it[Keys.WORKDAY_COUNT_START] = settings.workdayCountStartMin
            it[Keys.WORKDAY_COUNT_END] = settings.workdayCountEndMin
            it[Keys.MORNING_REMINDER] = settings.morningReminderMin
            it[Keys.EVENING_REMINDER] = settings.eveningReminderMin
            it[Keys.REMINDERS_ENABLED] = settings.remindersEnabled
            it[Keys.WORKDAYS] = settings.workdays.sortedBy(DayOfWeek::getValue)
                .joinToString(",") { d -> d.value.toString() }
            it[Keys.ANNUAL_LEAVE_QUOTA] = settings.annualLeaveQuotaDays
        }
}
