package com.saravana.localtracklogger.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saravana.localtracklogger.search.PlaceHit
import com.saravana.localtracklogger.search.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.saravana.localtracklogger.data.TrackPointEntity
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.tan

@HiltViewModel
class PlaceSearchViewModel @Inject constructor(private val places: PlaceRepository) : ViewModel() {
    fun warmUp() = places.warmUp()

    /** Returns null when the bundled place list cannot be loaded; search must never crash the app. */
    suspend fun search(query: String, nearLat: Double, nearLon: Double): List<PlaceHit>? =
        try {
            places.search(query, nearLat, nearLon)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
}

/**
 * Interactive map: drag to pan, pinch or double-tap to zoom, plus +/- and "fit" buttons and an
 * offline place search. Draws every list in [tracks] as a line. Without [mapFile] the tracks are
 * drawn on a blank background. Wrap the call in `key(mapFile)` so the view is rebuilt when the map changes.
 */
@Composable
fun TrackMap(
    tracks: List<List<TrackPointEntity>>,
    mapFile: File?,
    modifier: Modifier = Modifier,
    searchEnabled: Boolean = true,
    searchVm: PlaceSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val holder = remember(mapFile) { MapHolder(context, mapFile) }
    DisposableEffect(holder) { onDispose { holder.destroy() } }
    LaunchedEffect(holder, tracks.size, tracks.sumOf { it.size }) { holder.showTracks(tracks) }

    // clipToBounds: the map view draws a little beyond its edges and would cover neighbouring widgets.
    Box(modifier.clipToBounds()) {
        AndroidView(factory = { holder.mapView }, modifier = Modifier.fillMaxSize())
        holder.status?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ) { Text(message, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
        }
        if (searchEnabled) {
            SearchOverlay(
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).fillMaxWidth(),
                search = { q ->
                    val c = holder.center()
                    searchVm.search(q, c.latitude, c.longitude)
                },
                onPick = holder::goTo,
                onClear = holder::clearSearchMarker,
                onFocus = searchVm::warmUp
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(onClick = holder::zoomIn) { Icon(Icons.Default.Add, "Zoom in") }
            SmallFloatingActionButton(onClick = holder::zoomOut) { Icon(Icons.Default.Remove, "Zoom out") }
            SmallFloatingActionButton(onClick = holder::refit) { Icon(Icons.Default.CenterFocusStrong, "Show whole track") }
        }
    }
}

@Composable
private fun SearchOverlay(
    modifier: Modifier,
    search: suspend (String) -> List<PlaceHit>?,
    onPick: (PlaceHit) -> Unit,
    onClear: () -> Unit,
    onFocus: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var noMatch by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    val focus: FocusManager = LocalFocusManager.current

    fun choose(hit: PlaceHit) {
        picked = hit.name
        query = hit.name
        results = emptyList()
        noMatch = false
        focus.clearFocus()
        onPick(hit)
    }

    LaunchedEffect(query) {
        noMatch = false
        searching = false
        unavailable = false
        if (query.trim().length < 2 || query == picked) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(200) // debounce while typing
        searching = true
        val found = search(query)
        searching = false
        unavailable = found == null
        results = found ?: emptyList()
        noMatch = found != null && found.isEmpty()
    }

    Column(modifier) {
        Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search place, e.g. Augsburg") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            picked = null
                            results = emptyList()
                            onClear()
                        }) { Icon(Icons.Default.Close, "Clear search") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    scope.launch {
                        val found = results.ifEmpty { search(query).orEmpty() }
                        found.firstOrNull()?.let(::choose)
                    }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() }
            )
        }
        if (results.isNotEmpty() || noMatch || unavailable || searching) {
            Surface(
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    if (searching && results.isEmpty()) Text("Searching… the first search loads the place list", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    if (noMatch) Text("No place found", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    if (unavailable) Text("Place search data could not be loaded", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    results.forEachIndexed { i, hit ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().clickable { choose(hit) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            val distance = hit.distanceKm?.let { String.format(java.util.Locale.getDefault(), " · %.0f km away", it) }.orEmpty()
                            Text(
                                "${hit.name} · ${hit.country}$distance",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PALETTE = listOf(
    Triple(0, 95, 115), Triple(202, 103, 2), Triple(148, 52, 110), Triple(10, 147, 150), Triple(174, 32, 18)
)

private class MapHolder(private val context: Context, mapFile: File?) {
    val mapView = MapView(context)
    var status by mutableStateOf<String?>(null)
        private set

    private val factory = AndroidGraphicFactory.INSTANCE
    private val density = context.resources.displayMetrics.density
    private var tileCache: InMemoryTileCache? = null
    private var dataStore: MapFile? = null
    private var loadError: String? = null
    private var trackLayers: List<Layer> = emptyList()
    private var lastTracks: List<List<TrackPointEntity>> = emptyList()
    private var fitted = false
    private var centered = false
    private var searchLayer: Layer? = null

    init {
        mapView.isClickable = true
        mapView.mapScaleBar.isVisible = true
        mapView.setBuiltInZoomControls(false)
        if (mapFile != null) {
            try {
                val cache = InMemoryTileCache(48)
                val store = MapFile(mapFile)
                val layer = TileRendererLayer(cache, store, mapView.model.mapViewPosition, factory)
                layer.setXmlRenderTheme(InternalRenderTheme.DEFAULT)
                mapView.layerManager.layers.add(layer)
                tileCache = cache
                dataStore = store
            } catch (e: Exception) {
                loadError = e.message ?: e.javaClass.simpleName
            }
        }
        status = loadError?.let { "Map could not be opened: $it" }
    }

    fun center(): LatLong = mapView.model.mapViewPosition.center

    /** Moves the map to a searched place and drops a blue marker there. */
    fun goTo(hit: PlaceHit) {
        clearSearchMarker()
        val target = LatLong(hit.latitude, hit.longitude)
        val marker = Marker(target, dot(33, 110, 214), 0, 0)
        mapView.layerManager.layers.add(marker)
        searchLayer = marker
        mapView.setCenter(target)
        mapView.setZoomLevel(12)
        fitted = true // keep the searched view when track data refreshes
        mapView.layerManager.redrawLayers()
    }

    fun clearSearchMarker() {
        searchLayer?.let { mapView.layerManager.layers.remove(it) }
        searchLayer = null
        mapView.layerManager.redrawLayers()
    }

    fun zoomIn() = mapView.model.mapViewPosition.zoomIn()
    fun zoomOut() = mapView.model.mapViewPosition.zoomOut()

    fun refit() {
        fitted = false
        showTracks(lastTracks)
    }

    fun showTracks(tracks: List<List<TrackPointEntity>>) {
        lastTracks = tracks
        val layers = mapView.layerManager.layers
        trackLayers.forEach { layers.remove(it) }

        val everything = mutableListOf<LatLong>()
        val created = mutableListOf<Layer>()
        tracks.forEachIndexed { index, points ->
            if (points.isEmpty()) return@forEachIndexed
            val latLongs = points.map { LatLong(it.latitude, it.longitude) }
            everything += latLongs
            val (r, g, b) = PALETTE[index % PALETTE.size]
            val line = Polyline(paint(r, g, b, 5f), factory)
            line.latLongs.addAll(latLongs)
            created += line
            if (tracks.size == 1) {
                created += Marker(latLongs.first(), dot(46, 160, 67), 0, 0)
                created += Marker(latLongs.last(), dot(215, 58, 73), 0, 0)
            }
        }
        trackLayers = created
        created.forEach { layers.add(it) }

        if (everything.isNotEmpty() && !fitted) {
            fit(everything)
            fitted = true
        } else if (everything.isEmpty() && !fitted && !centered) {
            dataStore?.let {
                mapView.setCenter(it.boundingBox().centerPoint)
                mapView.setZoomLevel(7)
                centered = true
            }
        }
        updateStatus(everything)
        mapView.layerManager.redrawLayers()
    }

    private fun updateStatus(points: List<LatLong>) {
        val store = dataStore
        status = when {
            loadError != null -> "Map could not be opened: $loadError"
            store != null && points.isNotEmpty() && points.none { store.boundingBox().contains(it) } ->
                "Your track is outside the area covered by the imported map."
            else -> null
        }
    }

    private fun paint(r: Int, g: Int, b: Int, widthDp: Float): Paint {
        val p = factory.createPaint()
        p.setColor(factory.createColor(255, r, g, b))
        p.setStrokeWidth(widthDp * density)
        p.setStyle(Style.STROKE)
        return p
    }

    private fun dot(r: Int, g: Int, b: Int): org.mapsforge.core.graphics.Bitmap {
        val size = (18 * density).toInt()
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.rgb(r, g, b))
            setStroke((2 * density).toInt(), android.graphics.Color.WHITE)
            setSize(size, size)
        }
        return AndroidGraphicFactory.convertToBitmap(drawable)
    }

    /** Centres the map on the points and picks the zoom level at which all of them fit. */
    private fun fit(points: List<LatLong>) {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        mapView.setCenter(LatLong((minLat + maxLat) / 2, (minLon + maxLon) / 2))

        val metrics = context.resources.displayMetrics
        val viewWidth = metrics.widthPixels.toDouble()
        val viewHeight = metrics.heightPixels * 0.3
        val tile = mapView.model.displayModel.tileSize.toDouble()
        val xSpan = (maxLon - minLon) / 360.0
        val ySpan = abs(mercatorY(maxLat) - mercatorY(minLat))
        val zoomX = if (xSpan > 0) log2(viewWidth / (1.3 * tile * xSpan)) else MAX_ZOOM
        val zoomY = if (ySpan > 0) log2(viewHeight / (1.3 * tile * ySpan)) else MAX_ZOOM
        val zoom = minOf(zoomX, zoomY, MAX_ZOOM).coerceAtLeast(2.0)
        mapView.setZoomLevel(zoom.toInt().toByte())
    }

    private fun mercatorY(lat: Double): Double {
        val rad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2
    }

    fun destroy() {
        runCatching { mapView.destroyAll() }
        runCatching { tileCache?.destroy() }
        runCatching { dataStore?.close() }
        AndroidGraphicFactory.clearResourceMemoryCache()
    }

    private companion object {
        const val MAX_ZOOM = 16.0
    }
}
