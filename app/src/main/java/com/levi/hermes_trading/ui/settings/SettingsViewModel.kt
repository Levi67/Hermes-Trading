package com.levi.hermes_trading.ui.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.levi.hermes_trading.services.DataUpdateForegroundService // Import your service

// Assuming AppSettingsKeys is defined elsewhere or here
object AppSettingsKeys { // You can move this to a separate file if preferred
    const val PREFS_NAME = "AppSettings"
    const val KEY_ENABLE_MINUTE_SYNC = "EnableFrequentSync"
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences(AppSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoSyncEnabled = MutableLiveData<Boolean>()
    val autoSyncEnabled: LiveData<Boolean> = _autoSyncEnabled

    init {
        loadAutoSyncSetting()
    }

    private fun loadAutoSyncSetting() {
        _autoSyncEnabled.value = sharedPreferences.getBoolean(AppSettingsKeys.KEY_ENABLE_MINUTE_SYNC, true) // Default to true
        Log.d("SettingsViewModel", "Loaded auto-sync setting: ${autoSyncEnabled.value}")
    }

    fun saveAutoSyncSetting(isEnabled: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean(AppSettingsKeys.KEY_ENABLE_MINUTE_SYNC, isEnabled)
            apply() // Use apply() for asynchronous save
        }
        _autoSyncEnabled.value = isEnabled // Update LiveData immediately
        Log.d("SettingsViewModel", "Saved auto-sync setting: $isEnabled")

        // Trigger Foreground Service start/stop based on the new setting
        if (isEnabled) {
            Log.d("SettingsViewModel", "Auto-sync enabled, ensuring service is started via MainActivity's logic or directly.")
            // Ideally, MainActivity observes this change or a shared LiveData,
            // or you send an event. For now, we'll let MainActivity handle it on next creation,
            // or if MainActivity is the one observing this ViewModel.
            // OR, if you want direct control and MainActivity is not easily reachable:
            DataUpdateForegroundService.startHermesService(getApplication())
        } else {
            Log.d("SettingsViewModel", "Auto-sync disabled, ensuring service is stopped.")
            DataUpdateForegroundService.stopHermesService(getApplication())
        }
    }
}