package com.levi.hermes_trading.receivers // Ensure this package name is correct!

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.levi.hermes_trading.services.DataUpdateForegroundService // Ensure this path is correct

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "Alarm received! Action: ${intent?.action}")
        context?.let {
            // We expect this receiver to be triggered for critical sync
            val serviceIntent = Intent(it, DataUpdateForegroundService::class.java).apply {
                // This action needs to match the one defined in DataUpdateForegroundService
                // Use the public constant from DataUpdateForegroundService
                action = DataUpdateForegroundService.ACTION_CRITICAL_SYNC_AND_ALARM_CHECK
            }
            Log.d(TAG, "Starting DataUpdateForegroundService for critical sync.")
            ContextCompat.startForegroundService(it, serviceIntent)
        } ?: Log.e(TAG, "Context was null in onReceive, cannot start service.")
    }
}