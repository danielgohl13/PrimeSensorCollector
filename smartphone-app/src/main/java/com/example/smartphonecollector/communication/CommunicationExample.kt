package com.example.smartphonecollector.communication

import android.content.Context
import android.util.Log
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.repository.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Example class demonstrating how to use the WearableCommunicationManager
 * This shows the integration pattern for ViewModels or other components
 */
class CommunicationExample(
    private val context: Context,
    private val dataRepository: DataRepository
) {
    companion object {
        private const val TAG = "CommunicationExample"
    }

    private val exampleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private lateinit var communicationManager: WearableCommunicationManager
    
    // Current session tracking
    private var currentSessionId: String? = null
    
    /**
     * Initialize the communication system
     */
    fun initialize() {
        communicationManager = WearableCommunicationManager(
            context = context,
            onInertialDataReceived = ::handleInertialData,
            onBiometricDataReceived = ::handleBiometricData,
            onConnectionStatusChanged = ::handleConnectionStatusChange
        )
        
        communicationManager.initialize()
        Log.d(TAG, "Communication system initialized")
    }

    /**
     * Start a data collection session
     */
    fun startDataCollection() {
        exampleScope.launch {
            try {
                // Create a new session
                val sessionData = dataRepository.createSession()
                currentSessionId = sessionData.sessionId
                Log.d(TAG, "Created session: $currentSessionId")
                
                // Start collection on wearable
                val success = communicationManager.startCollection(currentSessionId!!)
                if (success) {
                    Log.d(TAG, "Data collection started successfully")
                } else {
                    Log.e(TAG, "Failed to start data collection")
                    currentSessionId = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting data collection", e)
                currentSessionId = null
            }
        }
    }

    /**
     * Stop the current data collection session
     */
    fun stopDataCollection() {
        exampleScope.launch {
            try {
                val success = communicationManager.stopCollection()
                if (success) {
                    Log.d(TAG, "Data collection stopped successfully")
                } else {
                    Log.e(TAG, "Failed to stop data collection")
                }
                
                currentSessionId = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping data collection", e)
            }
        }
    }

    /**
     * Check if currently collecting data
     */
    fun isCollecting(): Boolean {
        return currentSessionId != null && 
               communicationManager.getCurrentConnectionStatus() == ConnectionStatus.CONNECTED
    }

    /**
     * Get the connection status as a StateFlow for UI observation
     */
    fun getConnectionStatus(): StateFlow<ConnectionStatus> {
        return communicationManager.connectionStatus
    }

    /**
     * Handle incoming inertial sensor data
     */
    private fun handleInertialData(reading: InertialReading) {
        exampleScope.launch {
            try {
                // Save the data immediately
                dataRepository.saveInertialData(reading.sessionId, listOf(reading))
                Log.d(TAG, "Saved inertial reading: ${reading.timestamp}")
                
                // Here you could also update UI with real-time data
                // For example: updateRealTimeChart(reading)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving inertial data", e)
            }
        }
    }

    /**
     * Handle incoming biometric sensor data
     */
    private fun handleBiometricData(reading: BiometricReading) {
        exampleScope.launch {
            try {
                // Save the data immediately
                dataRepository.saveBiometricData(reading.sessionId, listOf(reading))
                Log.d(TAG, "Saved biometric reading: ${reading.timestamp}")
                
                // Here you could also update UI with real-time data
                // For example: updateBiometricDisplay(reading)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving biometric data", e)
            }
        }
    }

    /**
     * Handle connection status changes
     */
    private fun handleConnectionStatusChange(status: ConnectionStatus) {
        Log.d(TAG, "Connection status changed to: $status")
        
        when (status) {
            ConnectionStatus.CONNECTED -> {
                Log.d(TAG, "Wearable device connected")
                // UI could show connected indicator
            }
            ConnectionStatus.DISCONNECTED -> {
                Log.w(TAG, "Wearable device disconnected")
                // UI could show disconnected indicator
                // Consider stopping collection if active
            }
            ConnectionStatus.CONNECTING -> {
                Log.d(TAG, "Connecting to wearable device...")
                // UI could show connecting indicator
            }
            ConnectionStatus.ERROR -> {
                Log.e(TAG, "Connection error occurred")
                // UI could show error indicator
                // Consider stopping collection if active
                if (currentSessionId != null) {
                    stopDataCollection()
                }
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        if (isCollecting()) {
            stopDataCollection()
        }
        
        communicationManager.cleanup()
        Log.d(TAG, "Communication system cleaned up")
    }

    /**
     * Force connection check (useful for manual retry)
     */
    fun checkConnection() {
        exampleScope.launch {
            val connected = communicationManager.checkConnection()
            Log.d(TAG, "Manual connection check result: $connected")
        }
    }

    /**
     * Get current session ID if collecting
     */
    fun getCurrentSessionId(): String? {
        return currentSessionId
    }
}