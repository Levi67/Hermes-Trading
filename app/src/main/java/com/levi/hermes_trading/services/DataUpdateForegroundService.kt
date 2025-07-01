package com.levi.hermes_trading.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.levi.hermes_trading.R
import com.levi.hermes_trading.data.DataRepository
import com.levi.hermes_trading.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.localbroadcastmanager.content.LocalBroadcastManager

private const val PREF_KEY_AUTO_SYNC_ENABLED = "pref_auto_minute_sync_enabled"
private const val PREF_KEY_ALARMS_ENABLED = "pref_key_alarms_enabled"

class DataUpdateForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var dataRepository: DataRepository

    private var lastUpdateStatus: String = "Initializing..."
    private var lastUpdateTime: String = "N/A"

    private lateinit var sharedPreferences: SharedPreferences
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PREF_KEY_AUTO_SYNC_ENABLED) {
                val autoSyncEnabled = prefs.getBoolean(key, true)
                if (autoSyncEnabled) {
                    Log.d(TAG, "Auto-sync preference changed to ENABLED. Ensuring sync is scheduled.")
                    startPeriodicUpdatesIfEnabled()
                } else {
                    Log.d(TAG, "Auto-sync preference changed to DISABLED. Stopping periodic updates.")
                    stopPeriodicUpdates()
                }
            }
            // You could also add a direct check/reaction for PREF_KEY_ALARMS_ENABLED
            // if you need to do something more immediate than just checking it in fetchDataAndUpdate()
        }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (sharedPreferences.getBoolean(PREF_KEY_AUTO_SYNC_ENABLED, true)) {
                fetchDataAndUpdate()
                uiHandler.postDelayed(this, UPDATE_INTERVAL_MS)
                Log.d(TAG, "UpdateRunnable: Rescheduled next update.")
            } else {
                Log.d(TAG, "UpdateRunnable: Auto-sync disabled. Not rescheduling.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dataRepository = DataRepository(application)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        Log.d(TAG, "Service Created. Initial auto-sync: ${sharedPreferences.getBoolean(PREF_KEY_AUTO_SYNC_ENABLED, true)}, Initial alarms: ${sharedPreferences.getBoolean(PREF_KEY_ALARMS_ENABLED, true)}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service Started.")
        createNotificationChannels() // Create channels first
        updateOngoingNotification()  // Show initial ongoing notification

        startPeriodicUpdatesIfEnabled() // Start updates based on preference

        return START_STICKY
    }

    private fun startPeriodicUpdatesIfEnabled() {
        if (sharedPreferences.getBoolean(PREF_KEY_AUTO_SYNC_ENABLED, true)) {
            uiHandler.removeCallbacks(updateRunnable) // Ensure no duplicates
            uiHandler.post(updateRunnable)
            Log.d(TAG, "Periodic updates started (or ensured running).")
        } else {
            Log.d(TAG, "Periodic updates are disabled. Not starting.")
            stopPeriodicUpdates() // Ensure stopped if preference is false
        }
    }

    private fun stopPeriodicUpdates() {
        uiHandler.removeCallbacks(updateRunnable)
        Log.d(TAG, "Periodic updates stopped.")
        // Optionally update notification:
        // lastUpdateStatus = "Sync paused by user"
        // lastUpdateTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        // updateOngoingNotification()
    }

// Inside DataUpdateForegroundService.kt

    private fun fetchDataAndUpdate() {
        serviceScope.launch {
            Log.d(TAG, "Attempting to fetch data...")
            var fetchResult: com.levi.hermes_trading.data.FetchResult? = null
            try {
                // This method in DataRepository should handle fetching AND saving/updating
                // the local data source that HomeFragment's repository will read.
                fetchResult = dataRepository.fetchAndRefreshAllDataSources()
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                lastUpdateTime = sdf.format(Date())

                if (fetchResult.isSuccess) {
                    lastUpdateStatus = "Last update: Success at $lastUpdateTime"
                    Log.d(TAG, "Data fetch successful.")

                    // --- SEND BROADCAST FOR UI UPDATE ---
                    val intent = Intent(ACTION_DATA_UPDATED)
                    // Optionally, you can put extras in the intent if the fragment needs specific info
                    // that isn't already available via the repository reload.
                    // e.g., intent.putExtra("new_items_count", fetchResult.newItemsCount)
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                    Log.d(TAG, "Sent ACTION_DATA_UPDATED broadcast to trigger UI refresh.")
                    // --- END SEND BROADCAST ---

                    val alarmsPreferenceEnabled = sharedPreferences.getBoolean(PREF_KEY_ALARMS_ENABLED, true)
                    if (alarmsPreferenceEnabled && fetchResult.hasActiveAlarms) {
                        Log.i(TAG, "Alarms are active and preference enabled: ${fetchResult.activeAlarmItems.joinToString()}")
                        val alarmMessage = if (fetchResult.activeAlarmItems.size == 1) {
                            "Alarm: ${fetchResult.activeAlarmItems.first()} is active!"
                        } else {
                            "${fetchResult.activeAlarmItems.size} alarms active. Check app."
                        }
                        showAlarmNotification("Critical Alert", alarmMessage)
                    } else {
                        if (!alarmsPreferenceEnabled && fetchResult.hasActiveAlarms) {
                            Log.d(TAG, "Alarms active but preference is disabled.")
                        }
                        cancelAlarmNotification() // Cancel if no active alarms or preference disabled
                        if (alarmsPreferenceEnabled && !fetchResult.hasActiveAlarms) {
                            Log.d(TAG, "No active alarms (and preference enabled).")
                        }
                    }
                } else {
                    lastUpdateStatus = "Last update: Failed at $lastUpdateTime"
                    Log.w(TAG, "Data fetch reported failure.")
                    // Decide if you want to send a broadcast even on failure,
                    // perhaps with an extra indicating failure, so UI can show an error state.
                    // For now, only sending on success as per the primary request.
                }
            } catch (e: Exception) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                lastUpdateTime = sdf.format(Date())
                lastUpdateStatus = "Last update: Error at $lastUpdateTime"
                Log.e(TAG, "Error fetching data: ${e.message}", e)
                // Similarly, decide on broadcasting for errors.
            }
            updateOngoingNotification() // Update the status in the ongoing notification
        }
    }
    // --- START OF NOTIFICATION METHODS ---
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Hermes Trading Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles frequent background data synchronization."
            }
            val alarmChannel = NotificationChannel(
                ALARM_NOTIFICATION_CHANNEL_ID,
                "Hermes Trading Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows critical alerts from Hermes Trading."
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alarmChannel)
            Log.d(TAG, "Notification channels created.")
        }
    }

    private fun updateOngoingNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Trading Active")
            .setContentText(lastUpdateStatus)
            .setSmallIcon(R.drawable.ic_baseline_sync_24) // Ensure this icon exists
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(SERVICE_ID, notification)
        Log.d(TAG, "Ongoing notification updated: $lastUpdateStatus")
    }

    private fun showAlarmNotification(title: String, message: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 1, notificationIntent, pendingIntentFlags)
        val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmIcon = android.R.drawable.ic_dialog_alert

        val builder = NotificationCompat.Builder(this, ALARM_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(alarmIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(alarmSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALARM_NOTIFICATION_ID, builder.build())
        Log.i(TAG, "Alarm notification displayed: $title - $message")
    }

    private fun cancelAlarmNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALARM_NOTIFICATION_ID)
        Log.d(TAG, "Alarm notification cancelled.")
    }
    // --- END OF NOTIFICATION METHODS ---

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed.")
        stopPeriodicUpdates()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DataUpdateService"
        const val CHANNEL_ID = "DataUpdateForegroundServiceChannel"
        const val SERVICE_ID = 101
        const val ALARM_NOTIFICATION_CHANNEL_ID = "DataUpdateAlarmChannel"
        const val ALARM_NOTIFICATION_ID = 102
        const val UPDATE_INTERVAL_MS = 60 * 1000L
        // const val UPDATE_INTERVAL_MS = 10 * 1000L // FOR TESTING
        const val ACTION_START_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"

        const val ACTION_DATA_UPDATED = "com.levi.hermes_trading.DATA_UPDATED" // Unique action

        fun startService(context: Context) {
            val startIntent = Intent(context, DataUpdateForegroundService::class.java)
            startIntent.action = ACTION_START_SERVICE
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, DataUpdateForegroundService::class.java)
            stopIntent.action = ACTION_STOP_SERVICE
            context.stopService(stopIntent)
        }
    }
}