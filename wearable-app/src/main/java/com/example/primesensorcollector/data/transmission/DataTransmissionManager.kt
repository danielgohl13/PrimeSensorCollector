package com.example.primesensorcollector.data.transmission

import android.util.Log
import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.serialization.DataSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages data buffering and transmission from wearable to smartphone
 * Implements local buffering, capacity management, batch transmission, and retry logic
 */
class DataTransmissionManager(
    private val communicationClient: WearableCommunicationClient
) {
    companion object {
        private const val TAG = "DataTransmissionManager"
        
        // Buffer configuration
        private const val MAX_BUFFER_SIZE = 1000 // Maximum number of readings to buffer
        private const val BUFFER_WARNING_THRESHOLD = 800 // 80% capacity warning
        private const val TRANSMISSION_INTERVAL_MS = 1000L // 1 second batch transmission
        
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_RETRY_DELAY_MS = 1000L // 1 second
        private const val MAX_RETRY_DELAY_MS = 30000L // 30 seconds
        
        // Message types for communication
        const val MESSAGE_TYPE_INERTIAL_BATCH = "inertial_batch"
        const val MESSAGE_TYPE_BIOMETRIC_BATCH = "biometric_batch"
    }
    
    // Data buffers
    private val inertialBuffer = ConcurrentLinkedQueue<InertialReading>()
    private val biometricBuffer = ConcurrentLinkedQueue<BiometricReading>()
    
    // Buffer size tracking
    private val inertialBufferSize = AtomicInteger(0)
    private val biometricBufferSize = AtomicInteger(0)
    
    // Transmission state
    private val isTransmitting = AtomicBoolean(false)
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _bufferStatus = MutableStateFlow(BufferStatus())
    val bufferStatus: StateFlow<BufferStatus> = _bufferStatus.asStateFlow()
    
    // Coroutine management
    private val transmissionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transmissionJob: Job? = null
    
    /**
     * Start the data transmission manager
     */
    fun start() {
        Log.d(TAG, "Starting DataTransmissionManager")
        
        // Start periodic transmission
        startPeriodicTransmission()
        
        // Monitor connection status
        monitorConnectionStatus()
    }
    
    /**
     * Stop the data transmission manager
     */
    fun stop() {
        Log.d(TAG, "Stopping DataTransmissionManager")
        
        transmissionJob?.cancel()
        transmissionScope.cancel()
        
        // Clear buffers
        clearBuffers()
    }
    
    /**
     * Buffer inertial sensor reading
     */
    fun bufferInertialReading(reading: InertialReading) {
        if (inertialBufferSize.get() >= MAX_BUFFER_SIZE) {
            handleBufferOverflow("inertial")
            return
        }
        
        inertialBuffer.offer(reading)
        val newSize = inertialBufferSize.incrementAndGet()
        
        updateBufferStatus()
        
        if (newSize >= BUFFER_WARNING_THRESHOLD) {
            Log.w(TAG, "Inertial buffer approaching capacity: $newSize/$MAX_BUFFER_SIZE")
        }
    }
    
    /**
     * Buffer biometric sensor reading
     */
    fun bufferBiometricReading(reading: BiometricReading) {
        if (biometricBufferSize.get() >= MAX_BUFFER_SIZE) {
            handleBufferOverflow("biometric")
            return
        }
        
        biometricBuffer.offer(reading)
        val newSize = biometricBufferSize.incrementAndGet()
        
        updateBufferStatus()
        
        if (newSize >= BUFFER_WARNING_THRESHOLD) {
            Log.w(TAG, "Biometric buffer approaching capacity: $newSize/$MAX_BUFFER_SIZE")
        }
    }
    
    /**
     * Handle buffer overflow by removing oldest entries
     */
    private fun handleBufferOverflow(bufferType: String) {
        Log.w(TAG, "Buffer overflow detected for $bufferType data, removing oldest entries")
        
        when (bufferType) {
            "inertial" -> {
                // Remove oldest 10% of entries
                val removeCount = MAX_BUFFER_SIZE / 10
                repeat(removeCount) {
                    if (inertialBuffer.poll() != null) {
                        inertialBufferSize.decrementAndGet()
                    }
                }
            }
            "biometric" -> {
                val removeCount = MAX_BUFFER_SIZE / 10
                repeat(removeCount) {
                    if (biometricBuffer.poll() != null) {
                        biometricBufferSize.decrementAndGet()
                    }
                }
            }
        }
        
        updateBufferStatus()
    }
    
    /**
     * Start periodic transmission of buffered data
     */
    private fun startPeriodicTransmission() {
        transmissionJob = transmissionScope.launch {
            while (isActive) {
                try {
                    if (_isConnected.value && !isTransmitting.get()) {
                        transmitBufferedData()
                    }
                    delay(TRANSMISSION_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic transmission", e)
                    delay(TRANSMISSION_INTERVAL_MS)
                }
            }
        }
    }
    
    /**
     * Transmit all buffered data to smartphone
     */
    private suspend fun transmitBufferedData() {
        if (!isTransmitting.compareAndSet(false, true)) {
            return // Already transmitting
        }
        
        try {
            // Transmit inertial data batch
            transmitInertialBatch()
            
            // Transmit biometric data batch
            transmitBiometricBatch()
            
        } finally {
            isTransmitting.set(false)
        }
    }
    
    /**
     * Transmit inertial data batch
     */
    private suspend fun transmitInertialBatch() {
        val batchSize = inertialBufferSize.get()
        if (batchSize == 0) return
        
        val batch = mutableListOf<InertialReading>()
        
        // Collect batch data
        repeat(batchSize) {
            inertialBuffer.poll()?.let { reading ->
                batch.add(reading)
                inertialBufferSize.decrementAndGet()
            }
        }
        
        if (batch.isNotEmpty()) {
            Log.d(TAG, "Transmitting inertial batch: ${batch.size} readings")
            
            val success = transmitWithRetry(
                messageType = MESSAGE_TYPE_INERTIAL_BATCH,
                data = DataSerializer.serializeInertialReadings(batch)
            )
            
            if (!success) {
                // Re-buffer failed data
                batch.forEach { reading ->
                    if (inertialBufferSize.get() < MAX_BUFFER_SIZE) {
                        inertialBuffer.offer(reading)
                        inertialBufferSize.incrementAndGet()
                    }
                }
                Log.w(TAG, "Failed to transmit inertial batch, re-buffered ${batch.size} readings")
            }
        }
        
        updateBufferStatus()
    }
    
    /**
     * Transmit biometric data batch
     */
    private suspend fun transmitBiometricBatch() {
        val batchSize = biometricBufferSize.get()
        if (batchSize == 0) return
        
        val batch = mutableListOf<BiometricReading>()
        
        // Collect batch data
        repeat(batchSize) {
            biometricBuffer.poll()?.let { reading ->
                batch.add(reading)
                biometricBufferSize.decrementAndGet()
            }
        }
        
        if (batch.isNotEmpty()) {
            Log.d(TAG, "Transmitting biometric batch: ${batch.size} readings")
            
            val success = transmitWithRetry(
                messageType = MESSAGE_TYPE_BIOMETRIC_BATCH,
                data = DataSerializer.serializeBiometricReadings(batch)
            )
            
            if (!success) {
                // Re-buffer failed data
                batch.forEach { reading ->
                    if (biometricBufferSize.get() < MAX_BUFFER_SIZE) {
                        biometricBuffer.offer(reading)
                        biometricBufferSize.incrementAndGet()
                    }
                }
                Log.w(TAG, "Failed to transmit biometric batch, re-buffered ${batch.size} readings")
            }
        }
        
        updateBufferStatus()
    } 
   
    /**
     * Transmit data with exponential backoff retry logic
     */
    private suspend fun transmitWithRetry(messageType: String, data: String): Boolean {
        var attempt = 0
        var delay = INITIAL_RETRY_DELAY_MS
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                val success = communicationClient.sendMessage(messageType, data)
                if (success) {
                    if (attempt > 0) {
                        Log.d(TAG, "Transmission succeeded on attempt ${attempt + 1}")
                    }
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Transmission attempt ${attempt + 1} failed", e)
            }
            
            attempt++
            if (attempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Retrying transmission in ${delay}ms (attempt $attempt/$MAX_RETRY_ATTEMPTS)")
                delay(delay)
                
                // Exponential backoff with jitter
                delay = min(delay * 2, MAX_RETRY_DELAY_MS)
            }
        }
        
        Log.e(TAG, "Failed to transmit after $MAX_RETRY_ATTEMPTS attempts")
        return false
    }
    
    /**
     * Monitor connection status with the smartphone
     */
    private fun monitorConnectionStatus() {
        transmissionScope.launch {
            while (isActive) {
                try {
                    val connected = communicationClient.isConnected()
                    _isConnected.value = connected
                    
                    if (!connected) {
                        Log.w(TAG, "Connection to smartphone lost")
                    }
                    
                    delay(5000) // Check every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking connection status", e)
                    _isConnected.value = false
                    delay(5000)
                }
            }
        }
    }
    
    /**
     * Update buffer status for monitoring
     */
    private fun updateBufferStatus() {
        val inertialSize = inertialBufferSize.get()
        val biometricSize = biometricBufferSize.get()
        val totalSize = inertialSize + biometricSize
        
        _bufferStatus.value = BufferStatus(
            inertialBufferSize = inertialSize,
            biometricBufferSize = biometricSize,
            totalBufferSize = totalSize,
            maxBufferSize = MAX_BUFFER_SIZE * 2, // Total for both buffers
            isNearCapacity = totalSize >= (BUFFER_WARNING_THRESHOLD * 2),
            utilizationPercentage = (totalSize.toFloat() / (MAX_BUFFER_SIZE * 2) * 100).toInt()
        )
    }
    
    /**
     * Clear all buffers
     */
    private fun clearBuffers() {
        inertialBuffer.clear()
        biometricBuffer.clear()
        inertialBufferSize.set(0)
        biometricBufferSize.set(0)
        updateBufferStatus()
        Log.d(TAG, "All buffers cleared")
    }
    
    /**
     * Force immediate transmission of buffered data
     */
    suspend fun forceTransmission() {
        Log.d(TAG, "Forcing immediate transmission")
        if (_isConnected.value) {
            transmitBufferedData()
        } else {
            Log.w(TAG, "Cannot force transmission - not connected")
        }
    }
    
    /**
     * Get current buffer statistics
     */
    fun getBufferStatistics(): BufferStatistics {
        return BufferStatistics(
            inertialBufferSize = inertialBufferSize.get(),
            biometricBufferSize = biometricBufferSize.get(),
            maxBufferSize = MAX_BUFFER_SIZE,
            isTransmitting = isTransmitting.get(),
            isConnected = _isConnected.value
        )
    }
}

/**
 * Represents the current status of data buffers
 */
data class BufferStatus(
    val inertialBufferSize: Int = 0,
    val biometricBufferSize: Int = 0,
    val totalBufferSize: Int = 0,
    val maxBufferSize: Int = 0,
    val isNearCapacity: Boolean = false,
    val utilizationPercentage: Int = 0
)

/**
 * Detailed buffer statistics for monitoring
 */
data class BufferStatistics(
    val inertialBufferSize: Int,
    val biometricBufferSize: Int,
    val maxBufferSize: Int,
    val isTransmitting: Boolean,
    val isConnected: Boolean
) {
    val totalBufferSize: Int = inertialBufferSize + biometricBufferSize
    val utilizationPercentage: Int = (totalBufferSize.toFloat() / (maxBufferSize * 2) * 100).toInt()
    val isNearCapacity: Boolean = utilizationPercentage >= 80
}