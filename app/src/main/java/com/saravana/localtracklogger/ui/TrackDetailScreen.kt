package com.saravana.localtracklogger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saravana.localtracklogger.data.TrackEntity
import com.saravana.localtracklogger.data.TrackPointEntity
import com.saravana.localtracklogger.data.TrackRepository
import com.saravana.localtracklogger.data.TrackStats
import com.saravana.localtracklogger.data.computeStats
import com.saravana.localtracklogger.data.safeFileName
import com.saravana.localtracklogger.export.GpxExporter
import com.saravana.localtracklogger.maps.OfflineMapStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TrackDetail(val track: TrackEntity, val points: List<TrackPointEntity>, val stats: TrackStats)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TrackRepository,
    private val exporter: GpxExporter,
    mapStore: OfflineMapStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val trackId: Long = checkNotNull(savedState.get<Long>("trackId"))

    val mapInfo = mapStore.map

    /** Re-emits while the track is being recorded, so the screen follows live. */
    val detail = combine(repository.observeTrack(trackId), repository.observePoints(trackId)) { track, points ->
        track?.let { TrackDetail(it, points, computeStats(it, points, System.currentTimeMillis())) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun exportTo(uri: Uri, context: Context) = exporter.exportToUri(trackId, uri, context.contentResolver)
    suspend fun exportToCache(): File = exporter.exportToCache(trackId, appContext.cacheDir)
    suspend fun delete() = repository.deleteTrack(trackId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(onBack: () -> Unit, vm: TrackDetailViewModel = hiltViewModel()) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val mapInfo by vm.mapInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri != null) scope.launch {
            val message = try {
                vm.exportTo(uri, context)
                "GPX file saved"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            snackbar.showSnackbar(message)
        }
    }

    val current = detail
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(current?.track?.name ?: "Track", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { saveLauncher.launch("${safeFileName(current.track.name)}.gpx") }) {
                            Icon(Icons.Default.SaveAlt, "Save as GPX")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    shareGpx(context, vm.exportToCache())
                                } catch (e: Exception) {
                                    snackbar.showSnackbar("Share failed: ${e.message}")
                                }
                            }
                        }) { Icon(Icons.Default.Share, "Share GPX") }
                        IconButton(onClick = { confirmDelete = true }, enabled = !current.track.active) {
                            Icon(Icons.Default.Delete, "Delete track")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                val mapFile = mapInfo?.file
                key(mapFile?.path) {
                    TrackMap(listOf(current.points), mapFile, Modifier.fillMaxWidth().height(320.dp))
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (mapFile == null) {
                        Text(
                            "No offline map imported. Import one on the main screen to see roads and terrain.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    StatsGrid(current)
                    Charts(current.stats)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this track?") },
            text = { Text("The track and all its points will be permanently removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        vm.delete()
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun shareGpx(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share GPX"))
}

@Composable
private fun StatsGrid(detail: TrackDetail) {
    val s = detail.stats
    val items = buildList {
        add("Distance" to formatDistance(s.distanceMeters))
        add("Duration" to formatDuration(s.durationMs))
        add("Moving time" to formatDuration(s.movingMs))
        add("Avg speed (moving)" to formatSpeed(s.avgSpeedKmh))
        add("Max speed" to formatSpeed(s.maxSpeedKmh))
        add("Points" to detail.points.size.toString())
        if (s.ascentM != null) {
            add("Ascent" to formatAltitude(s.ascentM))
            add("Descent" to formatAltitude(s.descentM))
            add("Lowest" to formatAltitude(s.minAltM))
            add("Highest" to formatAltitude(s.maxAltM))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (s.ascentM == null) {
            Text(
                "Elevation is unavailable: this device does not report sea-level altitude (needs Android 14 or newer).",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Charts(stats: TrackStats) {
    val endLabel = String.format(java.util.Locale.getDefault(), "%.2f km", stats.distancesKm.lastOrNull() ?: 0.0)
    val altIndices = stats.altitudes.indices.filter { stats.altitudes[it] != null }
    if (altIndices.size > 1) {
        LineChart(
            title = "Elevation",
            xs = altIndices.map { stats.distancesKm[it] },
            ys = altIndices.map { stats.altitudes[it]!! },
            formatY = ::formatAltitude,
            xEndLabel = endLabel
        )
    }
    LineChart(
        title = "Speed",
        xs = stats.distancesKm,
        ys = stats.speedsKmh,
        formatY = ::formatSpeed,
        xEndLabel = endLabel
    )
}
