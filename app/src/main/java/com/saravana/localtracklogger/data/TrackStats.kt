package com.saravana.localtracklogger.data

import kotlin.math.max
import kotlin.math.min

data class TrackStats(
    val distanceMeters: Double,
    val durationMs: Long,
    val movingMs: Long,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val ascentM: Double?,
    val descentM: Double?,
    val minAltM: Double?,
    val maxAltM: Double?,
    /** Cumulative distance in km for each point. */
    val distancesKm: List<Double>,
    /** Smoothed speed in km/h for each point. */
    val speedsKmh: List<Double>,
    /** Sea-level altitude in meters for each point, null where unknown. */
    val altitudes: List<Double?>
)

private const val MOVING_THRESHOLD_MS = 0.5 // m/s; slower segments count as standing still
private const val ELEVATION_NOISE_M = 3.0

fun computeStats(track: TrackEntity, points: List<TrackPointEntity>, nowMillis: Long): TrackStats {
    val n = points.size
    val cumulative = DoubleArray(n)
    val rawSpeed = DoubleArray(n)
    var movingMs = 0L
    for (i in 1 until n) {
        val a = points[i - 1]
        val b = points[i]
        val d = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        val dt = b.timestamp - a.timestamp
        cumulative[i] = cumulative[i - 1] + d
        val segment = if (dt > 0) d / (dt / 1000.0) else 0.0
        rawSpeed[i] = b.speed?.toDouble() ?: segment
        if (segment >= MOVING_THRESHOLD_MS && dt > 0) movingMs += dt
    }
    if (n > 1) rawSpeed[0] = rawSpeed[1]
    val speeds = smooth(rawSpeed.map { it * 3.6 }, radius = 2)

    val knownAlts = points.mapNotNull { it.altitude }
    val (ascent, descent) = if (knownAlts.size > 1) ascentDescent(knownAlts) else (null to null)

    val total = if (n > 0) cumulative[n - 1] else 0.0
    val end = track.endedAt ?: nowMillis
    return TrackStats(
        distanceMeters = total,
        durationMs = max(0L, end - track.startedAt),
        movingMs = movingMs,
        avgSpeedKmh = if (movingMs > 0) total / (movingMs / 1000.0) * 3.6 else null,
        maxSpeedKmh = if (n > 1) speeds.max() else null,
        ascentM = ascent,
        descentM = descent,
        minAltM = knownAlts.minOrNull(),
        maxAltM = knownAlts.maxOrNull(),
        distancesKm = cumulative.map { it / 1000.0 },
        speedsKmh = speeds,
        altitudes = points.map { it.altitude }
    )
}

/**
 * Total climb and descent, ignoring changes smaller than [threshold] meters so GPS noise
 * does not accumulate into phantom elevation gain.
 */
fun ascentDescent(altitudes: List<Double>, threshold: Double = ELEVATION_NOISE_M): Pair<Double, Double> {
    if (altitudes.isEmpty()) return 0.0 to 0.0
    var reference = altitudes.first()
    var up = 0.0
    var down = 0.0
    for (a in altitudes) {
        when {
            a - reference >= threshold -> { up += a - reference; reference = a }
            reference - a >= threshold -> { down += reference - a; reference = a }
        }
    }
    return up to down
}

fun smooth(values: List<Double>, radius: Int): List<Double> = values.indices.map { i ->
    val from = max(0, i - radius)
    val to = min(values.lastIndex, i + radius)
    var sum = 0.0
    for (j in from..to) sum += values[j]
    sum / (to - from + 1)
}

/** Replaces characters that are unsafe in file names. */
fun safeFileName(name: String): String =
    name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "track" }
