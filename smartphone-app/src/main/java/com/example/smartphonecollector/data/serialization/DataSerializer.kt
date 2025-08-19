package com.example.smartphonecollector.data.serialization

import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.SessionData
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
}