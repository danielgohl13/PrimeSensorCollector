package com.example.primesensorcollector.data.models

import kotlinx.serialization.Serializable

/**
 * Represents a complete inertial sensor reading from the wearable device
 * Contains accelerometer, gyroscope, and magnetometer data with metadata
 */
@Serializable
data class InertialReading(
    val timestamp: Long,
    val sessionId: String,
    val deviceId: String,
    val accelerometer: Vector3D,
    val gyroscope: Vector3D,
    val magnetometer: Vector3D,
    val batteryLevel: Int
) {
    companion object {
        /**
         * Create a CSV header for inertial data
         */
        fun csvHeader(): String {
            return "timestamp,session_id,device_id,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,battery_level"
        }
    }
    
    /**
     * Convert this reading to CSV format
     */
    fun toCsvRow(): String {
        return "$timestamp,$sessionId,$deviceId," +
                "${accelerometer.x},${accelerometer.y},${accelerometer.z}," +
                "${gyroscope.x},${gyroscope.y},${gyroscope.z}," +
                "${magnetometer.x},${magnetometer.y},${magnetometer.z}," +
                "$batteryLevel"
    }
}