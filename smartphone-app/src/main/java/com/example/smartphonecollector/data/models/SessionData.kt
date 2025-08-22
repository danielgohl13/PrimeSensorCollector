package com.example.smartphonecollector.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a data collection session with metadata
 */
@Serializable
data class SessionData(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long?,
    val isActive: Boolean,
    val dataPointsCollected: Int,
    val deviceId: String
) {
    companion object {
        /**
         * Create a new session with a unique ID
         */
        fun createNew(deviceId: String): SessionData {
            val sessionId = "session_${UUID.randomUUID().toString().take(8)}"
            return SessionData(
                sessionId = sessionId,
                startTime = System.currentTimeMillis(),
                endTime = null,
                isActive = true,
                dataPointsCollected = 0,
                deviceId = deviceId
            )
        }
        
        /**
         * Create a new session with a provided session ID
         */
        fun createWithId(sessionId: String, deviceId: String): SessionData {
            return SessionData(
                sessionId = sessionId,
                startTime = System.currentTimeMillis(),
                endTime = null,
                isActive = true,
                dataPointsCollected = 0,
                deviceId = deviceId
            )
        }
    }
    
    /**
     * Mark the session as completed
     */
    fun complete(): SessionData {
        return copy(
            endTime = System.currentTimeMillis(),
            isActive = false
        )
    }
    
    /**
     * Update the data points count
     */
    fun updateDataPoints(count: Int): SessionData {
        return copy(dataPointsCollected = count)
    }
    
    /**
     * Get session duration in milliseconds
     */
    fun getDuration(): Long {
        return (endTime ?: System.currentTimeMillis()) - startTime
    }
}