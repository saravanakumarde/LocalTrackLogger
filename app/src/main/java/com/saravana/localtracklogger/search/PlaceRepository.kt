package com.saravana.localtracklogger.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled place list (assets/places.tsv.gz) once, in its own scope. Loading must not
 * belong to a search coroutine: every keystroke cancels the previous search, which used to abort
 * the load again and again so that no result ever appeared.
 */
@Singleton
class PlaceRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loading: Deferred<PlaceIndex>? = null

    /** Starts loading in the background; safe to call repeatedly. */
    fun warmUp() {
        ensureLoading()
    }

    suspend fun search(query: String, nearLat: Double?, nearLon: Double?, limit: Int = 5): List<PlaceHit> {
        val index = ensureLoading().await()
        return withContext(Dispatchers.Default) { index.search(query, nearLat, nearLon, limit) }
    }

    @Synchronized
    private fun ensureLoading(): Deferred<PlaceIndex> {
        val current = loading
        if (current != null && !current.isCancelled) return current // a failed load is retried
        return scope.async {
            context.assets.open("places.tsv.gz").use { raw ->
                GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { PlaceIndex(it) }
            }
        }.also { loading = it }
    }
}
