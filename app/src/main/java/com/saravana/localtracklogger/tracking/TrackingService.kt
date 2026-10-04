package com.saravana.localtracklogger.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.saravana.localtracklogger.MainActivity
import com.saravana.localtracklogger.data.TrackRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject lateinit var repository: TrackRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient

    // Main-thread state: one channel + one consumer per recording session, so fixes are
    // written strictly in order by a single coroutine.
    private var tracking = false
    private var channel = Channel<Location>(Channel.UNLIMITED)
    private var session: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { channel.trySend(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Location tracking active")
            .setContentText("Recording locally on this device")
            .setOngoing(true)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
        // Must run on every startForegroundService() call, even if already tracking.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        if (tracking) return
        tracking = true

        val previous = session
        val fixes = Channel<Location>(Channel.UNLIMITED)
        channel = fixes
        session = scope.launch {
            previous?.join() // a quickly restarted session waits for the previous one to be closed
            try {
                repository.start()
                for (location in fixes) repository.record(location)
            } finally {
                // Runs on normal stop, on error and on cancellation, so the track is never left active.
                withContext(NonCancellable) { repository.stop() }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
            .setMinUpdateDistanceMeters(10f)
            .setMinUpdateIntervalMillis(5_000)
            .build()
        try {
            client.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {
            stopTracking()
        }
    }

    private fun stopTracking() {
        if (!tracking) {
            stopSelf()
            return
        }
        tracking = false
        client.removeLocationUpdates(callback)
        channel.close() // consumer drains remaining fixes, then closes the track
        val finishing = session
        scope.launch {
            finishing?.join()
            withContext(Dispatchers.Main) {
                if (!tracking) { // a new session may have started meanwhile
                    ServiceCompat.stopForeground(this@TrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Active location tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        channel.close()
        // If the system destroys us without a Stop, give the session a moment to close the track.
        runBlocking { withTimeoutOrNull(2_000) { session?.join() } }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.saravana.localtracklogger.START"
        const val ACTION_STOP = "com.saravana.localtracklogger.STOP"
        private const val CHANNEL = "tracking"
        private const val NOTIFICATION_ID = 41
    }
}
