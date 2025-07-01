package com.levi.hermes_trading.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.levi.hermes_trading.R
import com.levi.hermes_trading.data.DataRepository
import com.levi.hermes_trading.MainActivity
// Assuming your AlarmReceiver is in a 'receivers' subpackage
import com.levi.hermes_trading.receivers.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.net.toUri

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
                    Log.d(TAG, "User-enabled periodic sync. Ensuring Handler loop is scheduled.")
                    startHandlerBasedPeriodicUpdates()
                } else {
                    Log.d(TAG, "User-disabled periodic sync. Stopping Handler loop.")
                    stopHandlerBasedPeriodicUpdates()
                }
            }
        }

    private val updateRunnable = object : Runnable {
        override fun run() {
            // This runnable is for the user-toggleable, less critical, frequent UI sync
            if (sharedPreferences.getBoolean(PREF_KEY_AUTO_SYNC_ENABLED, true)) {
                Log.d(TAG, "Handler-based updateRunnable executing.")
                serviceScope.launch {
                    // Pass a placeholder startId or handle service stop differently for handler-based updates
                    // For simplicity, we won't try to stopSelf from handler updates here,
                    // as it's meant to be an ongoing loop if enabled.
                    fetchDataAndUpdate(isCriticalCheck = false, serviceStartId = -1)
                }
                uiHandler.postDelayed(this, UPDATE_INTERVAL_HANDLER_MS)
                Log.d(TAG, "Handler-based updateRunnable: Rescheduled next UI sync.")
            } else {
                Log.d(TAG, "Handler-based updateRunnable: Auto-sync disabled. Not rescheduling.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dataRepository = DataRepository(application)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        createNotificationChannels() // Create channels early
        Log.d(TAG, "Service Created. Initial auto-sync (Handler): ${sharedPreferences.getBoolean(PREF_KEY_AUTO_SYNC_ENABLED, true)}")
        // Schedule the first critical sync if it's not already scheduled (e.g., after app update or first run)
        // More robust scheduling might involve checking if an alarm is already pending.
        // For now, we can schedule it here or rely on an external trigger like app boot.
        // Let's schedule it if the service is created and not already via critical sync.
        // A common pattern is to schedule alarms when the app starts or settings change.
        // For simplicity, we'll ensure one is scheduled if the service is created.
        // An alternative is to call scheduleNextCriticalSync() from where you first start the service.
        scheduleNextCriticalSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Service onStartCommand. Action: $action, StartId: $startId")

        updateOngoingNotification()  // Show/Update ongoing notification for foreground state

        when (action) {
            ACTION_CRITICAL_SYNC_AND_ALARM_CHECK -> {
                Log.i(TAG, "Handling CRITICAL_SYNC_AND_ALARM_CHECK from AlarmManager.")
                serviceScope.launch {
                    fetchDataAndUpdate(isCriticalCheck = true, serviceStartId = startId)
                }
            }
            ACTION_START_HANDLER_SYNC -> {
                Log.d(TAG, "Handling ACTION_START_HANDLER_SYNC. Ensuring Handler loop is scheduled if enabled.")
                startHandlerBasedPeriodicUpdates()
            }
            ACTION_STOP_HANDLER_SYNC -> {
                Log.d(TAG, "Handling ACTION_STOP_HANDLER_SYNC. Stopping Handler loop.")
                stopHandlerBasedPeriodicUpdates()
            }
            // ACTION_START_SERVICE from companion object - initial start
            ACTION_START_SERVICE -> {
                Log.d(TAG, "Service started via ACTION_START_SERVICE. Will start handler updates if pref enabled.")
                startHandlerBasedPeriodicUpdates() // Start Handler-based updates based on preference
                // The critical sync is scheduled in onCreate or can be re-ensured here too.
                // scheduleNextCriticalSync() // Could be called here too to ensure it's set
            }
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Service stopping via ACTION_STOP_SERVICE.")
                stopSelf() // This will eventually call onDestroy
                return START_NOT_STICKY // Don't restart if explicitly stopped
            }
            else -> {
                Log.d(TAG, "Service started with no specific action or default action. StartId: $startId")
                // Fallback: Ensure Handler-based updates are running if preference is set.
                startHandlerBasedPeriodicUpdates()
                // And ensure critical sync is scheduled.
                // scheduleNextCriticalSync()
            }
        }
        return START_STICKY
    }

    private fun startHandlerBasedPeriodicUpdates() {
        if (sharedPreferences.getBoolean(PREF_KEY_AUTO_SYNC_ENABLED, true)) {
            uiHandler.removeCallbacks(updateRunnable) // Ensure no duplicates
            uiHandler.post(updateRunnable) // Start immediately
            Log.d(TAG, "Handler-based periodic UI updates started (or ensured running).")
        } else {
            Log.d(TAG, "Handler-based periodic UI updates are disabled by preference. Not starting.")
            stopHandlerBasedPeriodicUpdates()
        }
    }

    private fun stopHandlerBasedPeriodicUpdates() {
        uiHandler.removeCallbacks(updateRunnable)
        Log.d(TAG, "Handler-based periodic UI updates stopped.")
    }

    private suspend fun fetchDataAndUpdate(isCriticalCheck: Boolean, serviceStartId: Int) {
        Log.i(TAG, "Attempting to fetch data... (Critical Check: $isCriticalCheck)")
        var fetchResult: com.levi.hermes_trading.data.FetchResult? = null // Use your actual FetchResult class
        try {
            fetchResult = dataRepository.fetchAndRefreshAllDataSources() // Use your actual method
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            lastUpdateTime = sdf.format(Date())

            if (fetchResult.isSuccess) {
                lastUpdateStatus = "Sync: Success @ $lastUpdateTime${if(isCriticalCheck) " (C)" else ""}"
                Log.d(TAG, "Data fetch successful. IsCritical: $isCriticalCheck")

                val intent = Intent(ACTION_DATA_UPDATED)
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                Log.d(TAG, "Sent ACTION_DATA_UPDATED broadcast.")

                val alarmsPreferenceEnabled = sharedPreferences.getBoolean(PREF_KEY_ALARMS_ENABLED, true)
                if (alarmsPreferenceEnabled && fetchResult.hasActiveAlarms) {
                    Log.i(TAG, "Alarms active & preference enabled: ${fetchResult.activeAlarmItems.joinToString()}")
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
                lastUpdateStatus = "Sync: Failed @ $lastUpdateTime${if(isCriticalCheck) " (C)" else ""}"
                Log.w(TAG, "Data fetch reported failure. IsCritical: $isCriticalCheck")
            }
        } catch (e: Exception) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            lastUpdateTime = sdf.format(Date())
            lastUpdateStatus = "Sync: Error @ $lastUpdateTime${if(isCriticalCheck) " (C)" else ""}"
            Log.e(TAG, "Error fetching data (IsCritical: $isCriticalCheck): ${e.message}", e)
        }
        updateOngoingNotification() // Update the content of the ongoing notification

        if (isCriticalCheck) {
            scheduleNextCriticalSync()
            Log.d(TAG, "Critical check complete. Service StartId: $serviceStartId. Attempting to stop if appropriate.")
            // The service will stop if this startId is the last one and no handler messages are pending.
            // However, START_STICKY might restart it. Consider how to manage lifecycle carefully.
            // If the handler loop is also running, stopSelf might be ignored.
            // A more robust stop might involve checking if the handler loop is also meant to be active.
            // For now, this will allow it to stop if this critical check was the sole active task for this startId.
            stopSelfResult(serviceStartId)
        }
        // If not a critical check, it's from the Handler loop, which reschedules itself.
    }


    private fun scheduleNextCriticalSync() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            // It's good practice to ensure the action is set for the receiver too,
            // though the receiver might not strictly need to check it if it only serves one purpose.
            action = ACTION_CRITICAL_SYNC_AND_ALARM_CHECK // Set action for clarity
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            PENDING_INTENT_REQUEST_CODE_CRITICAL_SYNC, // Use a unique request code
            intent,
            pendingIntentFlags
        )

        // Calculate next trigger time (e.g., 30 minutes from now)
        val triggerAtMillis = SystemClock.elapsedRealtime() + CRITICAL_SYNC_INTERVAL_MS

        // Check for permission to schedule exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                Log.i(TAG, "Next CRITICAL sync scheduled (exact) using ELAPSED_REALTIME_WAKEUP for: ${Date(System.currentTimeMillis() + CRITICAL_SYNC_INTERVAL_MS)}")
            } else {
                Log.w(TAG, "Cannot schedule exact alarms. Scheduling inexact alarm for critical sync.")
                // Fallback to inexact alarm if permission is not granted.
                // This will be less precise and subject to system batching.
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                // Consider guiding the user to grant SCHEDULE_EXACT_ALARM if this is crucial.
            }
        } else {
            // For older versions, setExactAndAllowWhileIdle is available from API 23 (M)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                // For pre-M, setExact is available
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.i(TAG, "Next CRITICAL sync scheduled (pre-S method) using ELAPSED_REALTIME_WAKEUP for: ${Date(System.currentTimeMillis() + CRITICAL_SYNC_INTERVAL_MS)}")
        }
    }

    private fun cancelCriticalSyncAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = ACTION_CRITICAL_SYNC_AND_ALARM_CHECK
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE // Important: FLAG_NO_CREATE to only get existing
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            PENDING_INTENT_REQUEST_CODE_CRITICAL_SYNC,
            intent,
            pendingIntentFlags
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // Also cancel the PendingIntent itself
            Log.i(TAG, "Cancelled CRITICAL sync alarm.")
        } else {
            Log.d(TAG, "No CRITICAL sync alarm was pending to cancel.")
        }
    }


    // --- START OF NOTIFICATION METHODS ---
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                ONGOING_CHANNEL_ID, // Use the constant
                "Hermes Trading Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles background data synchronization."
                setSound(null, null)
            }

            val customAlarmSoundUri: Uri =
                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${packageName}/${R.raw.alarm_sound}".toUri()

            val alarmAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID, // Use the constant
                "Hermes Trading Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows critical alerts from Hermes Trading."
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(customAlarmSoundUri, alarmAudioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC // Ensure public visibility
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alarmChannel)
            Log.d(TAG, "Notification channels created/updated.")
        }
    }

    private fun updateOngoingNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, PENDING_INTENT_REQUEST_CODE_ONGOING_NOTIFICATION, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setContentTitle("Hermes Trading Active")
            .setContentText(lastUpdateStatus)
            .setSmallIcon(R.drawable.ic_baseline_sync_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            startForeground(FOREGROUND_SERVICE_ID, notification)
            Log.d(TAG, "Ongoing notification updated: $lastUpdateStatus")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            // Handle cases where startForeground might fail (e.g., if called from background without permission on Android 12+)
            // This is less likely if started from AlarmManager or a user action.
        }
    }

    private fun showAlarmNotification(title: String, message: String) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, PENDING_INTENT_REQUEST_CODE_ALARM_NOTIFICATION, notificationIntent, pendingIntentFlags)

        val alarmIcon = android.R.drawable.ic_dialog_alert

        val customAlarmSoundUri: Uri =
            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${packageName}/${R.raw.alarm_sound}".toUri()

        val builder = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(alarmIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // CRITICAL FOR LOCK SCREEN

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(customAlarmSoundUri)
            builder.setVibrate(longArrayOf(0, 500, 200, 500))
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALARM_NOTIFICATION_ID_NUMBER, builder.build()) // Use the Int ID
        Log.i(TAG, "Alarm notification displayed: $title - $message.")
    }

    private fun cancelAlarmNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALARM_NOTIFICATION_ID_NUMBER)
        Log.d(TAG, "Alarm notification cancelled.")
    }
    // --- END OF NOTIFICATION METHODS ---

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service Destroyed.")
        stopHandlerBasedPeriodicUpdates() // Stop handler loop
        cancelCriticalSyncAlarm()     // Cancel any scheduled critical sync alarms
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        serviceJob.cancel() // Cancel coroutines
        Log.w(TAG, "All resources cleaned up in onDestroy.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DataUpdateFGService" // Changed for clarity

        // Notification Channel IDs (ensure these are unique strings)
        const val ONGOING_CHANNEL_ID = "DataUpdateForegroundServiceChannel_HermesOngoing"
        const val ALARM_CHANNEL_ID = "DataUpdateAlarmChannelWithCustomSound_HermesAlarm"

        // Notification IDs (ensure these are unique integers)
        const val FOREGROUND_SERVICE_ID = 101 // For startForeground
        const val ALARM_NOTIFICATION_ID_NUMBER = 102 // For alarm notifications

        // PendingIntent Request Codes (ensure these are unique integers)
        private const val PENDING_INTENT_REQUEST_CODE_CRITICAL_SYNC = 1001
        private const val PENDING_INTENT_REQUEST_CODE_ONGOING_NOTIFICATION = 1002
        private const val PENDING_INTENT_REQUEST_CODE_ALARM_NOTIFICATION = 1003


        // Intervals
        const val UPDATE_INTERVAL_HANDLER_MS = 60 * 1000L // 1 minute for Handler-based UI sync
        const val CRITICAL_SYNC_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes for AlarmManager critical sync

        // Actions
        const val ACTION_START_SERVICE = "com.levi.hermes_trading.ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_SERVICE = "com.levi.hermes_trading.ACTION_STOP_FOREGROUND_SERVICE"
        const val ACTION_CRITICAL_SYNC_AND_ALARM_CHECK = "com.levi.hermes_trading.ACTION_CRITICAL_SYNC_AND_ALARM_CHECK"
        const val ACTION_START_HANDLER_SYNC = "com.levi.hermes_trading.ACTION_START_HANDLER_SYNC"
        const val ACTION_STOP_HANDLER_SYNC = "com.levi.hermes_trading.ACTION_STOP_HANDLER_SYNC"

        const val ACTION_DATA_UPDATED = "com.levi.hermes_trading.DATA_UPDATED"


        // Public method to start the service for general purpose (e.g., from app UI to enable handler sync)
        fun startHermesService(context: Context) {
            val startIntent = Intent(context, DataUpdateForegroundService::class.java)
            startIntent.action = ACTION_START_SERVICE
            ContextCompat.startForegroundService(context, startIntent)
        }

        // Public method to stop the service completely
        fun stopHermesService(context: Context) {
            val stopIntent = Intent(context, DataUpdateForegroundService::class.java)
            // No action needed for stopService, but can set one if desired for specific stop logic in onStartCommand
            // stopIntent.action = ACTION_STOP_SERVICE // Optional: can be handled in onStartCommand
            context.stopService(stopIntent) // This will trigger onDestroy
        }

        // Method to explicitly trigger a critical sync (e.g., on app start or after settings change)
        // This can also be used to schedule the first critical alarm.
        fun scheduleInitialOrManualCriticalSync(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_CRITICAL_SYNC_AND_ALARM_CHECK
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                PENDING_INTENT_REQUEST_CODE_CRITICAL_SYNC,
                intent,
                pendingIntentFlags
            )
            // Schedule it to run almost immediately for an initial sync,
            // or after a short delay to allow app startup.
            val triggerAtMillis = SystemClock.elapsedRealtime() + 1000 // 1 second from now

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                }
            }
            Log.i(TAG, "Initial/Manual CRITICAL sync scheduled via AlarmManager.")
        }
    }
}