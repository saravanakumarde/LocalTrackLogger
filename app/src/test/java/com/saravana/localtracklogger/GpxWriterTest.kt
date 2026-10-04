package com.saravana.localtracklogger

import com.saravana.localtracklogger.data.TrackPointEntity
import com.saravana.localtracklogger.export.GpxWriter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxWriterTest {
    private fun point(altitude: Double?) = TrackPointEntity(
        trackId = 1, timestamp = 0L, latitude = 48.5, longitude = 10.5,
        altitude = altitude, accuracy = null, speed = null, bearing = null
    )

    private fun gpx(name: String, vararg points: TrackPointEntity) =
        StringBuilder().also { GpxWriter.write(name, points.toList(), it) }.toString()

    @Test fun usesNameElementAndEscapesSpecialCharacters() {
        val out = gpx("A & B <\"x\">")
        assertTrue(out.contains("<name>A &amp; B &lt;&quot;x&quot;&gt;</name>"))
        assertFalse(out.contains("<n>"))
    }

    @Test fun writesElevationOnlyWhenKnown() {
        assertTrue(gpx("t", point(512.5)).contains("<ele>512.5</ele>"))
        assertFalse(gpx("t", point(null)).contains("<ele>"))
    }

    @Test fun writesTimestampInUtcIso() {
        assertTrue(gpx("t", point(null)).contains("<time>1970-01-01T00:00:00Z</time>"))
    }
}
