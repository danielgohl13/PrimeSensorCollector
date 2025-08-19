package com.example.smartphonecollector.data.models

import kotlinx.serialization.Serializable

/**
 * Represents biometric sensor readings from the wearable device
 * Contains heart rate, step count, calories, and optional skin temperature
 */
@Serializable
data class BiometricReading(
    val timestamp: Long,
    val sessionId: String,
    val deviceId: String,
    val heartRate: Int?,
    val stepCount: Int,
    val calories: Float,
    val skinTemperature: Float?,
    val batteryLevel: Int
) {
    companion object {
        /**
         * Create a CSV header for biometric data
         */
        fun csvHeader(): String {
            return "timestamp,session_id,device_id,heart_rate,step_count,calories,skin_temp,battery_level"
        }
    }
    
    /**
     * Convert this reading to CSV format
     */
    fun toCsvRow(): String {
        return "$timestamp,$sessionId,$deviceId," +
                "${heartRate ?: ""},$stepCount,$calories," +
                "${skinTemperature ?: ""},$batteryLevel"
    }
}