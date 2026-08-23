package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.wear.compose.material3.MaterialTheme
import com.example.ui.navigation.WearsicApp
import com.example.ui.theme.WearsicTheme

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var pendingTileAction: String? = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Media notification permission result; playback works regardless. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Media notifications require POST_NOTIFICATIONS on API 33+ (Wear OS 6).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleTileIntent(intent)

        setContent {
            WearsicTheme {
                // Scale all app text up for comfortable reading on the watch.
                // The UI was tuned for 480x480 round displays; 1.25x lifts the
                // smallest 8-12sp labels to a readable size.
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 1.25f)
                ) {
                    WearsicApp()
                }
            }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleTileIntent(intent)
    }

    private fun handleTileIntent(intent: android.content.Intent?) {
        val action = intent?.getStringExtra("tile_action")
        if (!action.isNullOrBlank()) {
            pendingTileAction = action
        }
    }
}
