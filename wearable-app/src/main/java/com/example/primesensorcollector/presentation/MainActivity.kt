package com.example.primesensorcollector.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.primesensorcollector.data.transmission.WearableCommunicationClientImpl
import com.example.primesensorcollector.presentation.theme.PrimeSensorCollectorTheme
import com.example.primesensorcollector.service.SensorCollectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * WearableMainActivity with Wear OS Compose UI
 * Provides collection status display and touch controls for start/stop collection requests
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "WearableMainActivity"
    }
    
    private lateinit var sensorCollectionManager: SensorCollectionManager
    private lateinit var communicationClient: WearableCommunicationClientImpl
    
    // UI State
    private var isCollecting by mutableStateOf(false)
    private var batteryLevel by mutableStateOf(100)
    private var sessionStartTime by mutableStateOf<Long?>(null)
    private var sessionDuration by mutableStateOf("00:00")
    private var connectionStatus by mutableStateOf(false)
    private var showLowBatteryWarning by mutableStateOf(false)
    private var showCompletionMessage by mutableStateOf(false)
    private var completionMessage by mutableStateOf("")
    
    // Battery monitoring
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level * 100 / scale)
                    showLowBatteryWarning = batteryLevel <= 15
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setTheme(android.R.style.Theme_DeviceDefault)
        
        // Initialize components
        initializeComponents()
        
        // Register battery receiver
        registerBatteryReceiver()
        
        // Start monitoring session duration
        startSessionDurationMonitoring()
        
        setContent {
            WearApp(
                isCollecting = isCollecting,
                batteryLevel = batteryLevel,
                sessionDuration = sessionDuration,
                connectionStatus = connectionStatus,
                showLowBatteryWarning = showLowBatteryWarning,
                showCompletionMessage = showCompletionMessage,
                completionMessage = completionMessage,
                onTapGesture = { handleTapGesture() }
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering battery receiver", e)
        }
        
        sensorCollectionManager.unbindService()
        communicationClient.cleanup()
    }
    
    /**
     * Initialize sensor collection manager and communication client
     */
    private fun initializeComponents() {
        sensorCollectionManager = SensorCollectionManager(this)
        communicationClient = WearableCommunicationClientImpl(this)
        
        // Initialize communication client
        lifecycleScope.launch {
            val success = communicationClient.initialize()
            connectionStatus = success
            Log.d(TAG, "Communication client initialized: $success")
        }
        
        // Monitor connection status
        lifecycleScope.launch {
            while (true) {
                connectionStatus = communicationClient.isConnected()
                delay(5000) // Check every 5 seconds
            }
        }
    }
    
    /**
     * Register battery level monitoring
     */
    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        
        // Get initial battery level
        val batteryStatus = registerReceiver(null, filter)
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                batteryLevel = (level * 100 / scale)
                showLowBatteryWarning = batteryLevel <= 15
            }
        }
    }
    
    /**
     * Start monitoring session duration
     */
    private fun startSessionDurationMonitoring() {
        lifecycleScope.launch {
            while (true) {
                updateSessionDuration()
                delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Update session duration display
     */
    private fun updateSessionDuration() {
        val startTime = sessionStartTime
        if (isCollecting && startTime != null) {
            val currentTime = System.currentTimeMillis()
            val durationMs = currentTime - startTime
            val minutes = (durationMs / 60000).toInt()
            val seconds = ((durationMs % 60000) / 1000).toInt()
            sessionDuration = String.format("%02d:%02d", minutes, seconds)
        } else {
            sessionDuration = "00:00"
        }
    }
    
    /**
     * Handle tap gesture for start/stop collection requests
     * Requirements: 7.2, 7.3
     */
    private fun handleTapGesture() {
        Log.d(TAG, "Tap gesture detected, isCollecting: $isCollecting")
        
        lifecycleScope.launch {
            if (!isCollecting) {
                // Send start request to smartphone
                requestStartCollection()
            } else {
                // Send stop request to smartphone
                requestStopCollection()
            }
        }
    }
    
    /**
     * Request start collection from smartphone
     */
    private suspend fun requestStartCollection() {
        Log.d(TAG, "Requesting start collection from smartphone")
        
        if (!connectionStatus) {
            Log.w(TAG, "Cannot start collection - no connection to smartphone")
            return
        }
        
        try {
            // Generate session ID and device ID
            val sessionId = "wearable_${System.currentTimeMillis()}"
            val deviceId = "watch_${android.os.Build.MODEL}"
            
            // Send start command to smartphone
            val commandData = "$sessionId|$deviceId"
            val success = communicationClient.sendMessage(
                WearableCommunicationClientImpl.COMMAND_START_COLLECTION,
                commandData
            )
            
            if (success) {
                // Start local sensor collection
                sensorCollectionManager.startCollection(sessionId, deviceId)
                
                // Update local state
                isCollecting = true
                sessionStartTime = System.currentTimeMillis()
                Log.d(TAG, "Start collection request sent successfully and local collection started")
            } else {
                Log.e(TAG, "Failed to send start collection request")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting start collection", e)
        }
    }
    
    /**
     * Request stop collection from smartphone
     */
    private suspend fun requestStopCollection() {
        Log.d(TAG, "Requesting stop collection from smartphone")
        
        if (!connectionStatus) {
            Log.w(TAG, "Cannot stop collection - no connection to smartphone")
            // Still update local state even if we can't communicate
            isCollecting = false
            sessionStartTime = null
            return
        }
        
        try {
            // Send stop command to smartphone
            val success = communicationClient.sendMessage(
                WearableCommunicationClientImpl.COMMAND_STOP_COLLECTION,
                ""
            )
            
            if (success) {
                // Stop local sensor collection
                sensorCollectionManager.stopCollection()
                
                // Show completion feedback
                showCompletionFeedback()
                
                // Update local state
                isCollecting = false
                sessionStartTime = null
                Log.d(TAG, "Stop collection request sent successfully and local collection stopped")
            } else {
                Log.e(TAG, "Failed to send stop collection request")
                // Still stop local collection and update state
                sensorCollectionManager.stopCollection()
                showCompletionFeedback()
                isCollecting = false
                sessionStartTime = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting stop collection", e)
            // Still stop local collection and update state
            sensorCollectionManager.stopCollection()
            showCompletionFeedback()
            isCollecting = false
            sessionStartTime = null
        }
    }
    
    /**
     * Show completion feedback to user
     */
    private fun showCompletionFeedback() {
        val duration = sessionDuration
        completionMessage = "Collection Complete\n$duration"
        showCompletionMessage = true
        
        // Hide the message after 3 seconds
        lifecycleScope.launch {
            delay(3000)
            showCompletionMessage = false
        }
    }
}

/**
 * Main Wear OS Compose UI
 * Requirements: 7.1, 7.4, 7.5
 */
@Composable
fun WearApp(
    isCollecting: Boolean,
    batteryLevel: Int,
    sessionDuration: String,
    connectionStatus: Boolean,
    showLowBatteryWarning: Boolean,
    showCompletionMessage: Boolean,
    completionMessage: String,
    onTapGesture: () -> Unit
) {
    PrimeSensorCollectorTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .clickable { onTapGesture() },
            contentAlignment = Alignment.Center
        ) {
            // Time display at top
            TimeText()
            
            // Main content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                
                // Collection status indicator
                CollectionStatusIndicator(
                    isCollecting = isCollecting,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Collection status text
                Text(
                    text = if (isCollecting) "COLLECTING" else "TAP TO START",
                    color = if (isCollecting) Color.Green else MaterialTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Session duration (only show when collecting)
                if (isCollecting) {
                    Text(
                        text = sessionDuration,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Battery level and connection status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Battery level
                    BatteryLevelDisplay(
                        batteryLevel = batteryLevel,
                        showWarning = showLowBatteryWarning
                    )
                    
                    // Connection status
                    ConnectionStatusIndicator(
                        isConnected = connectionStatus
                    )
                }
                
                // Low battery warning
                if (showLowBatteryWarning) {
                    Text(
                        text = "LOW BATTERY",
                        color = Color.Red,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Completion message
                if (showCompletionMessage) {
                    Text(
                        text = completionMessage,
                        color = Color.Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Collection status indicator component
 * Shows a visual indicator of active/inactive collection state
 */
@Composable
fun CollectionStatusIndicator(
    isCollecting: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isCollecting) Color.Green else Color.Gray
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isCollecting) "●" else "○",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Battery level display component
 * Requirements: 7.4, 7.5
 */
@Composable
fun BatteryLevelDisplay(
    batteryLevel: Int,
    showWarning: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = "🔋 $batteryLevel%",
        color = if (showWarning) Color.Red else MaterialTheme.colors.onBackground,
        fontSize = 10.sp,
        fontWeight = if (showWarning) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier
    )
}

/**
 * Connection status indicator component
 */
@Composable
fun ConnectionStatusIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (isConnected) "📱" else "❌",
        fontSize = 12.sp,
        modifier = modifier
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(
        isCollecting = false,
        batteryLevel = 85,
        sessionDuration = "02:34",
        connectionStatus = true,
        showLowBatteryWarning = false,
        showCompletionMessage = false,
        completionMessage = "",
        onTapGesture = {}
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun CollectingPreview() {
    WearApp(
        isCollecting = true,
        batteryLevel = 45,
        sessionDuration = "05:42",
        connectionStatus = true,
        showLowBatteryWarning = false,
        showCompletionMessage = false,
        completionMessage = "",
        onTapGesture = {}
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun LowBatteryPreview() {
    WearApp(
        isCollecting = true,
        batteryLevel = 12,
        sessionDuration = "01:15",
        connectionStatus = false,
        showLowBatteryWarning = true,
        showCompletionMessage = false,
        completionMessage = "",
        onTapGesture = {}
    )
}