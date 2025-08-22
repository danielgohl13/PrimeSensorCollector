package com.example.smartphonecollector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smartphonecollector.MainActivity
import com.example.smartphonecollector.R
import com.example.smartphonecollector.communication.WearableCommunicationService
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.repository.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background service for maintaining wearable communication and data collection
 * Keeps running even when the app is not in foreground
 */
class DataCollectionService : Service() {
    
    companion object {
        private const val TAG = "DataCollectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "data_collection_channel"
        
        fun startService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java)
            context.stopService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var dataRepository: DataRepository
    private var communicationService: WearableCommunicationService? = null
    private var isCollecting = false
    private var currentSessionId: String? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DataCollectionService created")
        
        // Initialize repository
        dataRepository = DataRepository(this)
        
        // Create notification channel
        createNotificationChannel()
        
        // Initialize communication service
        initializeCommunicationService()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DataCollectionService started")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Ready to collect data"))
        
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DataCollectionService destroyed")
        
        communicationService?.cleanup()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Collection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection with wearable device"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wearable Data Collector")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }
    
    private fun initializeCommunicationService() {
        try {
            communicationService = WearableCommunicationService(
                context = this,
                onInertialDataReceived = { inertialReading ->
                    handleInertialDataReceived(inertialReading)
                },
                onBiometricDataReceived = { biometricReading ->
                    handleBiometricDataReceived(biometricReading)
                },
                onStartCollectionRequested = { sessionId ->
                    handleStartCollectionRequest(sessionId)
                },
                onStopCollectionRequested = {
                    handleStopCollectionRequest()
                }
            )
            
            Log.d(TAG, "Communication service initialized in background")
            updateNotification("Connected to wearable")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize communication service", e)
            updateNotification("Connection error")
        }
    }
    
    private fun handleInertialDataReceived(inertialReading: InertialReading) {
        serviceScope.launch {
            try {
                if (isCollecting && currentSessionId != null) {
                    val result = dataRepository.appendInertialData(currentSessionId!!, inertialReading)
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to save inertial data", result.exceptionOrNull())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling inertial data", e)
            }
        }
    }
    
    private fun handleBiometricDataReceived(biometricReading: BiometricReading) {
        serviceScope.launch {
            try {
                if (isCollecting && currentSessionId != null) {
                    val result = dataRepository.appendBiometricData(currentSessionId!!, biometricReading)
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to save biometric data", result.exceptionOrNull())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling biometric data", e)
            }
        }
    }
    
    private fun handleStartCollectionRequest(sessionId: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Background service: Start collection requested - $sessionId")
                
                if (isCollecting) {
                    Log.w(TAG, "Collection already in progress")
                    return@launch
                }
                
                // Check storage
                if (!dataRepository.isStorageAvailable()) {
                    Log.e(TAG, "Storage not available")
                    return@launch
                }
                
                // Start collection
                currentSessionId = sessionId
                isCollecting = true
                
                updateNotification("Collecting data from wearable...")
                Log.d(TAG, "Background collection started: $sessionId")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting background collection", e)
                updateNotification("Collection start failed")
            }
        }
    }
    
    private fun handleStopCollectionRequest() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Background service: Stop collection requested")
                
                if (!isCollecting) {
                    Log.w(TAG, "No active collection")
                    return@launch
                }
                
                isCollecting = false
                currentSessionId = null
                
                updateNotification("Collection completed")
                Log.d(TAG, "Background collection stopped")
                
                // Show completion notification
                showCompletionNotification()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping background collection", e)
            }
        }
    }
    
    private fun showCompletionNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val completionNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Data Collection Complete")
            .setContentText("Wearable data collection session finished")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID + 1, completionNotification)
    }
}