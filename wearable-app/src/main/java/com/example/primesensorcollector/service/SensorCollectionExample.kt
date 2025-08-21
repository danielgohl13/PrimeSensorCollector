package com.example.primesensorcollector.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID

/**
 * Example class demonstrating how to use SensorCollectionService
 * This shows the integration pattern for collecting sensor data
 */
class SensorCollectionExample(private val context: Context) {
    
    companion object {
        private const val TAG = "SensorCollectionExample"
    }
    
    private val manager = SensorCollectionManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    /**
     * Start a data collection session
     */
    fun startDataCollection() {
        val sessionId = "session_${UUID.randomUUID()}"
        val deviceId = "galaxy_watch_5"
        
        Log.d(TAG, "Starting data collection session: $sessionId")
        
        // Subscribe to data flows before starting collection
        subscribeToDataFlows()
        
        // Start the collection service
        manager.startCollection(sessionId, deviceId)
    }
    
    /**
     * Stop the current data collection session
     */
    fun stopDataCollection() {
        Log.d(TAG, "Stopping data collection")
        manager.stopCollection()
    }
    
    /**
     * Subscribe to sensor data flows
     */
    private fun subscribeToDataFlows() {
        // Subscribe to inertial data
        manager.getInertialDataFlow()
            .onEach { reading ->
                Log.d(TAG, "Inertial data: " +
                        "accel=(${reading.accelerometer.x}, ${reading.accelerometer.y}, ${reading.accelerometer.z}), " +
                        "gyro=(${reading.gyroscope.x}, ${reading.gyroscope.y}, ${reading.gyroscope.z}), " +
                        "mag=(${reading.magnetometer.x}, ${reading.magnetometer.y}, ${reading.magnetometer.z}), " +
                        "battery=${reading.batteryLevel}%")
                
                // In a real implementation, this data would be:
                // 1. Buffered locally
                // 2. Transmitted to smartphone
                // 3. Saved to CSV files
            }
            .launchIn(scope)
        
        // Subscribe to biometric data
        manager.getBiometricDataFlow()
            .onEach { reading ->
                Log.d(TAG, "Biometric data: " +
                        "hr=${reading.heartRate}, " +
                        "steps=${reading.stepCount}, " +
                        "calories=${reading.calories}, " +
                        "temp=${reading.skinTemperature}, " +
                        "battery=${reading.batteryLevel}%")
                
                // In a real implementation, this data would be:
                // 1. Buffered locally
                // 2. Transmitted to smartphone
                // 3. Saved to CSV files
            }
            .launchIn(scope)
    }
    
    /**
     * Check if collection is currently active
     */
    fun isCollecting(): Boolean {
        return manager.isCollectionActive()
    }
    
    /**
     * Get current session ID
     */
    fun getCurrentSession(): String? {
        return manager.getCurrentSessionId()
    }
}