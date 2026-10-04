package com.saravana.localtracklogger.maps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

data class MapInfo(val file: File, val name: String, val sizeBytes: Long)

/**
 * Keeps one Mapsforge (.map) file in app-private storage. The file is imported through the
 * system file picker, so the app itself never needs the Internet permission.
 */
@Singleton
class OfflineMapStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "maps")
    private val mapFile get() = File(dir, "offline.map")
    private val nameFile get() = File(dir, "offline.name")

    private val _map = MutableStateFlow(load())
    val map: StateFlow<MapInfo?> = _map.asStateFlow()

    private fun load(): MapInfo? =
        if (mapFile.exists()) MapInfo(mapFile, nameFile.takeIf { it.exists() }?.readText() ?: "offline.map", mapFile.length())
        else null

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "import.tmp")
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected file")
                input.use { i -> tmp.outputStream().use { o -> i.copyTo(o, bufferSize = 1 shl 16) } }
                require(isMapsforgeFile(tmp)) { "This is not a Mapsforge .map file" }
                mapFile.delete()
                check(tmp.renameTo(mapFile)) { "Could not store the map" }
                nameFile.writeText(displayName(uri))
                _map.value = load()
            } finally {
                tmp.delete()
            }
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        mapFile.delete()
        nameFile.delete()
        _map.value = null
    }

    private fun isMapsforgeFile(file: File): Boolean {
        val magic = "mapsforge binary OSM".toByteArray(Charsets.US_ASCII)
        if (file.length() < magic.size) return false
        val header = ByteArray(magic.size)
        RandomAccessFile(file, "r").use { it.readFully(header) }
        return header.contentEquals(magic)
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "offline.map"
}
