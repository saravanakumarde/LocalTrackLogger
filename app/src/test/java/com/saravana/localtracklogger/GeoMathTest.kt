package com.saravana.localtracklogger

import com.saravana.localtracklogger.data.haversineMeters
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test fun samePointIsZero() {
        assertEquals(0.0, haversineMeters(48.37, 10.89, 48.37, 10.89), 1e-9)
    }

    @Test fun oneDegreeOfLatitudeIsAbout111km() {
        assertEquals(111_195.0, haversineMeters(0.0, 0.0, 1.0, 0.0), 100.0)
    }

    @Test fun distanceIsSymmetric() {
        val ab = haversineMeters(48.37, 10.89, 52.52, 13.40)
        val ba = haversineMeters(52.52, 13.40, 48.37, 10.89)
        assertEquals(ab, ba, 1e-6)
    }
}
