package com.example.mypersonaltimetracker.companion

import org.json.JSONArray
import org.json.JSONObject

const val STATE_PATH = "/tracker/v1/state"
const val QUEUE_PREFIX = "/tracker/v1/queue/"
const val ACK_PREFIX = "/tracker/v1/ack/"
const val REQUEST_PATH = "/tracker/v1/request"
const val PAYLOAD = "payload"

// A stop refers to an exact session start, never to whichever session happens to be open.
data class WatchAction(val id: String, val at: Long, val start: Boolean, val sessionStart: Long)
data class WatchSession(val inAt: Long, val outAt: Long?)
data class ActionResult(val sessions: List<WatchSession>, val error: String? = null)

object WatchActions {
    fun shortcut(sessions: List<WatchSession>, start: Boolean, expectedStart: Long?, at: Long, id: String): WatchAction? {
        val running = sessions.firstOrNull { it.outAt == null }
        if (start && running != null || !start && (running == null || running.inAt != expectedStart)) return null
        return WatchAction(id, at, start, running?.inAt ?: at)
    }

    fun apply(sessions: List<WatchSession>, action: WatchAction, now: Long): ActionResult {
        fun reject(message: String) = ActionResult(sessions, message)
        if (action.at > now + 60_000 || action.at < 0 || action.sessionStart < 0) {
            return reject("Giờ trên đồng hồ không đúng. Kiểm tra trên điện thoại.")
        }
        if (action.start) {
            if (action.sessionStart != action.at) return reject("Giờ bắt đầu phiên không hợp lệ.")
            // The snapshot may already contain this action while its acknowledgement is in flight.
            if (sessions.any { it.inAt == action.at }) return ActionResult(sessions)
            if (sessions.any { it.outAt == null || it.outAt > action.at }) {
                return reject("Lần vào làm trùng với phiên trên điện thoại. Kiểm tra trên điện thoại.")
            }
            return ActionResult(sessions + WatchSession(action.at, null))
        }
        val target = sessions.firstOrNull { it.inAt == action.sessionStart }
            ?: return reject("Phiên đã được sửa trên điện thoại. Kiểm tra trên điện thoại.")
        if (target.outAt == action.at) return ActionResult(sessions)
        if (target.outAt != null || action.at < target.inAt) {
            return reject("Lần ra ngoài không khớp với phiên đã sửa. Kiểm tra trên điện thoại.")
        }
        if (sessions.any { it != target && it.inAt >= target.inAt && it.inAt < action.at }) {
            return reject("Giờ ra ngoài chồng lên phiên khác. Kiểm tra trên điện thoại.")
        }
        return ActionResult(sessions.map { if (it == target) it.copy(outAt = action.at) else it })
    }
}

data class WatchState(
    val updatedAt: Long,
    val date: String,
    val zone: String,
    val sessions: List<WatchSession>,
    val exception: String?,
    val target: Int,
    val lunchStart: Int,
    val lunchEnd: Int,
    val lunchDeduct: Int,
    val countStart: Int,
    val countEnd: Int,
) {
    fun encode(): String = JSONObject().apply {
        put("updatedAt", updatedAt); put("date", date); put("zone", zone)
        put("sessions", JSONArray().apply { sessions.forEach { s ->
            put(JSONObject().put("in", s.inAt).put("out", s.outAt ?: JSONObject.NULL))
        } })
        put("exception", exception ?: JSONObject.NULL); put("target", target)
        put("lunchStart", lunchStart); put("lunchEnd", lunchEnd); put("lunchDeduct", lunchDeduct)
        put("countStart", countStart); put("countEnd", countEnd)
    }.toString()

    companion object {
        fun decode(raw: String): WatchState = JSONObject(raw).let { j ->
            val array = j.getJSONArray("sessions")
            WatchState(j.getLong("updatedAt"), j.getString("date"), j.getString("zone"),
                List(array.length()) { i -> array.getJSONObject(i).let {
                    WatchSession(it.getLong("in"), if (it.isNull("out")) null else it.getLong("out"))
                } }, if (j.isNull("exception")) null else j.getString("exception"),
                j.getInt("target"), j.getInt("lunchStart"), j.getInt("lunchEnd"),
                j.getInt("lunchDeduct"), j.getInt("countStart"), j.getInt("countEnd"))
        }
    }
}

fun encodeActions(actions: List<WatchAction>): String = JSONArray().apply {
    actions.forEach { a -> put(JSONObject().apply {
        put("id", a.id); put("at", a.at); put("start", a.start); put("sessionStart", a.sessionStart)
    }) }
}.toString()

fun decodeActions(raw: String): List<WatchAction> = JSONArray(raw).let { array ->
    List(array.length()) { i -> array.getJSONObject(i).let {
        WatchAction(it.getString("id"), it.getLong("at"), it.getBoolean("start"), it.getLong("sessionStart"))
    } }
}
