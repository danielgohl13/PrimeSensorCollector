package com.example.primesensorcollector.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.InertialReading
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manager class to handle SensorCollectionService interactions
 * Provides a simplified interface for starting/stopping collection and accessing data flows
 */
class SensorCollectionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SensorCollectionManager"
    }
    
    private var service: SensorCollectionService? = null
    private var isBound = false
    
    /**
     * Service connection to bind to SensorCollectionService
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected")
            // Note: SensorCollectionService doesn't return a binder since it's a started service
            // We'll access it through static methods or broadcasts
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            service = null
            isBound = false
        }
    }
    
    /**
     * Start sensor data collection
     */
    fun startCollection(sessionId: String, deviceId: String) {
        Log.d(TAG, "Starting collection for session: $sessionId")
        
        val intent = Intent(context, SensorCollectionService::class.java).apply {
            action = SensorCollectionService.ACTION_START_COLLECTION
            putExtra(SensorCollectionService.EXTRA_SESSION_ID, sessionId)
            putExtra(SensorCollectionService.EXTRA_DEVICE_ID, deviceId)
        }
        
        context.startService(intent)
    }
    
    /**
     * Stop sensor data collection
     */
    fun stopCollection() {
        Log.d(TAG, "Stopping collection")
        
        val intent = Intent(context, SensorCollectionService::class.java).apply {
            action = SensorCollectionService.ACTION_STOP_COLLECTION
        }
        
        context.startService(intent)
    }
    
    /**
     * Check if collection is currently active
     */
    fun isCollectionActive(): Boolean {
        return service?.isCollectionActive() ?: false
    }
    
    /**
     * Get current session ID
     */
    fun getCurrentSessionId(): String? {
        return service?.getCurrentSessionId()
    }
    
    /**
     * Get inertial data flow
     */
    fun getInertialDataFlow(): SharedFlow<InertialReading> {
        return service?.inertialDataFlow ?: MutableSharedFlow<InertialReading>().asSharedFlow()
    }
    
    /**
     * Get biometric data flow
     */
    fun getBiometricDataFlow(): SharedFlow<BiometricReading> {
        return service?.biometricDataFlow ?: MutableSharedFlow<BiometricReading>().asSharedFlow()
    }
    
    /**
     * Bind to the service (optional, for direct access)
     */
    fun bindService() {
        if (!isBound) {
            val intent = Intent(context, SensorCollectionService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    /**
     * Unbind from the service
     */
    fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}