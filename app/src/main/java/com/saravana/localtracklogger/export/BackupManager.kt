package com.saravana.localtracklogger.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.saravana.localtracklogger.data.TrackPointEntity
import com.saravana.localtracklogger.data.TrackRepository
import com.saravana.localtracklogger.data.safeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class ImportSummary(val imported: Int, val duplicates: Int, val failedFiles: Int)

class BackupManager @Inject constructor(private val repository: TrackRepository) {

    /** Writes every track as one GPX file inside a ZIP. Returns the number of tracks exported. */
    suspend fun exportAll(uri: Uri, resolver: ContentResolver): Int = withContext(Dispatchers.IO) {
        val tracks = repository.allTracks()
        val stream = resolver.openOutputStream(uri) ?: error("Cannot open selected export destination")
        ZipOutputStream(stream.buffered()).use { zip ->
            tracks.forEach { track ->
                zip.putNextEntry(ZipEntry("${safeFileName(track.name)}_${track.id}.gpx"))
                val writer = zip.writer(Charsets.UTF_8)
                GpxWriter.write(track.name, repository.points(track.id), writer)
                writer.flush()
                zip.closeEntry()
            }
        }
        tracks.size
    }

    /** Imports .gpx files and .zip archives of .gpx files (such as the export above). */
    suspend fun import(uris: List<Uri>, resolver: ContentResolver): ImportSummary = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        var failed = 0
        for (uri in uris) {
            try {
                val streams = mutableListOf<ParsedTrack>()
                val input = resolver.openInputStream(uri) ?: error("Cannot open file")
                input.use {
                    if (displayName(resolver, uri).endsWith(".zip", ignoreCase = true)) {
                        ZipInputStream(it).use { zip -> readZip(zip, streams) }
                    } else {
                        streams += GpxImporter.parse(it)
                    }
                }
                streams.forEach { track ->
                    if (store(track)) imported++ else duplicates++
                }
            } catch (_: Exception) {
                failed++
            }
        }
        ImportSummary(imported, duplicates, failed)
    }

    private fun readZip(zip: ZipInputStream, into: MutableList<ParsedTrack>) {
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".gpx", ignoreCase = true)) {
                into += GpxImporter.parse(zip)
            }
            entry = zip.nextEntry
        }
    }

    private suspend fun store(track: ParsedTrack): Boolean {
        val base = System.currentTimeMillis()
        val points = track.points.mapIndexed { i, p ->
            TrackPointEntity(
                trackId = 0,
                timestamp = p.timeMillis ?: (base + i * 1000L),
                latitude = p.latitude,
                longitude = p.longitude,
                altitude = p.altitude,
                accuracy = null,
                speed = null,
                bearing = null
            )
        }
        return repository.importTrack(track.name, points)
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
}
