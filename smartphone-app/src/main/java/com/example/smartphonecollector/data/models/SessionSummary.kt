package com.example.smartphonecollector.data.models

import kotlinx.serialization.Serializable

/**
 * Summary information about a completed data collection session
 */
@Serializable
data class SessionSummary(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val dataPointsCollected: Int,
    val deviceId: String,
    val inertialFilePath: String?,
    val biometricFilePath: String?
) {
    companion object {
        /**
         * Create a session summary from session data and file paths
         */
        fun fromSessionData(
            sessionData: SessionData,
            inertialFilePath: String? = null,
            biometricFilePath: String? = null
        ): SessionSummary {
            return SessionSummary(
                sessionId = sessionData.sessionId,
                startTime = sessionData.startTime,
                endTime = sessionData.endTime ?: System.currentTimeMillis(),
                duration = sessionData.getDuration(),
                dataPointsCollected = sessionData.dataPointsCollected,
                deviceId = sessionData.deviceId,
                inertialFilePath = inertialFilePath,
                biometricFilePath = biometricFilePath
            )
        }
    }
    
    /**
     * Get formatted duration string
     */
    fun getFormattedDuration(): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}