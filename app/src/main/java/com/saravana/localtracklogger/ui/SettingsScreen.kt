package com.saravana.localtracklogger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saravana.localtracklogger.BuildConfig
import com.saravana.localtracklogger.data.TrackRepository
import com.saravana.localtracklogger.export.BackupManager
import com.saravana.localtracklogger.export.ImportSummary
import com.saravana.localtracklogger.maps.MapInfo
import com.saravana.localtracklogger.maps.OfflineMapStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val mapStore: OfflineMapStore,
    private val backup: BackupManager
) : ViewModel() {
    val trackCount = repository.observeTracks().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val finishedCount = repository.observeTracks().map { list -> list.count { !it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val map: StateFlow<MapInfo?> = mapStore.map

    private val _importingMap = MutableStateFlow(false)
    val importingMap = _importingMap.asStateFlow()
    private val _mapError = MutableStateFlow<String?>(null)
    val mapError = _mapError.asStateFlow()

    fun importMap(uri: Uri) {
        viewModelScope.launch {
            _importingMap.value = true
            _mapError.value = null
            mapStore.import(uri).onFailure { _mapError.value = it.message ?: "Import failed" }
            _importingMap.value = false
        }
    }

    fun removeMap() {
        viewModelScope.launch { mapStore.remove() }
    }

    fun deleteAllFinished() {
        viewModelScope.launch { repository.deleteAllFinished() }
    }

    suspend fun exportAll(uri: Uri, context: Context): Int = backup.exportAll(uri, context.contentResolver)
    suspend fun importFiles(uris: List<Uri>, context: Context): ImportSummary = backup.import(uris, context.contentResolver)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val trackCount by vm.trackCount.collectAsStateWithLifecycle()
    val finishedCount by vm.finishedCount.collectAsStateWithLifecycle()
    val map by vm.map.collectAsStateWithLifecycle()
    val importingMap by vm.importingMap.collectAsStateWithLifecycle()
    val mapError by vm.mapError.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val message = try {
                val n = vm.exportAll(uri, context)
                "Exported $n track(s)"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            busy = false
            snackbar.showSnackbar(message)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            val s = vm.importFiles(uris, context)
            busy = false
            snackbar.showSnackbar(
                "Imported ${s.imported} track(s)" +
                    (if (s.duplicates > 0) ", ${s.duplicates} already present" else "") +
                    (if (s.failedFiles > 0) ", ${s.failedFiles} file(s) could not be read" else "")
            )
        }
    }
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importMap(uri)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section("Data") {
                Text("$trackCount track(s) stored on this device.")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Export saves every track as a GPX file inside one ZIP. Import reads GPX files or such a ZIP, " +
                        "and skips tracks that already exist.",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { exportLauncher.launch("LocalTrackLogger-${today()}.zip") }, enabled = !busy && trackCount > 0) {
                        Text("Export all tracks")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !busy) {
                        Text("Import GPX / ZIP")
                    }
                }
                OutlinedButton(
                    onClick = { confirmDeleteAll = true },
                    enabled = !busy && finishedCount > 0,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete all finished tracks") }
            }

            Section("Offline map") {
                if (map != null) {
                    Text("${map!!.name} (${formatSize(map!!.sizeBytes)})")
                } else {
                    Text(
                        "No map imported yet. Download a Mapsforge (.map) file for your region in your browser, " +
                            "then import it here. This app itself never connects to the Internet."
                    )
                }
                if (importingMap) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Importing map, large files take a moment…", style = MaterialTheme.typography.bodySmall)
                }
                mapError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { mapPicker.launch(arrayOf("*/*")) }, enabled = !importingMap) {
                        Text(if (map == null) "Import map" else "Replace map")
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://download.mapsforge.org/")))
                    }) { Text("Get maps") }
                    if (map != null) TextButton(onClick = vm::removeMap, enabled = !importingMap) { Text("Remove") }
                }
            }

            Section("About") {
                Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                Text(
                    "All data stays on this device. The app declares no Internet permission. " +
                        "Updates installed over the existing app keep your tracks.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Place search uses a bundled list of place names from GeoNames (geonames.org), licensed CC BY 4.0.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all finished tracks?") },
            text = { Text("Every finished track will be permanently removed from this device. Consider exporting first.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllFinished(); confirmDeleteAll = false }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
