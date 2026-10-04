package com.saravana.localtracklogger.export

import com.saravana.localtracklogger.data.TrackPointEntity
import java.time.Instant

/** Pure GPX 1.1 serialisation, kept free of Android types so it can be unit-tested. */
object GpxWriter {
    fun write(name: String, points: List<TrackPointEntity>, out: Appendable) {
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<gpx version=\"1.1\" creator=\"Local Track Logger\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        out.append("<trk><name>").append(escape(name)).append("</name><trkseg>\n")
        points.forEach { p ->
            out.append("<trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">")
            p.altitude?.let { out.append("<ele>$it</ele>") }
            out.append("<time>${Instant.ofEpochMilli(p.timestamp)}</time></trkpt>\n")
        }
        out.append("</trkseg></trk></gpx>\n")
    }

    fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
