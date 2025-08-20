package com.example.smartphonecollector.data.serialization

import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.data.models.Vector3D
import com.google.android.gms.wearable.DataMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Utility class for serializing and deserializing data models for transmission
 * between smartphone and wearable devices
 */
object DataSerializer {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // InertialReading serialization
    fun serializeInertialReading(reading: InertialReading): String {
        return json.encodeToString(reading)
    }
    
    fun deserializeInertialReading(data: String): InertialReading {
        return json.decodeFromString(data)
    }
    
    fun serializeInertialReadings(readings: List<InertialReading>): String {
        return json.encodeToString(readings)
    }
    
    fun deserializeInertialReadings(data: String): List<InertialReading> {
        return json.decodeFromString(data)
    }
    
    // BiometricReading serialization
    fun serializeBiometricReading(reading: BiometricReading): String {
        return json.encodeToString(reading)
    }
    
    fun deserializeBiometricReading(data: String): BiometricReading {
        return json.decodeFromString(data)
    }
    
    fun serializeBiometricReadings(readings: List<BiometricReading>): String {
        return json.encodeToString(readings)
    }
    
    fun deserializeBiometricReadings(data: String): List<BiometricReading> {
        return json.decodeFromString(data)
    }
    
    // SessionData serialization
    fun serializeSessionData(session: SessionData): String {
        return json.encodeToString(session)
    }
    
    fun deserializeSessionData(data: String): SessionData {
        return json.decodeFromString(data)
    }
    
    // Generic byte array conversion for Wearable API
    fun serializeToByteArray(data: String): ByteArray {
        return data.toByteArray(Charsets.UTF_8)
    }
    
    fun deserializeFromByteArray(bytes: ByteArray): String {
        return String(bytes, Charsets.UTF_8)
    }
    
    // Byte array serialization for Wearable API messages
    fun deserializeInertialReading(bytes: ByteArray): InertialReading {
        val jsonString = String(bytes, Charsets.UTF_8)
        return json.decodeFromString(jsonString)
    }
    
    fun deserializeBiometricReading(bytes: ByteArray): BiometricReading {
        val jsonString = String(bytes, Charsets.UTF_8)
        return json.decodeFromString(jsonString)
    }
    
    fun serializeInertialReadingToBytes(reading: InertialReading): ByteArray {
        val jsonString = json.encodeToString(reading)
        return jsonString.toByteArray(Charsets.UTF_8)
    }
    
    fun serializeBiometricReadingToBytes(reading: BiometricReading): ByteArray {
        val jsonString = json.encodeToString(reading)
        return jsonString.toByteArray(Charsets.UTF_8)
    }
    
    // DataMap serialization for Wearable API data items
    fun deserializeInertialReadingFromDataMap(dataMap: DataMap): InertialReading {
        return InertialReading(
            timestamp = dataMap.getLong("timestamp"),
            sessionId = dataMap.getString("sessionId") ?: "",
            deviceId = dataMap.getString("deviceId") ?: "",
            accelerometer = Vector3D(
                x = dataMap.getFloat("accel_x"),
                y = dataMap.getFloat("accel_y"),
                z = dataMap.getFloat("accel_z")
            ),
            gyroscope = Vector3D(
                x = dataMap.getFloat("gyro_x"),
                y = dataMap.getFloat("gyro_y"),
                z = dataMap.getFloat("gyro_z")
            ),
            magnetometer = Vector3D(
                x = dataMap.getFloat("mag_x"),
                y = dataMap.getFloat("mag_y"),
                z = dataMap.getFloat("mag_z")
            ),
            batteryLevel = dataMap.getInt("batteryLevel")
        )
    }
    
    fun deserializeBiometricReadingFromDataMap(dataMap: DataMap): BiometricReading {
        return BiometricReading(
            timestamp = dataMap.getLong("timestamp"),
            sessionId = dataMap.getString("sessionId") ?: "",
            deviceId = dataMap.getString("deviceId") ?: "",
            heartRate = if (dataMap.containsKey("heartRate")) dataMap.getInt("heartRate") else null,
            stepCount = dataMap.getInt("stepCount"),
            calories = dataMap.getFloat("calories"),
            skinTemperature = if (dataMap.containsKey("skinTemperature")) dataMap.getFloat("skinTemperature") else null,
            batteryLevel = dataMap.getInt("batteryLevel")
        )
    }
    
    fun serializeInertialReadingToDataMap(reading: InertialReading): DataMap {
        return DataMap().apply {
            putLong("timestamp", reading.timestamp)
            putString("sessionId", reading.sessionId)
            putString("deviceId", reading.deviceId)
            putFloat("accel_x", reading.accelerometer.x)
            putFloat("accel_y", reading.accelerometer.y)
            putFloat("accel_z", reading.accelerometer.z)
            putFloat("gyro_x", reading.gyroscope.x)
            putFloat("gyro_y", reading.gyroscope.y)
            putFloat("gyro_z", reading.gyroscope.z)
            putFloat("mag_x", reading.magnetometer.x)
            putFloat("mag_y", reading.magnetometer.y)
            putFloat("mag_z", reading.magnetometer.z)
            putInt("batteryLevel", reading.batteryLevel)
        }
    }
    
    fun serializeBiometricReadingToDataMap(reading: BiometricReading): DataMap {
        return DataMap().apply {
            putLong("timestamp", reading.timestamp)
            putString("sessionId", reading.sessionId)
            putString("deviceId", reading.deviceId)
            reading.heartRate?.let { putInt("heartRate", it) }
            putInt("stepCount", reading.stepCount)
            putFloat("calories", reading.calories)
            reading.skinTemperature?.let { putFloat("skinTemperature", it) }
            putInt("batteryLevel", reading.batteryLevel)
        }
    }
}