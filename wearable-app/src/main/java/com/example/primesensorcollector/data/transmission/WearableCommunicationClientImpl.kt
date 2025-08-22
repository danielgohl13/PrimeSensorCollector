package com.example.primesensorcollector.data.transmission

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of WearableCommunicationClient using Google Play Services Wearable API
 * Handles communication between wearable and smartphone apps
 */
class WearableCommunicationClientImpl(
    private val context: Context
) : WearableCommunicationClient, MessageClient.OnMessageReceivedListener {
    
    companion object {
        private const val TAG = "WearableCommunicationClient"
        
        // Message paths for communication
        private const val DATA_MESSAGE_PATH = "/sensor_data"
        private const val STATUS_MESSAGE_PATH = "/status"
        private const val COMMAND_MESSAGE_PATH = "/commands"
        
        // Command types
        const val COMMAND_START_COLLECTION = "start_collection"
        const val COMMAND_STOP_COLLECTION = "stop_collection"
        
        // Status types
        const val STATUS_COLLECTING = "collecting"
        const val STATUS_IDLE = "idle"
        const val STATUS_ERROR = "error"
    }
    
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }
    
    private var commandListener: ((command: String, data: String?) -> Unit)? = null
    private var connectedNodeId: String? = null
    
    /**
     * Initialize the communication client
     */
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing WearableCommunicationClient")
            
            // Add message listener
            messageClient.addListener(this@WearableCommunicationClientImpl)
            
            // Find connected nodes - let exceptions propagate during initialization
            try {
                val nodes = nodeClient.connectedNodes.await()
                val firstNode = nodes.firstOrNull()
                connectedNodeId = firstNode?.id
                
                if (connectedNodeId != null) {
                    Log.d(TAG, "Connected to node: $connectedNodeId")
                } else {
                    Log.w(TAG, "No connected nodes found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating connected nodes during initialization", e)
                throw e // Re-throw during initialization
            }
            
            Log.d(TAG, "WearableCommunicationClient initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WearableCommunicationClient", e)
            false
        }
    }
    
    /**
     * Send a message to the connected smartphone with automatic reconnection
     * Requirements: 8.2, 8.3 - Connection loss handling with automatic reconnection
     */
    override suspend fun sendMessage(messageType: String, data: String): Boolean = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 3
        
        while (attempts < maxAttempts) {
            try {
                val nodeId = connectedNodeId ?: run {
                    Log.d(TAG, "No connected node, attempting to find one...")
                    updateConnectedNodes()
                    connectedNodeId
                }
                
                if (nodeId == null) {
                    Log.w(TAG, "No connected node found for message transmission (attempt ${attempts + 1}/$maxAttempts)")
                    attempts++
                    if (attempts < maxAttempts) {
                        kotlinx.coroutines.delay(1000) // Wait before retry
                    }
                    continue
                }
                
                val messageData = createMessageData(messageType, data)
                val result = messageClient.sendMessage(nodeId, DATA_MESSAGE_PATH, messageData).await()
                
                Log.d(TAG, "Message sent successfully: $messageType (${data.length} chars)")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message: $messageType (attempt ${attempts + 1}/$maxAttempts)", e)
                
                // Connection might be lost, clear cached node ID
                connectedNodeId = null
                
                attempts++
                if (attempts < maxAttempts) {
                    kotlinx.coroutines.delay(1000) // Wait before retry
                }
            }
        }
        
        Log.e(TAG, "Failed to send message after $maxAttempts attempts: $messageType")
        return@withContext false
    }
    
    /**
     * Check if the wearable is connected to a smartphone
     */
    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            updateConnectedNodes()
            connectedNodeId != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection status", e)
            false
        }
    }
    
    /**
     * Set up message listeners for incoming commands
     */
    override fun setCommandListener(onCommandReceived: (command: String, data: String?) -> Unit) {
        this.commandListener = onCommandReceived
        Log.d(TAG, "Command listener set")
    }
    
    /**
     * Send status update to smartphone
     */
    override suspend fun reportStatus(status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodeId = connectedNodeId ?: run {
                updateConnectedNodes()
                connectedNodeId
            }
            
            if (nodeId == null) {
                Log.w(TAG, "No connected node found for status report")
                return@withContext false
            }
            
            val statusData = status.toByteArray(Charsets.UTF_8)
            messageClient.sendMessage(nodeId, STATUS_MESSAGE_PATH, statusData).await()
            
            Log.d(TAG, "Status reported: $status")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report status: $status", e)
            false
        }
    }
    
    /**
     * Clean up resources
     */
    override fun cleanup() {
        try {
            messageClient.removeListener(this)
            Log.d(TAG, "WearableCommunicationClient cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * Handle incoming messages from smartphone
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
        
        when (messageEvent.path) {
            COMMAND_MESSAGE_PATH -> {
                handleCommandMessage(messageEvent)
            }
            else -> {
                Log.w(TAG, "Unknown message path: ${messageEvent.path}")
            }
        }
    }
    
    /**
     * Handle command messages from smartphone
     */
    private fun handleCommandMessage(messageEvent: MessageEvent) {
        try {
            val commandData = String(messageEvent.data, Charsets.UTF_8)
            val parts = commandData.split("|", limit = 2)
            val command = parts[0]
            val data = if (parts.size > 1) parts[1] else null
            
            Log.d(TAG, "Command received: $command")
            
            commandListener?.invoke(command, data)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command message", e)
        }
    }
    
    /**
     * Update the list of connected nodes with retry logic
     * Requirements: 8.2, 8.3 - Connection loss handling with automatic reconnection
     */
    private suspend fun updateConnectedNodes() {
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount < maxRetries) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val firstNode = nodes.firstOrNull()
                val previousNodeId = connectedNodeId
                connectedNodeId = firstNode?.id
                
                if (connectedNodeId != null) {
                    if (previousNodeId != connectedNodeId) {
                        Log.i(TAG, "Connected to node: $connectedNodeId")
                    }
                    return // Success, exit retry loop
                } else {
                    Log.w(TAG, "No connected nodes found (attempt ${retryCount + 1}/$maxRetries)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating connected nodes (attempt ${retryCount + 1}/$maxRetries)", e)
            }
            
            retryCount++
            if (retryCount < maxRetries) {
                kotlinx.coroutines.delay(2000) // Wait 2 seconds before retry
            }
        }
        
        // All retries failed
        if (connectedNodeId != null) {
            Log.w(TAG, "Lost connection to smartphone after $maxRetries attempts")
        }
        connectedNodeId = null
    }
    
    /**
     * Create message data with type and payload
     */
    private fun createMessageData(messageType: String, data: String): ByteArray {
        val message = "$messageType|$data"
        return message.toByteArray(Charsets.UTF_8)
    }
}