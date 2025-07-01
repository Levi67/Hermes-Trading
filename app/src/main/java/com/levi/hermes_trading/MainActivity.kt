package com.levi.hermes_trading

import android.Manifest
import android.content.Context // Added for SharedPreferences
import android.content.SharedPreferences // Added
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.levi.hermes_trading.databinding.ActivityMainBinding
import com.levi.hermes_trading.JsonUpdateWorker
import com.levi.hermes_trading.services.DataUpdateForegroundService
// Assuming AppSettingsKeys is accessible, e.g., from SettingsViewModel or a Constants file
import com.levi.hermes_trading.ui.settings.AppSettingsKeys // Adjust import if needed
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences // Added

    // This local variable will now be loaded from SharedPreferences
    private var enableAutoMinuteSync: Boolean = true

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission", "POST_NOTIFICATIONS permission granted.")
                // Re-check the persisted setting before starting
                loadSyncSetting() // Load the latest setting
                if (enableAutoMinuteSync) {
                    Log.i("MainActivity", "Permission granted and auto-sync enabled, starting service.")
                    DataUpdateForegroundService.startService(this)
                }
            } else {
                Log.w("Permission", "POST_NOTIFICATIONS permission denied. Frequent sync will not start automatically.")
                // If permission is denied, it makes sense to also reflect this in the setting
                // to avoid repeated attempts if the user explicitly denied permission.
                // However, the SettingsFragment is the primary controller of this setting.
                // MainActivity should just respect what's in SharedPreferences.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(AppSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        loadSyncSetting() // Load the setting on create

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // ... (rest of NavController setup)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings, R.id.navigation_settings // Add settings ID
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        navView.setOnItemSelectedListener { item ->
            // Ensure settings navigation works
            if (item.itemId == R.id.navigation_settings && navController.currentDestination?.id != R.id.navigation_settings) {
                navController.navigate(R.id.navigation_settings)
            } else if (navController.currentDestination?.id != item.itemId) {
                navController.navigate(item.itemId)
            }
            true
        }


        scheduleJsonUpdatesIfNotDoneInApplication()

        // Automatic service start/stop logic based on loaded preference
        updateServiceBasedOnPreference()
    }

    override fun onResume() {
        super.onResume()
        // If the user changes the setting in SettingsFragment and comes back,
        // ensure MainActivity reflects the latest preference.
        val previousState = enableAutoMinuteSync
        loadSyncSetting()
        if (previousState != enableAutoMinuteSync) { // If setting changed while activity was paused
            Log.i("MainActivity", "Auto-sync setting changed. Updating service state.")
            updateServiceBasedOnPreference()
        }
    }

    private fun loadSyncSetting() {
        enableAutoMinuteSync = sharedPreferences.getBoolean(AppSettingsKeys.KEY_ENABLE_MINUTE_SYNC, true) // Default true
        Log.i("MainActivity", "Auto-sync setting loaded: $enableAutoMinuteSync")
    }

    // This function will be called from SettingsViewModel or when the setting changes.
    // For now, MainActivity reads it on its own lifecycle events.
    // We can also make MainActivity observe the LiveData from SettingsViewModel if they share a ViewModel scope or via an event bus.

    private fun updateServiceBasedOnPreference() {
        if (enableAutoMinuteSync) {
            Log.i("MainActivity", "Auto-sync is enabled, attempting to start service.")
            askNotificationPermissionAndStartService()
        } else {
            Log.i("MainActivity", "Auto-sync is disabled. Foreground service will be stopped.")
            DataUpdateForegroundService.stopService(this)
        }
    }

    private fun askNotificationPermissionAndStartService() {
        // ... (this method remains mostly the same, ensuring it checks enableAutoMinuteSync internally too)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    if (enableAutoMinuteSync) { // Crucial check
                        Log.i("MainActivity", "Permission granted and auto-sync enabled, starting service.")
                        DataUpdateForegroundService.startService(this)
                    }
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.w("Permission", "Showing rationale for POST_NOTIFICATIONS.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.i("Permission", "Requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            if (enableAutoMinuteSync) { // Crucial check
                Log.i("MainActivity", "Auto-sync enabled, starting service (SDK < 33).")
                DataUpdateForegroundService.startService(this)
            }
        }
    }

    private fun scheduleJsonUpdatesIfNotDoneInApplication() {
        // ... (this method remains the same)
        val jsonUpdateWorkRequest =
            PeriodicWorkRequestBuilder<JsonUpdateWorker>(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "jsonSheetUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            jsonUpdateWorkRequest
        )
        Log.i("MainActivity", "Periodic JSON update work (WorkManager, ~15min) (re)scheduled.")
    }
}