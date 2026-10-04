package com.saravana.localtracklogger.export

import android.content.ContentResolver
import android.net.Uri
import com.saravana.localtracklogger.data.TrackRepository
import com.saravana.localtracklogger.data.safeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Writer
import javax.inject.Inject

class GpxExporter @Inject constructor(private val repository: TrackRepository) {

    /** Writes the track to a location chosen by the user through the system file picker. */
    suspend fun exportToUri(trackId: Long, uri: Uri, resolver: ContentResolver) = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(uri) ?: error("Cannot open selected export destination")
        stream.bufferedWriter().use { write(trackId, it) }
    }

    /** Writes the track to the app cache so it can be handed to the share sheet. */
    suspend fun exportToCache(trackId: Long, cacheDir: File): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val track = requireNotNull(repository.track(trackId)) { "Track $trackId not found" }
        File(dir, "${safeFileName(track.name)}.gpx").also { file ->
            file.bufferedWriter().use { write(trackId, it) }
        }
    }

    private suspend fun write(trackId: Long, out: Writer) {
        val track = requireNotNull(repository.track(trackId)) { "Track $trackId not found" }
        GpxWriter.write(track.name, repository.points(trackId), out)
    }
}
