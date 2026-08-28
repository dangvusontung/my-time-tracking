package com.example.mypersonaltimetracker.data

import com.example.mypersonaltimetracker.domain.DayType
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** Parsed content of a backup file (see [Backup]). */
data class BackupData(
    val sessions: List<SessionEntity>,
    val exceptions: List<DayExceptionEntity>,
    val weekTargets: List<WeekTargetEntity>,
    val dayNotes: List<DayNoteEntity>,
    val settings: AppSettings?,
)

/**
 * US-31: sao lưu/khôi phục toàn bộ dữ liệu dưới dạng một file JSON:
 * `{"version":1, "sessions":[...], "exceptions":[...], "weekTargets":[...], "dayNotes":[...], "settings":{...}}`
 * org.json chỉ có trên Android, nên tầng này không được test bằng JVM unit test.
 */
object Backup {

    const val VERSION = 1

    fun build(
        sessions: List<SessionEntity>,
        exceptions: List<DayExceptionEntity>,
        weekTargets: List<WeekTargetEntity>,
        dayNotes: List<DayNoteEntity>,
        settings: AppSettings,
    ): String {
        val root = JSONObject()
        root.put("version", VERSION)

        root.put("sessions", JSONArray().apply {
            sessions.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("inAt", s.inAt)
                    put("outAt", if (s.outAt == null) JSONObject.NULL else s.outAt)
                    put("isEstimated", s.isEstimated)
                })
            }
        })

        root.put("exceptions", JSONArray().apply {
            exceptions.forEach { e ->
                put(JSONObject().apply {
                    put("date", e.date)
                    put("type", e.type.name)
                    put("note", e.note ?: JSONObject.NULL)
                })
            }
        })

        root.put("weekTargets", JSONArray().apply {
            weekTargets.forEach { t ->
                put(JSONObject().apply {
                    put("weekStart", t.weekStart)
                    put("targetMinutes", t.targetMinutes)
                })
            }
        })

        root.put("dayNotes", JSONArray().apply {
            dayNotes.forEach { n ->
                put(JSONObject().apply {
                    put("date", n.date)
                    put("note", n.note)
                })
            }
        })

        root.put("settings", JSONObject().apply {
            put("defaultWeeklyTargetMinutes", settings.defaultWeeklyTargetMinutes)
            put("lunchWindowStartMin", settings.lunchWindowStartMin)
            put("lunchWindowEndMin", settings.lunchWindowEndMin)
            put("lunchMaxCreditMinutes", settings.lunchMaxCreditMinutes)
            put("morningReminderMin", settings.morningReminderMin)
            put("eveningReminderMin", settings.eveningReminderMin)
            put("remindersEnabled", settings.remindersEnabled)
            put("workdays", settings.workdays.sortedBy(DayOfWeek::getValue).joinToString(",") { it.value.toString() })
            put("annualLeaveQuotaDays", settings.annualLeaveQuotaDays)
        })

        return root.toString(2)
    }

    /** @throws IllegalArgumentException khi version không hỗ trợ hoặc dữ liệu hỏng. */
    fun parse(json: String): BackupData {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("File không phải JSON hợp lệ", e)
        }
        val version = root.optInt("version", -1)
        if (version != VERSION) {
            throw IllegalArgumentException("Phiên bản sao lưu không hỗ trợ: $version")
        }

        val sessions = root.optJSONArray("sessions").toList { o ->
            SessionEntity(
                id = o.optLong("id", 0),
                inAt = o.getLong("inAt"),
                outAt = if (o.isNull("outAt")) null else o.getLong("outAt"),
                isEstimated = o.optBoolean("isEstimated", false),
            )
        }
        val exceptions = root.optJSONArray("exceptions").toList { o ->
            DayExceptionEntity(
                date = o.getString("date"),
                type = DayType.valueOf(o.getString("type")),
                note = if (o.isNull("note")) null else o.getString("note"),
            )
        }
        val weekTargets = root.optJSONArray("weekTargets").toList { o ->
            WeekTargetEntity(
                weekStart = o.getString("weekStart"),
                targetMinutes = o.getInt("targetMinutes"),
            )
        }
        val dayNotes = root.optJSONArray("dayNotes").toList { o ->
            DayNoteEntity(
                date = o.getString("date"),
                note = o.getString("note"),
            )
        }
        val settings = root.optJSONObject("settings")?.let { o ->
            AppSettings(
                defaultWeeklyTargetMinutes = o.optInt("defaultWeeklyTargetMinutes", 2400),
                lunchWindowStartMin = o.optInt("lunchWindowStartMin", 690),
                lunchWindowEndMin = o.optInt("lunchWindowEndMin", 840),
                lunchMaxCreditMinutes = o.optInt("lunchMaxCreditMinutes", 60),
                morningReminderMin = o.optInt("morningReminderMin", 510),
                eveningReminderMin = o.optInt("eveningReminderMin", 1050),
                remindersEnabled = o.optBoolean("remindersEnabled", true),
                workdays = o.optString("workdays", "")
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .mapNotNull { v -> DayOfWeek.entries.firstOrNull { it.value == v } }
                    .toSet()
                    .takeIf { it.isNotEmpty() }
                    ?: DEFAULT_WORKDAYS,
                annualLeaveQuotaDays = o.optDouble("annualLeaveQuotaDays", 12.0),
            )
        }
        return BackupData(sessions, exceptions, weekTargets, dayNotes, settings)
    }

    private fun <T> JSONArray?.toList(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { i ->
            try {
                map(getJSONObject(i))
            } catch (e: Exception) {
                throw IllegalArgumentException("Dữ liệu sao lưu bị hỏng ở mục thứ ${i + 1}", e)
            }
        }
    }
}
