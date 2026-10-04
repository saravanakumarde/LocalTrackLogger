package com.saravana.localtracklogger.ui

import java.util.Locale

fun formatDistance(meters: Double): String =
    if (meters < 1000) String.format(Locale.getDefault(), "%.0f m", meters)
    else String.format(Locale.getDefault(), "%.2f km", meters / 1000)

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
}

fun formatSpeed(kmh: Double?): String =
    kmh?.let { String.format(Locale.getDefault(), "%.1f km/h", it) } ?: "–"

fun formatAltitude(m: Double?): String =
    m?.let { String.format(Locale.getDefault(), "%.0f m", it) } ?: "–"

fun formatSize(bytes: Long): String =
    String.format(Locale.getDefault(), "%.0f MB", bytes / 1_048_576.0)
