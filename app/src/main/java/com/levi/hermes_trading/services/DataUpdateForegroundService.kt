package com.levi.hermes_trading.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.levi.hermes_trading.R // For your R.drawable.icon
import com.levi.hermes_trading.data.DataRepository
import com.levi.hermes_trading.* // Or your main entry activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataUpdateForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var dataRepository: DataRepository // Needs initialization

    private var lastUpdateStatus: String = "Initializing..."
    private var lastUpdateTime: String = "N/A"

    private val updateRunnable = object : Runnable {
        override fun run() {
            fetchDataAndUpdate()
            uiHandler.postDelayed(this, UPDATE_INTERVAL_MS) // Reschedule
        }
    }

    private fun fetchDataAndUpdate() {
        serviceScope.launch {
            Log.d(TAG, "Attempting to fetch data in background...")
            try {
                // This method in DataRepository should:
                // 1. Fetch from network
                // 2. Save to local storage (file)
                // 3. Update its internal LiveData that ViewModels observe
                val success = dataRepository.fetchAndRefreshAllDataSources() // Assuming this returns a boolean or some status

                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                lastUpdateTime = sdf.format(Date())

                if (success) {
                    lastUpdateStatus = "Last update: Success at $lastUpdateTime"
                    Log.d(TAG, "Data fetch successful.")
                } else {
                    lastUpdateStatus = "Last update: Failed at $lastUpdateTime"
                    Log.w(TAG, "Data fetch reported failure.")
                }
            } catch (e: Exception) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                lastUpdateTime = sdf.format(Date())
                lastUpdateStatus = "Last update: Error at $lastUpdateTime"
                Log.e(TAG, "Error fetching data: ${e.message}", e)
            }
            // Update the notification with the new status
            updateNotification()
        }
    }


    override fun onCreate() {
        super.onCreate()
        // Initialize DataRepository here.
        // If it needs Application context and is a singleton:
        // dataRepository = DataRepository.getInstance(application)
        // Or if you pass it:
        dataRepository = DataRepository(application)
        Log.d(TAG, "Service Created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service Started.")
        createNotificationChannel()

        // Initial notification before first fetch
        updateNotification() // Build and show initial notification

        uiHandler.removeCallbacks(updateRunnable) // Remove existing before posting new
        uiHandler.post(updateRunnable)        // Start the periodic updates

        return START_STICKY // Keep service running if killed by system (use with caution)
    }

    private fun updateNotification() {
        // Intent to launch your app when notification is clicked
        val notificationIntent = Intent(this, MainActivity::class.java) // Replace MainActivity
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Trading Active") // Your App Name
            .setContentText(lastUpdateStatus) // Dynamic content!
            .setSmallIcon(R.drawable.ic_baseline_sync_24) // REPLACE with your actual icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissible by swipe
            .setOnlyAlertOnce(true) // Don't make sound/vibrate for subsequent updates
            .setPriority(NotificationCompat.PRIORITY_LOW) // Use LOW or MIN to be less intrusive
            .build()

        startForeground(SERVICE_ID, notification)
        Log.d(TAG, "Notification updated: $lastUpdateStatus")
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed.")
        uiHandler.removeCallbacks(updateRunnable) // Stop updates
        serviceJob.cancel() // Cancel all coroutines started by this scope
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Hermes Trading Sync Service", // User visible name
                NotificationManager.IMPORTANCE_LOW // Use LOW or MIN
            ).apply {
                description = "Handles frequent background data synchronization."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val TAG = "DataUpdateService"
        const val CHANNEL_ID = "DataUpdateForegroundServiceChannel"
        const val SERVICE_ID = 101 // Must be unique within your app
        const val UPDATE_INTERVAL_MS = 60 * 1000L // 1 minute
        // const val UPDATE_INTERVAL_MS = 10 * 1000L // 10 seconds FOR TESTING ONLY

        // Optional: Actions for starting/stopping from other components
        const val ACTION_START_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"

        fun startService(context: Context) {
            val startIntent = Intent(context, DataUpdateForegroundService::class.java)
            startIntent.action = ACTION_START_SERVICE
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, DataUpdateForegroundService::class.java)
            stopIntent.action = ACTION_STOP_SERVICE
            context.stopService(stopIntent) // Use context.stopService
        }
    }
}