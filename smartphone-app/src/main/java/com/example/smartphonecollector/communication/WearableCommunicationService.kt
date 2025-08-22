package com.example.smartphonecollector.communication

import android.content.Context
import android.util.Log
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.serialization.DataSerializer
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Service for managing communication with the wearable device
 * Handles sending commands and receiving sensor data using Google Play Services Wearable API
 */
class WearableCommunicationService(
    private val context: Context,
    private val onInertialDataReceived: (InertialReading) -> Unit,
    private val onBiometricDataReceived: (BiometricReading) -> Unit
) : MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "WearableCommunicationService"
        
        // Message paths for communication
        private const val START_COLLECTION_PATH = "/start_collection"
        private const val STOP_COLLECTION_PATH = "/stop_collection"
        private const val INERTIAL_DATA_PATH = "/inertial_data"
        private const val BIOMETRIC_DATA_PATH = "/biometric_data"
        private const val STATUS_REQUEST_PATH = "/status_request"
        
        // Node capability for finding wearable devices
        private const val WEARABLE_CAPABILITY = "wearable_data_collector"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Wearable API clients
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    
    // Connection status
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    // Connected nodes
    private var connectedNodes: Set<Node> = emptySet()
    
    init {
        // Register listeners
        messageClient.addListener(this)
        dataClient.addListener(this)
        
        // Start monitoring connection status
        startConnectionMonitoring()
    }

    /**
     * Start data collection on the wearable device
     */
    suspend fun startCollection(sessionId: String): Boolean {
        return try {
            Log.d(TAG, "Starting collection with session ID: $sessionId")
            _connectionStatus.value = ConnectionStatus.CONNECTING
            
            val message = sessionId.toByteArray()
            val success = sendMessageToWearable(START_COLLECTION_PATH, message)
            
            if (success) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                Log.d(TAG, "Collection started successfully")
            } else {
                _connectionStatus.value = ConnectionStatus.ERROR
                Log.e(TAG, "Failed to start collection")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error starting collection", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            false
        }
    }

    /**
     * Stop data collection on the wearable device
     */
    suspend fun stopCollection(): Boolean {
        return try {
            Log.d(TAG, "Stopping collection")
            val success = sendMessageToWearable(STOP_COLLECTION_PATH, byteArrayOf())
            
            if (success) {
                Log.d(TAG, "Collection stopped successfully")
            } else {
                Log.e(TAG, "Failed to stop collection")
                _connectionStatus.value = ConnectionStatus.ERROR
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping collection", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            false
        }
    }

    /**
     * Check connection status with wearable device
     */
    suspend fun checkConnectionStatus(): Boolean {
        return try {
            val nodes = getConnectedNodes()
            val isConnected = nodes.isNotEmpty()
            
            _connectionStatus.value = if (isConnected) {
                ConnectionStatus.CONNECTED
            } else {
                ConnectionStatus.DISCONNECTED
            }
            
            Log.d(TAG, "Connection status checked: $isConnected")
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection status", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            false
        }
    }

    /**
     * Send a message to all connected wearable devices
     */
    private suspend fun sendMessageToWearable(path: String, data: ByteArray): Boolean {
        return try {
            val nodes = getConnectedNodes()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found")
                return false
            }

            var success = true
            for (node in nodes) {
                try {
                    val task = messageClient.sendMessage(node.id, path, data)
                    // Simple blocking wait for the task to complete
                    while (!task.isComplete) {
                        Thread.sleep(10)
                    }
                    if (!task.isSuccessful) {
                        throw task.exception ?: Exception("Message send failed")
                    }
                    Log.d(TAG, "Message sent to node: ${node.displayName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send message to node: ${node.displayName}", e)
                    success = false
                }
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to wearable", e)
            false
        }
    }

    /**
     * Get all connected nodes with the wearable capability
     */
    private suspend fun getConnectedNodes(): Set<Node> {
        return try {
            val task = capabilityClient
                .getCapability(WEARABLE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            
            // Simple blocking wait for the task to complete
            while (!task.isComplete) {
                Thread.sleep(10)
            }
            
            if (!task.isSuccessful) {
                throw task.exception ?: Exception("Failed to get capability")
            }
            
            val capabilityInfo = task.result
            
            connectedNodes = capabilityInfo.nodes
            Log.d(TAG, "Found ${connectedNodes.size} connected nodes")
            
            connectedNodes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connected nodes", e)
            emptySet()
        }
    }

    /**
     * Start monitoring connection status with automatic reconnection
     * Requirements: 8.2, 8.3, 8.4 - Connection loss handling with automatic reconnection
     */
    private fun startConnectionMonitoring() {
        serviceScope.launch {
            var consecutiveFailures = 0
            val maxConsecutiveFailures = 6 // 30 seconds of failures before error state
            
            while (true) {
                try {
                    val wasConnected = _connectionStatus.value == ConnectionStatus.CONNECTED
                    val isConnected = checkConnectionStatus()
                    
                    if (isConnected) {
                        consecutiveFailures = 0
                        if (!wasConnected) {
                            Log.i(TAG, "Connection restored to wearable device")
                        }
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= maxConsecutiveFailures) {
                            _connectionStatus.value = ConnectionStatus.ERROR
                            Log.e(TAG, "Connection lost for ${consecutiveFailures * 5} seconds - entering error state")
                        } else if (wasConnected) {
                            _connectionStatus.value = ConnectionStatus.CONNECTING
                            Log.w(TAG, "Connection lost, attempting to reconnect... (attempt $consecutiveFailures/$maxConsecutiveFailures)")
                        }
                    }
                    
                    kotlinx.coroutines.delay(5000) // Check every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in connection monitoring", e)
                    consecutiveFailures++
                    _connectionStatus.value = ConnectionStatus.ERROR
                    kotlinx.coroutines.delay(10000) // Wait longer on error
                }
            }
        }
    }

    /**
     * Handle incoming messages from wearable device
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
        
        when (messageEvent.path) {
            INERTIAL_DATA_PATH -> {
                handleInertialData(messageEvent.data)
            }
            BIOMETRIC_DATA_PATH -> {
                handleBiometricData(messageEvent.data)
            }
            else -> {
                Log.w(TAG, "Unknown message path: ${messageEvent.path}")
            }
        }
    }

    /**
     * Handle incoming data changes from wearable device
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                Log.d(TAG, "Data changed: ${dataItem.uri.path}")
                
                when (dataItem.uri.path) {
                    INERTIAL_DATA_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        handleInertialDataMap(dataMap)
                    }
                    BIOMETRIC_DATA_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        handleBiometricDataMap(dataMap)
                    }
                }
            }
        }
    }

    /**
     * Handle inertial data received via message
     */
    private fun handleInertialData(data: ByteArray) {
        try {
            val inertialReading = DataSerializer.deserializeInertialReading(data)
            Log.d(TAG, "Inertial data received: ${inertialReading.timestamp}")
            onInertialDataReceived(inertialReading)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing inertial data", e)
        }
    }

    /**
     * Handle biometric data received via message
     */
    private fun handleBiometricData(data: ByteArray) {
        try {
            val biometricReading = DataSerializer.deserializeBiometricReading(data)
            Log.d(TAG, "Biometric data received: ${biometricReading.timestamp}")
            onBiometricDataReceived(biometricReading)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing biometric data", e)
        }
    }

    /**
     * Handle inertial data received via DataMap
     */
    private fun handleInertialDataMap(dataMap: DataMap) {
        try {
            val inertialReading = DataSerializer.deserializeInertialReadingFromDataMap(dataMap)
            Log.d(TAG, "Inertial data received via DataMap: ${inertialReading.timestamp}")
            onInertialDataReceived(inertialReading)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing inertial data from DataMap", e)
        }
    }

    /**
     * Handle biometric data received via DataMap
     */
    private fun handleBiometricDataMap(dataMap: DataMap) {
        try {
            val biometricReading = DataSerializer.deserializeBiometricReadingFromDataMap(dataMap)
            Log.d(TAG, "Biometric data received via DataMap: ${biometricReading.timestamp}")
            onBiometricDataReceived(biometricReading)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing biometric data from DataMap", e)
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        messageClient.removeListener(this)
        dataClient.removeListener(this)
        Log.d(TAG, "WearableCommunicationService cleaned up")
    }
}