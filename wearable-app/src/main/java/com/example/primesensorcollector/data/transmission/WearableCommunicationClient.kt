package com.example.primesensorcollector.data.transmission

/**
 * Interface for wearable communication client
 * Handles communication between wearable and smartphone apps
 */
interface WearableCommunicationClient {
    
    /**
     * Send a message to the connected smartphone
     * @param messageType Type identifier for the message
     * @param data Serialized data to send
     * @return true if message was sent successfully, false otherwise
     */
    suspend fun sendMessage(messageType: String, data: String): Boolean
    
    /**
     * Check if the wearable is connected to a smartphone
     * @return true if connected, false otherwise
     */
    suspend fun isConnected(): Boolean
    
    /**
     * Set up message listeners for incoming commands
     * @param onCommandReceived Callback for handling received commands
     */
    fun setCommandListener(onCommandReceived: (command: String, data: String?) -> Unit)
    
    /**
     * Send status update to smartphone
     * @param status Current collection status
     */
    suspend fun reportStatus(status: String): Boolean
    
    /**
     * Initialize the communication client
     */
    suspend fun initialize(): Boolean
    
    /**
     * Clean up resources
     */
    fun cleanup()
}