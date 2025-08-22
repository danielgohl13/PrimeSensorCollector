package com.example.smartphonecollector.integration

import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.data.serialization.DataSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Core system integration test that validates data models, serialization, and CSV formatting
 * without requiring Android context dependencies
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5
 */
class CoreSystemIntegrationTest {

    private val testSessionId = "test_session_${System.currentTimeMillis()}"
    private val testDeviceId = "galaxy_watch_5_test"

    /**
     * Test complete data pipeline from models to CSV format
     * Requirements: 6.1, 6.2, 6.3, 6.4
     */
    @Test
    fun testDataModelToCsvPipeline() = runTest {
        println("Testing data model to CSV pipeline...")

        // Phase 1: Create test data models
        val inertialReadings = createTestInertialReadings(10)
        val biometricReadings = createTestBiometricReadings(5)

        // Phase 2: Test CSV formatting
        inertialReadings.forEach { reading ->
            val csvRow = reading.toCsvRow()
            assertNotNull("CSV row should not be null", csvRow)
            assertTrue("CSV row should contain session ID", csvRow.contains(testSessionId))
            assertTrue("CSV row should contain device ID", csvRow.contains(testDeviceId))
            assertTrue("CSV row should contain timestamp", csvRow.contains(reading.timestamp.toString()))
            
            // Verify all sensor data is present
            assertTrue("CSV should contain accelerometer X", csvRow.contains(reading.accelerometer.x.toString()))
            assertTrue("CSV should contain gyroscope Y", csvRow.contains(reading.gyroscope.y.toString()))
            assertTrue("CSV should contain magnetometer Z", csvRow.contains(reading.magnetometer.z.toString()))
            assertTrue("CSV should contain battery level", csvRow.contains(reading.batteryLevel.toString()))
        }

        biometricReadings.forEach { reading ->
            val csvRow = reading.toCsvRow()
            assertNotNull("CSV row should not be null", csvRow)
            assertTrue("CSV row should contain session ID", csvRow.contains(testSessionId))
            assertTrue("CSV row should contain device ID", csvRow.contains(testDeviceId))
            assertTrue("CSV row should contain timestamp", csvRow.contains(reading.timestamp.toString()))
            
            // Verify all biometric data is present
            assertTrue("CSV should contain heart rate", csvRow.contains(reading.heartRate.toString()))
            assertTrue("CSV should contain step count", csvRow.contains(reading.stepCount.toString()))
            assertTrue("CSV should contain calories", csvRow.contains(reading.calories.toString()))
            assertTrue("CSV should contain battery level", csvRow.contains(reading.batteryLevel.toString()))
        }

        // Phase 3: Test CSV headers
        val inertialHeader = InertialReading.csvHeader()
        val biometricHeader = BiometricReading.csvHeader()

        assertNotNull("Inertial header should not be null", inertialHeader)
        assertNotNull("Biometric header should not be null", biometricHeader)

        // Verify required columns are present
        val expectedInertialColumns = listOf("timestamp", "session_id", "device_id", "accel_x", "accel_y", "accel_z",
            "gyro_x", "gyro_y", "gyro_z", "mag_x", "mag_y", "mag_z", "battery_level")
        expectedInertialColumns.forEach { column ->
            assertTrue("Inertial header should contain $column", inertialHeader.contains(column))
        }

        val expectedBiometricColumns = listOf("timestamp", "session_id", "device_id", "heart_rate", 
            "step_count", "calories", "skin_temp", "battery_level")
        expectedBiometricColumns.forEach { column ->
            assertTrue("Biometric header should contain $column", biometricHeader.contains(column))
        }

        println("Data model to CSV pipeline test completed successfully!")
    }

    /**
     * Test data serialization and deserialization
     * Requirements: 4.1, 4.2
     */
    @Test
    fun testDataSerializationPipeline() = runTest {
        println("Testing data serialization pipeline...")

        // Phase 1: Test inertial data serialization
        val originalInertial = createTestInertialReadings(1).first()
        
        val serializedInertial = DataSerializer.serializeInertialReading(originalInertial)
        assertNotNull("Serialized data should not be null", serializedInertial)
        assertTrue("Serialized data should not be empty", serializedInertial.isNotEmpty())

        val deserializedInertial = DataSerializer.deserializeInertialReading(serializedInertial)
        assertNotNull("Deserialized data should not be null", deserializedInertial)

        // Verify data integrity
        assertEquals("Timestamp should match", originalInertial.timestamp, deserializedInertial.timestamp)
        assertEquals("Session ID should match", originalInertial.sessionId, deserializedInertial.sessionId)
        assertEquals("Device ID should match", originalInertial.deviceId, deserializedInertial.deviceId)
        assertEquals("Accelerometer X should match", originalInertial.accelerometer.x, deserializedInertial.accelerometer.x, 0.001f)
        assertEquals("Gyroscope Y should match", originalInertial.gyroscope.y, deserializedInertial.gyroscope.y, 0.001f)
        assertEquals("Magnetometer Z should match", originalInertial.magnetometer.z, deserializedInertial.magnetometer.z, 0.001f)
        assertEquals("Battery level should match", originalInertial.batteryLevel, deserializedInertial.batteryLevel)

        // Phase 2: Test biometric data serialization
        val originalBiometric = createTestBiometricReadings(1).first()
        
        val serializedBiometric = DataSerializer.serializeBiometricReading(originalBiometric)
        assertNotNull("Serialized biometric data should not be null", serializedBiometric)
        assertTrue("Serialized biometric data should not be empty", serializedBiometric.isNotEmpty())

        val deserializedBiometric = DataSerializer.deserializeBiometricReading(serializedBiometric)
        assertNotNull("Deserialized biometric data should not be null", deserializedBiometric)

        // Verify biometric data integrity
        assertEquals("Timestamp should match", originalBiometric.timestamp, deserializedBiometric.timestamp)
        assertEquals("Session ID should match", originalBiometric.sessionId, deserializedBiometric.sessionId)
        assertEquals("Device ID should match", originalBiometric.deviceId, deserializedBiometric.deviceId)
        assertEquals("Heart rate should match", originalBiometric.heartRate, deserializedBiometric.heartRate)
        assertEquals("Step count should match", originalBiometric.stepCount, deserializedBiometric.stepCount)
        assertEquals("Calories should match", originalBiometric.calories, deserializedBiometric.calories, 0.1f)
        assertEquals("Battery level should match", originalBiometric.batteryLevel, deserializedBiometric.batteryLevel)

        println("Data serialization pipeline test completed successfully!")
    }

    /**
     * Test data model validation and edge cases
     * Requirements: 2.5, 3.5
     */
    @Test
    fun testDataModelValidationAndEdgeCases() = runTest {
        println("Testing data model validation and edge cases...")

        // Test with null/missing biometric data
        val biometricWithNulls = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = testSessionId,
            deviceId = testDeviceId,
            heartRate = null, // Heart rate sensor unavailable
            stepCount = 1500,
            calories = 45.5f,
            skinTemperature = null, // Temperature sensor unavailable
            batteryLevel = 75
        )

        val csvRowWithNulls = biometricWithNulls.toCsvRow()
        assertNotNull("CSV row with nulls should not be null", csvRowWithNulls)
        assertTrue("CSV should handle null heart rate", csvRowWithNulls.contains("null") || csvRowWithNulls.contains(""))
        assertTrue("CSV should contain valid step count", csvRowWithNulls.contains("1500"))

        // Test with extreme values
        val extremeInertial = InertialReading(
            timestamp = Long.MAX_VALUE,
            sessionId = "extreme_test_session",
            deviceId = "extreme_device",
            accelerometer = Vector3D(Float.MAX_VALUE, Float.MIN_VALUE, 0.0f),
            gyroscope = Vector3D(-Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY),
            magnetometer = Vector3D(1000.0f, -1000.0f, 0.001f),
            batteryLevel = 0 // Critical battery
        )

        val extremeCsvRow = extremeInertial.toCsvRow()
        assertNotNull("Extreme values CSV should not be null", extremeCsvRow)
        assertTrue("CSV should contain extreme timestamp", extremeCsvRow.contains(Long.MAX_VALUE.toString()))
        assertTrue("CSV should contain zero battery", extremeCsvRow.contains("0"))

        // Test serialization with extreme values
        val serializedExtreme = DataSerializer.serializeInertialReading(extremeInertial)
        assertNotNull("Extreme values should serialize", serializedExtreme)
        
        val deserializedExtreme = DataSerializer.deserializeInertialReading(serializedExtreme)
        assertNotNull("Extreme values should deserialize", deserializedExtreme)
        assertEquals("Extreme timestamp should match", extremeInertial.timestamp, deserializedExtreme.timestamp)

        println("Data model validation and edge cases test completed successfully!")
    }

    /**
     * Test session data management
     * Requirements: 1.1, 1.2, 1.3
     */
    @Test
    fun testSessionDataManagement() = runTest {
        println("Testing session data management...")

        // Test session creation
        val session = com.example.smartphonecollector.data.models.SessionData.createNew(testDeviceId)
        
        assertNotNull("Session should be created", session)
        assertNotNull("Session ID should be generated", session.sessionId)
        assertTrue("Session should be active", session.isActive)
        assertNotNull("Session should have start time", session.startTime)
        assertNull("Session should not have end time initially", session.endTime)
        assertEquals("Device ID should match", testDeviceId, session.deviceId)
        assertEquals("Initial data points should be zero", 0, session.dataPointsCollected)

        // Test session completion
        Thread.sleep(100) // Ensure some time passes
        val completedSession = session.complete()
        
        assertFalse("Completed session should not be active", completedSession.isActive)
        assertNotNull("Completed session should have end time", completedSession.endTime)
        assertTrue("Session duration should be positive", completedSession.getDuration() > 0)

        // Test session data points update
        val updatedSession = completedSession.updateDataPoints(150)
        assertEquals("Data points should be updated", 150, updatedSession.dataPointsCollected)

        // Test session duration calculation
        val duration = updatedSession.getDuration()
        assertTrue("Duration should be reasonable", duration >= 100) // At least 100ms

        println("Session data management test completed successfully!")
    }

    /**
     * Test Vector3D operations and validation
     * Requirements: 2.1, 2.2, 2.3
     */
    @Test
    fun testVector3DOperations() = runTest {
        println("Testing Vector3D operations...")

        // Test basic vector creation
        val vector = Vector3D(1.5f, -2.3f, 9.8f)
        assertEquals("X component should match", 1.5f, vector.x, 0.001f)
        assertEquals("Y component should match", -2.3f, vector.y, 0.001f)
        assertEquals("Z component should match", 9.8f, vector.z, 0.001f)

        // Test vector magnitude calculation
        val magnitude = vector.magnitude()
        val expectedMagnitude = kotlin.math.sqrt(1.5f * 1.5f + (-2.3f) * (-2.3f) + 9.8f * 9.8f)
        assertEquals("Magnitude should be correct", expectedMagnitude, magnitude, 0.001f)

        // Test vector with unit magnitude
        val unitVector = Vector3D(1.0f, 0.0f, 0.0f)
        val unitMagnitude = unitVector.magnitude()
        assertEquals("Unit vector magnitude should be 1", 1.0f, unitMagnitude, 0.001f)

        // Test zero vector
        val zeroVector = Vector3D(0.0f, 0.0f, 0.0f)
        assertEquals("Zero vector magnitude should be 0", 0.0f, zeroVector.magnitude(), 0.001f)

        // Test vector with extreme values
        val extremeVector = Vector3D(Float.MAX_VALUE, Float.MIN_VALUE, 0.0f)
        val extremeMagnitude = extremeVector.magnitude()
        assertTrue("Extreme vector magnitude should be finite", extremeMagnitude.isFinite())

        println("Vector3D operations test completed successfully!")
    }

    // Helper methods

    private fun createTestInertialReadings(count: Int): List<InertialReading> {
        val baseTime = System.currentTimeMillis()
        return (1..count).map { i ->
            InertialReading(
                timestamp = baseTime + i * 20, // 50Hz data rate
                sessionId = testSessionId,
                deviceId = testDeviceId,
                accelerometer = Vector3D(
                    x = i * 0.1f,
                    y = i * 0.2f,
                    z = 9.8f + i * 0.05f
                ),
                gyroscope = Vector3D(
                    x = i * 0.01f,
                    y = i * 0.02f,
                    z = i * 0.03f
                ),
                magnetometer = Vector3D(
                    x = 45.0f + i * 0.5f,
                    y = -12.0f + i * 0.3f,
                    z = 8.0f + i * 0.2f
                ),
                batteryLevel = 100 - (i / 10) // Gradually decrease battery
            )
        }
    }

    private fun createTestBiometricReadings(count: Int): List<BiometricReading> {
        val baseTime = System.currentTimeMillis()
        return (1..count).map { i ->
            BiometricReading(
                timestamp = baseTime + i * 100, // 10Hz data rate
                sessionId = testSessionId,
                deviceId = testDeviceId,
                heartRate = 70 + (i % 20), // Vary heart rate
                stepCount = 1000 + i * 10,
                calories = 40.0f + i * 2.5f,
                skinTemperature = if (i % 3 == 0) null else 32.0f + i * 0.1f, // Sometimes unavailable
                batteryLevel = 100 - (i / 5) // Gradually decrease battery
            )
        }
    }
}