package com.example.mypersonaltimetracker.ui.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "7h30", "45p", "8h" */
fun formatMinutes(minutes: Int): String {
    val abs = kotlin.math.abs(minutes)
    val h = abs / 60
    val m = abs % 60
    val body = when {
        h > 0 && m > 0 -> "${h}h${m.toString().padStart(2, '0')}"
        h > 0 -> "${h}h"
        else -> "${m}p"
    }
    return if (minutes < 0) "-$body" else body
}

fun formatTime(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(timeFormatter)

fun formatDate(date: LocalDate): String = date.format(dateFormatter)

fun dayOfWeekVi(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "Thứ Hai"
    DayOfWeek.TUESDAY -> "Thứ Ba"
    DayOfWeek.WEDNESDAY -> "Thứ Tư"
    DayOfWeek.THURSDAY -> "Thứ Năm"
    DayOfWeek.FRIDAY -> "Thứ Sáu"
    DayOfWeek.SATURDAY -> "Thứ Bảy"
    DayOfWeek.SUNDAY -> "Chủ nhật"
}

fun dayOfWeekShortVi(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "CN"
    else -> "T${dayOfWeek.value + 1}"
}

fun formatDateLongVi(date: LocalDate): String = "${dayOfWeekVi(date.dayOfWeek)}, ${formatDate(date)}"

fun minuteOfDayToTime(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    return "%02d:%02d".format(h, m)
}
