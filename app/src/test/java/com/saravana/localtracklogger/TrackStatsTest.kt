package com.saravana.localtracklogger

import com.saravana.localtracklogger.data.TrackEntity
import com.saravana.localtracklogger.data.TrackPointEntity
import com.saravana.localtracklogger.data.ascentDescent
import com.saravana.localtracklogger.data.computeStats
import com.saravana.localtracklogger.data.safeFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackStatsTest {
    private fun point(i: Int, altitude: Double? = null) = TrackPointEntity(
        trackId = 1, timestamp = i * 60_000L, latitude = 0.0, longitude = i * 0.001,
        altitude = altitude, accuracy = null, speed = null, bearing = null
    )

    @Test fun ascentIgnoresNoiseBelowThreshold() {
        val (up, down) = ascentDescent(listOf(0.0, 1.0, 2.0, 10.0, 9.0, 20.0, 5.0))
        assertEquals(20.0, up, 1e-9)
        assertEquals(15.0, down, 1e-9)
    }

    @Test fun computesDistanceDurationAndSpeed() {
        val track = TrackEntity(id = 1, name = "t", startedAt = 0, endedAt = 120_000)
        val stats = computeStats(track, listOf(point(0), point(1), point(2)), nowMillis = 999_999)
        assertEquals(222.4, stats.distanceMeters, 1.0)
        assertEquals(120_000L, stats.durationMs)
        assertEquals(120_000L, stats.movingMs)
        assertEquals(6.67, stats.avgSpeedKmh!!, 0.1)
        assertNull(stats.ascentM)
    }

    @Test fun activeTrackUsesCurrentTimeForDuration() {
        val track = TrackEntity(id = 1, name = "t", startedAt = 1_000, endedAt = null)
        assertEquals(4_000L, computeStats(track, emptyList(), nowMillis = 5_000).durationMs)
    }

    @Test fun fileNamesAreSanitised() {
        assertEquals("Track_3_Oct_2026_09_18_18", safeFileName("Track 3 Oct 2026 09:18:18"))
        assertEquals("track", safeFileName("///"))
    }
}
