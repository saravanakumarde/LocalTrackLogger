package com.saravana.localtracklogger.data

import android.location.Location
import android.os.Build
import kotlinx.coroutines.flow.Flow

class TrackRepository(private val dao: TrackDao) {
    fun observeTracks(): Flow<List<TrackEntity>> = dao.observeTracks()

    /** Starts a new track. Any track still marked active at this point is a leftover and is closed first. */
    suspend fun start(): Long {
        closeStaleTracks(Long.MAX_VALUE)
        val name = "Track ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}"
        return dao.insertTrack(TrackEntity(name = name, startedAt = System.currentTimeMillis()))
    }

    suspend fun stop() {
        val track = dao.activeTrack() ?: return
        dao.finishTrack(track.id, System.currentTimeMillis())
    }

    /**
     * Closes tracks left "active" by a killed process or crash. Only tracks that started
     * before [startedBefore] are touched, so a session started later is never affected.
     */
    suspend fun closeStaleTracks(startedBefore: Long) {
        dao.activeTracksStartedBefore(startedBefore).forEach { track ->
            val end = dao.lastPoint(track.id)?.timestamp ?: track.startedAt
            dao.finishTrack(track.id, end)
        }
    }

    /** Must be called from a single coroutine at a time (the service serialises calls). */
    suspend fun record(location: Location) {
        val track = dao.activeTrack() ?: return
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return
        val previous = dao.lastPoint(track.id)
        val moved = previous?.let {
            haversineMeters(it.latitude, it.longitude, location.latitude, location.longitude)
        } ?: 0.0
        if (previous != null && location.time - previous.timestamp < MIN_INTERVAL_MS && moved < MIN_DISTANCE_M) return
        dao.addPoint(
            TrackPointEntity(
                trackId = track.id,
                timestamp = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = seaLevelAltitude(location),
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null
            ),
            moved
        )
    }

    fun observeTrack(trackId: Long): Flow<TrackEntity?> = dao.observeTrack(trackId)
    fun observePoints(trackId: Long): Flow<List<TrackPointEntity>> = dao.observePoints(trackId)
    suspend fun allTracks() = dao.allTracks()

    /** Stores an imported track. Returns false when a track with the same start time already exists. */
    suspend fun importTrack(name: String, points: List<TrackPointEntity>): Boolean {
        if (points.isEmpty()) return false
        val startedAt = points.first().timestamp
        if (dao.countStartedAt(startedAt) > 0) return false
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
        }
        dao.importTrack(
            TrackEntity(
                name = name, startedAt = startedAt, endedAt = points.last().timestamp,
                distanceMeters = distance, pointCount = points.size, active = false
            ),
            points
        )
        return true
    }

    fun observeAllPoints(): Flow<List<TrackPointEntity>> = dao.observeAllPoints()
    suspend fun deleteTrack(trackId: Long) = dao.deleteTrack(trackId)
    suspend fun deleteAllFinished() = dao.deleteAllFinished()
    suspend fun points(trackId: Long) = dao.points(trackId)
    suspend fun track(trackId: Long) = dao.track(trackId)

    /**
     * Location.getAltitude() is height above the WGS84 ellipsoid, which differs from sea level
     * by tens of meters. Only the mean-sea-level value (Android 14+) is stored; otherwise null
     * so a wrong elevation is never written to exports.
     */
    private fun seaLevelAltitude(location: Location): Double? =
        if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude()) location.mslAltitudeMeters else null

    private companion object {
        const val MAX_ACCURACY_M = 50f
        const val MIN_INTERVAL_MS = 3_000L
        const val MIN_DISTANCE_M = 5.0
    }
}
