package com.levi.hermes_trading

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
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
import com.levi.hermes_trading.ui.settings.AppSettingsKeys
import java.util.concurrent.TimeUnit
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    // This local variable will now be loaded from SharedPreferences
    // It primarily controls the Handler-based frequent sync
    private var enableAutoMinuteSync: Boolean = true

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission", "POST_NOTIFICATIONS permission granted.")
                // Re-check the persisted setting before starting the Handler-based sync
                loadSyncSetting() // Load the latest setting
                if (enableAutoMinuteSync) {
                    Log.i("MainActivity", "Permission granted and auto-sync (Handler) enabled, starting service for Handler sync.")
                    // This starts the service which then enables the Handler-based loop if pref is true
                    DataUpdateForegroundService.startHermesService(this)
                }
            } else {
                Log.w("Permission", "POST_NOTIFICATIONS permission denied. Frequent (Handler) sync will not start automatically if dependent on this.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(AppSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        loadSyncSetting() // Load the setting for Handler-based sync

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        navView.setOnItemSelectedListener { item ->
            if (navController.currentDestination?.id != item.itemId) {
                navController.navigate(item.itemId)
            }
            true
        }

        // Schedule your existing WorkManager task (independent of the foreground service types)
        scheduleJsonUpdatesIfNotDoneInApplication()

        // --- CRITICAL CHANGE: Start the AlarmManager-based critical sync schedule ---
        // This ensures the reliable, Doze-friendly sync cycle is initiated.
        Log.i("MainActivity", "Scheduling initial AlarmManager-based critical sync.")
        DataUpdateForegroundService.scheduleInitialOrManualCriticalSync(this.applicationContext)
        // Using applicationContext is often safer for long-lived operations initiated from an Activity

        // Automatic service start/stop logic for the Handler-based frequent sync, based on loaded preference
        updateServiceBasedOnPreference()
    }

    override fun onResume() {
        super.onResume()
        // If the user changes the setting for Handler-based sync in SettingsFragment and comes back,
        // ensure MainActivity reflects the latest preference for the Handler loop.
        val previousHandlerSyncState = enableAutoMinuteSync
        loadSyncSetting()
        if (previousHandlerSyncState != enableAutoMinuteSync) {
            Log.i("MainActivity", "Handler-based auto-sync setting changed. Updating service state for Handler sync.")
            updateServiceBasedOnPreference() // This will start/stop the Handler loop within the service
        }
    }

    private fun loadSyncSetting() {
        // This setting primarily controls the Handler-based minute sync
        enableAutoMinuteSync = sharedPreferences.getBoolean(AppSettingsKeys.KEY_ENABLE_MINUTE_SYNC, true) // Default true
        Log.i("MainActivity", "Handler-based auto-minute-sync setting loaded: $enableAutoMinuteSync")
    }

    // This function updates the state of the Handler-based frequent sync.
    // The AlarmManager-based critical sync runs independently once scheduled.
    private fun updateServiceBasedOnPreference() {
        if (enableAutoMinuteSync) {
            Log.i("MainActivity", "Handler-based auto-sync is enabled. Attempting to start service for Handler loop.")
            // This ensures the service is running, and the service internally manages the Handler loop.
            // It also handles notification permission for the service's foreground notification.
            askNotificationPermissionAndStartService()
        } else {
            Log.i("MainActivity", "Handler-based auto-sync is disabled. Stopping Handler loop within the service.")
            // We don't necessarily stop the entire service here if the critical AlarmManager sync
            // is still meant to run. The service itself will manage its lifecycle.
            // This call will primarily ensure the Handler-based part is stopped.
            // DataUpdateForegroundService.stopHermesService(this) // This would stop everything, including AlarmManager loop if not careful.
            // Instead, send a specific intent to stop just the handler loop if your service supports it,
            // or rely on the preference listener within the service.
            // For now, startHermesService will ensure the service is up, and it will read the preference.
            // If the preference is false, the service's Handler loop won't run.
            // If you want to explicitly tell the service to stop its handler loop:
            val intent = Intent(this, DataUpdateForegroundService::class.java).apply {
                action = DataUpdateForegroundService.ACTION_STOP_HANDLER_SYNC
            }
            ContextCompat.startForegroundService(this, intent)
            Log.d("MainActivity", "Sent ACTION_STOP_HANDLER_SYNC to service.")

            // If the service should truly stop if neither sync type is desired,
            // then DataUpdateForegroundService.stopHermesService(this) might be appropriate,
            // but ensure your critical alarms are also meant to be off.
        }
    }

    // This method now primarily ensures the service can run as foreground for the Handler-based sync
    // and asks for POST_NOTIFICATIONS if needed for the service's own ongoing notification.
    private fun askNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.i("MainActivity", "Notification permission already granted.")
                    if (enableAutoMinuteSync) {
                        Log.i("MainActivity", "Starting service for Handler-based sync (permission granted).")
                        DataUpdateForegroundService.startHermesService(this)
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
            // No runtime POST_NOTIFICATIONS permission needed before Android 13
            if (enableAutoMinuteSync) {
                Log.i("MainActivity", "Starting service for Handler-based sync (SDK < 33).")
                DataUpdateForegroundService.startHermesService(this)
            }
        }
    }

    private fun scheduleJsonUpdatesIfNotDoneInApplication() {
        val jsonUpdateWorkRequest =
            PeriodicWorkRequestBuilder<JsonUpdateWorker>(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "jsonSheetUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP, // K KEEP if you want it to persist, REPLACE if it should update
            jsonUpdateWorkRequest
        )
        Log.i("MainActivity", "Periodic JSON update work (WorkManager, ~15min) (re)scheduled.")
    }
}