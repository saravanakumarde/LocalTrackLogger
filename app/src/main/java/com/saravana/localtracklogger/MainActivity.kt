package com.saravana.localtracklogger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.saravana.localtracklogger.ui.CrashScreen
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saravana.localtracklogger.tracking.TrackingService
import com.saravana.localtracklogger.ui.SettingsScreen
import com.saravana.localtracklogger.ui.TrackDetailScreen
import com.saravana.localtracklogger.ui.TrackListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val located = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (located) startTrackingService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashReport = CrashReporter.read(this)
        setContent {
            MaterialTheme {
                var showCrash by remember { mutableStateOf(crashReport != null) }
                if (showCrash && crashReport != null) {
                    CrashScreen(crashReport, onContinue = {
                        CrashReporter.clear(this@MainActivity)
                        showCrash = false
                    })
                    return@MaterialTheme
                }
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "tracks") {
                    composable("tracks") {
                        TrackListScreen(
                            onStart = ::requestStart,
                            onStop = ::stopTrackingService,
                            onOpen = { id -> nav.navigate("track/$id") },
                            onOpenSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable(
                        route = "track/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.LongType })
                    ) {
                        TrackDetailScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    private fun requestStart() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startTrackingService() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startTrackingService() {
        ContextCompat.startForegroundService(
            this, Intent(this, TrackingService::class.java).setAction(TrackingService.ACTION_START)
        )
    }

    private fun stopTrackingService() {
        startService(Intent(this, TrackingService::class.java).setAction(TrackingService.ACTION_STOP))
    }
}
