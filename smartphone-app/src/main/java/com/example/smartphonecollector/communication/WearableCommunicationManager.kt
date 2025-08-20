package com.example.smartphonecollector.communication

import android.content.Context
import android.util.Log
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.pow

/**
 * High-level manager for wearable communication with error handling and retry logic
 * Implements exponential backoff for failed operations and connection monitoring
 */
class WearableCommunicationManager(
    private val context: Context,
    private val onInertialDataReceived: (InertialReading) -> Unit,
    private val onBiometricDataReceived: (BiometricReading) -> Unit,
    private val onConnectionStatusChanged: (ConnectionStatus) -> Unit
) {
    companion object {
        private const val TAG = "WearableCommunicationManager"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val CONNECTION_TIMEOUT_MS = 30000L
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var communicationService: WearableCommunicationService? = null
    
    // Connection state management
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    // Retry state
    private var currentRetryAttempt = 0
    private var retryJob: Job? = null

    /**
     * Initialize the communication manager
     */
    fun initialize() {
        if (_isInitialized.value) {
            Log.w(TAG, "Communication manager already initialized")
            return
        }
        
        try {
            communicationService = WearableCommunicationService(
                context = context,
                onInertialDataReceived = onInertialDataReceived,
                onBiometricDataReceived = onBiometricDataReceived
            )
            
            // Monitor connection status changes
            managerScope.launch {
                communicationService?.connectionStatus?.collect { status ->
                    _connectionStatus.value = status
                    onConnectionStatusChanged(status)
                    
                    // Reset retry attempts on successful connection
                    if (status == ConnectionStatus.CONNECTED) {
                        currentRetryAttempt = 0
                        retryJob?.cancel()
                    }
                }
            }
            
            _isInitialized.value = true
            Log.d(TAG, "Communication manager initialized successfully")
            
            // Start initial connection check
            managerScope.launch {
                checkConnection()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize communication manager", e)
            _connectionStatus.value = ConnectionStatus.ERROR
        }
    }

    /**
     * Start data collection with retry logic
     */
    suspend fun startCollection(sessionId: String): Boolean {
        return executeWithRetry("startCollection") {
            communicationService?.startCollection(sessionId) ?: false
        }
    }

    /**
     * Stop data collection with retry logic
     */
    suspend fun stopCollection(): Boolean {
        return executeWithRetry("stopCollection") {
            communicationService?.stopCollection() ?: false
        }
    }

    /**
     * Check connection status
     */
    suspend fun checkConnection(): Boolean {
        return try {
            communicationService?.checkConnectionStatus() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            false
        }
    }

    /**
     * Execute an operation with exponential backoff retry logic
     */
    private suspend fun executeWithRetry(
        operationName: String,
        operation: suspend () -> Boolean
    ): Boolean {
        var attempt = 0
        
        while (attempt <= MAX_RETRY_ATTEMPTS) {
            try {
                Log.d(TAG, "Executing $operationName (attempt ${attempt + 1})")
                
                val result = operation()
                if (result) {
                    Log.d(TAG, "$operationName succeeded on attempt ${attempt + 1}")
                    currentRetryAttempt = 0
                    return true
                }
                
                Log.w(TAG, "$operationName failed on attempt ${attempt + 1}")
                
            } catch (e: Exception) {
                Log.e(TAG, "$operationName threw exception on attempt ${attempt + 1}", e)
            }
            
            attempt++
            
            if (attempt <= MAX_RETRY_ATTEMPTS) {
                val delayMs = calculateRetryDelay(attempt)
                Log.d(TAG, "Retrying $operationName in ${delayMs}ms")
                delay(delayMs)
            }
        }
        
        Log.e(TAG, "$operationName failed after $MAX_RETRY_ATTEMPTS attempts")
        _connectionStatus.value = ConnectionStatus.ERROR
        return false
    }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val exponentialDelay = BASE_RETRY_DELAY_MS * (2.0.pow(attempt - 1)).toLong()
        return min(exponentialDelay, MAX_RETRY_DELAY_MS)
    }

    /**
     * Start automatic connection recovery
     */
    fun startConnectionRecovery() {
        if (retryJob?.isActive == true) {
            Log.d(TAG, "Connection recovery already in progress")
            return
        }
        
        retryJob = managerScope.launch {
            Log.d(TAG, "Starting connection recovery")
            
            while (coroutineContext.isActive && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                try {
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    
                    val connected = checkConnection()
                    if (connected) {
                        Log.d(TAG, "Connection recovery successful")
                        break
                    }
                    
                    currentRetryAttempt++
                    val delayMs = calculateRetryDelay(currentRetryAttempt)
                    
                    Log.d(TAG, "Connection recovery attempt $currentRetryAttempt failed, retrying in ${delayMs}ms")
                    delay(delayMs)
                    
                    if (currentRetryAttempt >= MAX_RETRY_ATTEMPTS) {
                        Log.e(TAG, "Connection recovery failed after $MAX_RETRY_ATTEMPTS attempts")
                        _connectionStatus.value = ConnectionStatus.ERROR
                        break
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during connection recovery", e)
                    _connectionStatus.value = ConnectionStatus.ERROR
                    delay(calculateRetryDelay(currentRetryAttempt))
                }
            }
        }
    }

    /**
     * Stop connection recovery
     */
    fun stopConnectionRecovery() {
        retryJob?.cancel()
        retryJob = null
        currentRetryAttempt = 0
        Log.d(TAG, "Connection recovery stopped")
    }

    /**
     * Handle connection loss - start automatic recovery
     */
    fun onConnectionLost() {
        Log.w(TAG, "Connection lost, starting recovery")
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        startConnectionRecovery()
    }

    /**
     * Get current connection status
     */
    fun getCurrentConnectionStatus(): ConnectionStatus {
        return _connectionStatus.value
    }

    /**
     * Check if the manager is ready for operations
     */
    fun isReady(): Boolean {
        return _isInitialized.value && 
               communicationService != null && 
               _connectionStatus.value == ConnectionStatus.CONNECTED
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up communication manager")
        
        stopConnectionRecovery()
        communicationService?.cleanup()
        communicationService = null
        
        _isInitialized.value = false
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        
        managerScope.cancel()
    }
}