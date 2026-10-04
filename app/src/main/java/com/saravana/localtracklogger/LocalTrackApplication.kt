package com.saravana.localtracklogger

import android.app.Application
import com.saravana.localtracklogger.data.TrackRepository
import dagger.hilt.android.HiltAndroidApp
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LocalTrackApplication : Application() {
    @Inject lateinit var repository: TrackRepository

    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
        runCatching { AndroidGraphicFactory.createInstance(this) }
        // Any track still "active" from before this process started was orphaned by a crash or kill.
        val processStart = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch { runCatching { repository.closeStaleTracks(processStart) } }
    }
}
