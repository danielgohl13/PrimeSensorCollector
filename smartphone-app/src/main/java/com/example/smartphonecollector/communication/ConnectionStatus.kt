package com.example.smartphonecollector.communication

/**
 * Represents the connection status between smartphone and wearable device
 */
enum class ConnectionStatus {
    /**
     * No connection to wearable device
     */
    DISCONNECTED,
    
    /**
     * Attempting to connect to wearable device
     */
    CONNECTING,
    
    /**
     * Successfully connected to wearable device
     */
    CONNECTED,
    
    /**
     * Connection error occurred
     */
    ERROR
}