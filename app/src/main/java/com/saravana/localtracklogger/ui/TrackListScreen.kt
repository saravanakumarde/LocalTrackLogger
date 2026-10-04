package com.saravana.localtracklogger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saravana.localtracklogger.data.TrackEntity
import com.saravana.localtracklogger.data.TrackPointEntity
import com.saravana.localtracklogger.data.TrackRepository
import com.saravana.localtracklogger.maps.MapInfo
import com.saravana.localtracklogger.maps.OfflineMapStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val repository: TrackRepository,
    mapStore: OfflineMapStore
) : ViewModel() {
    val tracks = repository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every track's points (thinned out) for the overview map. */
    val overview: StateFlow<List<List<TrackPointEntity>>> = repository.observeAllPoints()
        .map { all -> all.groupBy { it.trackId }.values.map { thin(it) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val map: StateFlow<MapInfo?> = mapStore.map

    fun deleteTrack(id: Long) {
        viewModelScope.launch { repository.deleteTrack(id) }
    }

    private fun thin(points: List<TrackPointEntity>, max: Int = 600): List<TrackPointEntity> {
        if (points.size <= max) return points
        val step = (points.size + max - 1) / max
        return points.filterIndexed { i, _ -> i % step == 0 || i == points.lastIndex }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: TrackerViewModel = hiltViewModel()
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val map by vm.map.collectAsStateWithLifecycle()
    val recording = tracks.any { it.active }
    var trackToDelete by remember { mutableStateOf<TrackEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Track Logger") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = !recording) { Text("Start tracking") }
                OutlinedButton(onClick = onStop, enabled = recording) { Text("Stop tracking") }
            }
            key(map?.file?.path) {
                TrackMap(overview, map?.file, Modifier.weight(1f).fillMaxWidth())
            }
            var expanded by rememberSaveable { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recorded tracks (${tracks.size})" + if (recording) " • Recording" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (expanded) "Collapse track list" else "Expand track list"
                )
            }
            AnimatedVisibility(visible = expanded) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (tracks.isEmpty()) {
                        item { Text("No tracks yet. Press “Start tracking” and go for a walk.") }
                    }
                    items(tracks, key = { it.id }) { track ->
                        TrackCard(track, onClick = { onOpen(track.id) }, onDelete = { trackToDelete = track })
                    }
                }
            }
        }
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Delete this track?") },
            text = { Text("“${track.name}” and all its points will be permanently removed from this device.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTrack(track.id); trackToDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { trackToDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackCard(track: TrackEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val durationMs = (track.endedAt ?: System.currentTimeMillis()) - track.startedAt
    val avgKmh = if (durationMs > 0) track.distanceMeters / (durationMs / 1000.0) * 3.6 else null
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatDistance(track.distanceMeters)} • ${formatDuration(durationMs)} • ${formatSpeed(avgKmh)}" +
                        if (track.active) " • Recording" else ""
                )
                Text("${track.pointCount} points", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete, enabled = !track.active) {
                Icon(Icons.Default.Delete, "Delete track")
            }
        }
    }
}
