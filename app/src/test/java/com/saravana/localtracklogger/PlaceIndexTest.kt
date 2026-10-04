package com.saravana.localtracklogger

import com.saravana.localtracklogger.search.PlaceIndex
import com.saravana.localtracklogger.search.normalizePlaceName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceIndexTest {
    private val index = PlaceIndex(
        listOf(
            "Augsburg\t48.3715\t10.8985\tDE",
            "Munich\t48.1374\t11.5755\tDE",
            "Waldmünchen\t49.3780\t12.7040\tDE",
            "Fürth\t49.6500\t7.0000\tDE",
            "Fürth\t49.4750\t10.9880\tDE",
            "Augsburg Hochzoll\t48.3500\t10.9300\tDE"
        )
    )

    @Test fun exactMatchRanksFirst() {
        assertEquals("Augsburg", index.search("augsburg").first().name)
    }

    @Test fun ignoresAccentsAndCase() {
        assertEquals("furth", normalizePlaceName("Fürth"))
        assertEquals("Fürth", index.search("FURTH").first().name)
    }

    @Test fun localSpellingFindsEnglishName() {
        assertEquals("Munich", index.search("München").first().name)
    }

    @Test fun nearestWinsAmongSameNames() {
        val hit = index.search("Fürth", nearLat = 49.45, nearLon = 11.0).first()
        assertEquals(49.475, hit.latitude, 1e-6)
    }

    @Test fun tooShortQueryReturnsNothing() {
        assertTrue(index.search("a").isEmpty())
    }
}
