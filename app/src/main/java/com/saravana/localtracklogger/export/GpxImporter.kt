package com.saravana.localtracklogger.export

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.OffsetDateTime

data class ParsedPoint(val latitude: Double, val longitude: Double, val altitude: Double?, val timeMillis: Long?)
data class ParsedTrack(val name: String, val points: List<ParsedPoint>)

/** Reads tracks (<trk>/<trkpt>) from a GPX file, including files exported by other apps. */
object GpxImporter {
    fun parse(input: InputStream): List<ParsedTrack> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val tracks = mutableListOf<ParsedTrack>()
        var inTrack = false
        var inPoint = false
        var name: String? = null
        var points = mutableListOf<ParsedPoint>()
        var lat = 0.0
        var lon = 0.0
        var ele: Double? = null
        var time: Long? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "trk" -> { inTrack = true; name = null; points = mutableListOf() }
                    "trkpt" -> {
                        inPoint = true
                        lat = parser.getAttributeValue(null, "lat").toDouble()
                        lon = parser.getAttributeValue(null, "lon").toDouble()
                        ele = null
                        time = null
                    }
                    "name" -> if (inTrack && !inPoint) name = parser.nextText().trim()
                    "ele" -> if (inPoint) ele = parser.nextText().trim().toDoubleOrNull()
                    "time" -> if (inPoint) time = parseTime(parser.nextText())
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "trkpt" -> { points.add(ParsedPoint(lat, lon, ele, time)); inPoint = false }
                    "trk" -> {
                        if (points.isNotEmpty()) tracks.add(ParsedTrack(name?.takeIf { it.isNotEmpty() } ?: "Imported track", points))
                        inTrack = false
                    }
                }
            }
            event = parser.next()
        }
        return tracks
    }

    private fun parseTime(text: String): Long? =
        runCatching { OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli() }.getOrNull()
}
