package com.example.mypersonaltimetracker.domain

/** Official daily target, separate from the optional 8h30 milestone. */
data class DailyGoalProgress(val creditedMinutes: Int, val targetMinutes: Int) {
    val hasTarget: Boolean get() = targetMinutes > 0
    val fraction: Float get() = if (hasTarget) (creditedMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f) else 0f
    val percent: Int? get() = if (hasTarget) (creditedMinutes.coerceAtLeast(0).toLong() * 100 / targetMinutes).toInt() else null
    val remainingMinutes: Int? get() = if (hasTarget) (targetMinutes - creditedMinutes.coerceAtLeast(0)).coerceAtLeast(0) else null
    val extraMinutes: Int get() = if (hasTarget) (creditedMinutes - targetMinutes).coerceAtLeast(0) else 0
}
