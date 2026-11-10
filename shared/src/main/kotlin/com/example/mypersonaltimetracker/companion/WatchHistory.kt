package com.example.mypersonaltimetracker.companion

import org.json.JSONArray
import org.json.JSONObject

const val HISTORY_PREFIX = "/tracker/v1/history/"

data class WatchHistoryDay(
    val date: String,
    val sessions: List<WatchSession>,
    val exception: String?,
    val officeMinutes: Int,
    val creditedMinutes: Int,
    val lunchExtraMinutes: Int,
)

/** Partition by month instead of putting an unbounded lifetime history in one Data Item. */
data class WatchHistoryMonth(
    val month: String,
    val updatedAt: Long,
    val zone: String,
    val days: List<WatchHistoryDay>,
    val weeklyTarget: Int,
    val workdays: List<Int>,
    val weekTargets: Map<String, Int>,
) {
    fun encode(): String = JSONObject().apply {
        put("month", month); put("updatedAt", updatedAt); put("zone", zone); put("weeklyTarget", weeklyTarget)
        put("workdays", JSONArray(workdays)); put("weekTargets", JSONObject(weekTargets))
        put("days", JSONArray().apply { days.forEach { d -> put(JSONObject().apply {
            put("date", d.date); put("exception", d.exception ?: JSONObject.NULL)
            put("office", d.officeMinutes); put("credited", d.creditedMinutes); put("lunchExtra", d.lunchExtraMinutes)
            put("sessions", JSONArray().apply { d.sessions.forEach { s ->
                put(JSONObject().put("in", s.inAt).put("out", s.outAt ?: JSONObject.NULL))
            } })
        }) } })
    }.toString()

    companion object {
        fun decode(raw: String): WatchHistoryMonth = JSONObject(raw).let { j ->
            val days = j.getJSONArray("days")
            val workdays = j.getJSONArray("workdays")
            val targets = j.getJSONObject("weekTargets")
            WatchHistoryMonth(j.getString("month"), j.getLong("updatedAt"), j.getString("zone"),
                List(days.length()) { index -> days.getJSONObject(index).let { d ->
                    val sessions = d.getJSONArray("sessions")
                    WatchHistoryDay(d.getString("date"), List(sessions.length()) { i ->
                        sessions.getJSONObject(i).let { s -> WatchSession(s.getLong("in"), if (s.isNull("out")) null else s.getLong("out")) }
                    }, if (d.isNull("exception")) null else d.getString("exception"),
                        d.getInt("office"), d.getInt("credited"), d.getInt("lunchExtra"))
                } }, j.getInt("weeklyTarget"), List(workdays.length()) { workdays.getInt(it) },
                targets.keys().asSequence().associateWith { targets.getInt(it) })
        }
    }
}
