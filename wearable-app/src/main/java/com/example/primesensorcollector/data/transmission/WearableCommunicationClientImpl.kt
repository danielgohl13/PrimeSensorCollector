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
            
            // Find connected nodes
            updateConnectedNodes()
            
            Log.d(TAG, "WearableCommunicationClient initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WearableCommunicationClient", e)
            false
        }
    }
    
    /**
     * Send a message to the connected smartphone
     */
    override suspend fun sendMessage(messageType: String, data: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodeId = connectedNodeId ?: run {
                updateConnectedNodes()
                connectedNodeId
            }
            
            if (nodeId == null) {
                Log.w(TAG, "No connected node found for message transmission")
                return@withContext false
            }
            
            val messageData = createMessageData(messageType, data)
            val result = messageClient.sendMessage(nodeId, DATA_MESSAGE_PATH, messageData).await()
            
            Log.d(TAG, "Message sent successfully: $messageType (${data.length} chars)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: $messageType", e)
            false
        }
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
     * Update the list of connected nodes
     */
    private suspend fun updateConnectedNodes() {
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
            Log.e(TAG, "Error updating connected nodes", e)
            connectedNodeId = null
        }
    }
    
    /**
     * Create message data with type and payload
     */
    private fun createMessageData(messageType: String, data: String): ByteArray {
        val message = "$messageType|$data"
        return message.toByteArray(Charsets.UTF_8)
    }
}