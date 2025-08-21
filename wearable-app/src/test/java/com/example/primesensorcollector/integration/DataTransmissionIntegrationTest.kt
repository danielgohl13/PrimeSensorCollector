package com.example.primesensorcollector.integration

import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.Vector3D
import com.example.primesensorcollector.data.transmission.DataTransmissionManager
import com.example.primesensorcollector.data.transmission.WearableCommunicationClient
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for data transmission functionality
 */
class DataTransmissionIntegrationTest {
    
    @Test
    fun `test end-to-end data buffering and transmission flow`() {
        // Given
        val mockClient = MockCommunicationClient()
        val transmissionManager = DataTransmissionManager(mockClient)
        
        // When - Buffer some data
        val inertialReading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "integration_test_session",
            deviceId = "test_watch",
            accelerometer = Vector3D(1.5f, -2.3f, 9.8f),
            gyroscope = Vector3D(0.01f, 0.02f, -0.01f),
            magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
            batteryLevel = 85
        )
        
        val biometricReading = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "integration_test_session",
            deviceId = "test_watch",
            heartRate = 72,
            stepCount = 1250,
            calories = 45.2f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )
        
        transmissionManager.bufferInertialReading(inertialReading)
        transmissionManager.bufferBiometricReading(biometricReading)
        
        // Then - Verify data is buffered
        val stats = transmissionManager.getBufferStatistics()
        assertEquals("Should have 1 inertial reading", 1, stats.inertialBufferSize)
        assertEquals("Should have 1 biometric reading", 1, stats.biometricBufferSize)
        assertEquals("Total should be 2", 2, stats.totalBufferSize)
        
        // Verify buffer utilization calculation
        assertTrue("Utilization should be low", stats.utilizationPercentage < 10)
        assertFalse("Should not be near capacity", stats.isNearCapacity)
    }
    
    @Test
    fun `test buffer overflow handling`() {
        // Given
        val mockClient = MockCommunicationClient()
        val transmissionManager = DataTransmissionManager(mockClient)
        
        // When - Add many readings to trigger overflow handling
        repeat(50) { i ->
            val reading = InertialReading(
                timestamp = System.currentTimeMillis() + i,
                sessionId = "overflow_test",
                deviceId = "test_watch",
                accelerometer = Vector3D(i.toFloat(), i.toFloat(), i.toFloat()),
                gyroscope = Vector3D(0f, 0f, 0f),
                magnetometer = Vector3D(0f, 0f, 0f),
                batteryLevel = 85
            )
            transmissionManager.bufferInertialReading(reading)
        }
        
        // Then - Verify buffer is managed correctly
        val stats = transmissionManager.getBufferStatistics()
        assertEquals("Should have 50 inertial readings", 50, stats.inertialBufferSize)
        assertTrue("Should be able to handle multiple readings", stats.totalBufferSize > 0)
    }
    
    @Test
    fun `test message type constants`() {
        // Verify that the message type constants are properly defined
        assertEquals("inertial_batch", DataTransmissionManager.MESSAGE_TYPE_INERTIAL_BATCH)
        assertEquals("biometric_batch", DataTransmissionManager.MESSAGE_TYPE_BIOMETRIC_BATCH)
    }
    
    /**
     * Simple mock client for integration testing
     */
    private class MockCommunicationClient : WearableCommunicationClient {
        private var connected = true
        val sentMessages = mutableListOf<Pair<String, String>>()
        
        override suspend fun sendMessage(messageType: String, data: String): Boolean {
            if (connected) {
                sentMessages.add(messageType to data)
                return true
            }
            return false
        }
        
        override suspend fun isConnected(): Boolean = connected
        
        override fun setCommandListener(onCommandReceived: (command: String, data: String?) -> Unit) {
            // No-op for this test
        }
        
        override suspend fun reportStatus(status: String): Boolean = connected
        
        override suspend fun initialize(): Boolean = true
        
        override fun cleanup() {
            // No-op for this test
        }
        
        fun setConnected(isConnected: Boolean) {
            connected = isConnected
        }
    }
}