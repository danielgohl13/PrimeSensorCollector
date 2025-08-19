package com.example.smartphonecollector

import com.example.smartphonecollector.data.models.*
import com.example.smartphonecollector.data.serialization.DataSerializer
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for data models and serialization
 */
class DataModelTest {

    @Test
    fun vector3D_creation_isCorrect() {
        val vector = Vector3D(1.0f, 2.0f, 3.0f)
        assertEquals(1.0f, vector.x, 0.001f)
        assertEquals(2.0f, vector.y, 0.001f)
        assertEquals(3.0f, vector.z, 0.001f)
    }

    @Test
    fun vector3D_magnitude_isCorrect() {
        val vector = Vector3D(3.0f, 4.0f, 0.0f)
        assertEquals(5.0f, vector.magnitude(), 0.001f)
    }

    @Test
    fun inertialReading_serialization_isCorrect() {
        val reading = InertialReading(
            timestamp = 1640995200000L,
            sessionId = "session_001",
            deviceId = "watch_001",
            accelerometer = Vector3D(0.12f, -0.34f, 9.81f),
            gyroscope = Vector3D(0.01f, 0.02f, -0.01f),
            magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
            batteryLevel = 85
        )

        val serialized = DataSerializer.serializeInertialReading(reading)
        val deserialized = DataSerializer.deserializeInertialReading(serialized)

        assertEquals(reading, deserialized)
    }

    @Test
    fun biometricReading_serialization_isCorrect() {
        val reading = BiometricReading(
            timestamp = 1640995200000L,
            sessionId = "session_001",
            deviceId = "watch_001",
            heartRate = 72,
            stepCount = 1250,
            calories = 45.2f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )

        val serialized = DataSerializer.serializeBiometricReading(reading)
        val deserialized = DataSerializer.deserializeBiometricReading(serialized)

        assertEquals(reading, deserialized)
    }

    @Test
    fun sessionData_creation_isCorrect() {
        val session = SessionData.createNew("watch_001")
        
        assertTrue(session.sessionId.startsWith("session_"))
        assertEquals("watch_001", session.deviceId)
        assertTrue(session.isActive)
        assertEquals(0, session.dataPointsCollected)
        assertNull(session.endTime)
    }

    @Test
    fun sessionData_completion_isCorrect() {
        val session = SessionData.createNew("watch_001")
        Thread.sleep(10) // Small delay to ensure duration > 0
        val completedSession = session.complete()
        
        assertFalse(completedSession.isActive)
        assertNotNull(completedSession.endTime)
        assertTrue(completedSession.getDuration() >= 0)
    }

    @Test
    fun inertialReading_csvFormat_isCorrect() {
        val reading = InertialReading(
            timestamp = 1640995200000L,
            sessionId = "session_001",
            deviceId = "watch_001",
            accelerometer = Vector3D(0.12f, -0.34f, 9.81f),
            gyroscope = Vector3D(0.01f, 0.02f, -0.01f),
            magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
            batteryLevel = 85
        )

        val csvRow = reading.toCsvRow()
        val expectedRow = "1640995200000,session_001,watch_001,0.12,-0.34,9.81,0.01,0.02,-0.01,45.2,-12.3,8.7,85"
        
        assertEquals(expectedRow, csvRow)
    }

    @Test
    fun biometricReading_csvFormat_isCorrect() {
        val reading = BiometricReading(
            timestamp = 1640995200000L,
            sessionId = "session_001",
            deviceId = "watch_001",
            heartRate = 72,
            stepCount = 1250,
            calories = 45.2f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )

        val csvRow = reading.toCsvRow()
        val expectedRow = "1640995200000,session_001,watch_001,72,1250,45.2,32.1,85"
        
        assertEquals(expectedRow, csvRow)
    }
}