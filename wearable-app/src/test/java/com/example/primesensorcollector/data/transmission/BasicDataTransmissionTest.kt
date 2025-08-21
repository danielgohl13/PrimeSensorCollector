package com.example.primesensorcollector.data.transmission

import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.Vector3D
import org.junit.Test
import org.junit.Assert.*

class BasicDataTransmissionTest {
    
    @Test
    fun `test data transmission manager creation`() {
        // Given
        val mockClient = object : WearableCommunicationClient {
            override suspend fun sendMessage(messageType: String, data: String): Boolean = true
            override suspend fun isConnected(): Boolean = true
            override fun setCommandListener(onCommandReceived: (command: String, data: String?) -> Unit) {}
            override suspend fun reportStatus(status: String): Boolean = true
            override suspend fun initialize(): Boolean = true
            override fun cleanup() {}
        }
        
        // When
        val manager = DataTransmissionManager(mockClient)
        
        // Then
        assertNotNull("Manager should be created", manager)
        
        // Test basic buffer functionality
        val reading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test",
            deviceId = "test",
            accelerometer = Vector3D(1f, 2f, 3f),
            gyroscope = Vector3D(0.1f, 0.2f, 0.3f),
            magnetometer = Vector3D(10f, 20f, 30f),
            batteryLevel = 85
        )
        
        manager.bufferInertialReading(reading)
        val stats = manager.getBufferStatistics()
        assertEquals("Should have 1 inertial reading", 1, stats.inertialBufferSize)
    }
    
    @Test
    fun `test buffer status creation`() {
        // Test that BufferStatus data class works correctly
        val status = BufferStatus(
            inertialBufferSize = 5,
            biometricBufferSize = 3,
            totalBufferSize = 8,
            maxBufferSize = 100,
            isNearCapacity = false,
            utilizationPercentage = 8
        )
        
        assertEquals(5, status.inertialBufferSize)
        assertEquals(3, status.biometricBufferSize)
        assertEquals(8, status.totalBufferSize)
        assertFalse(status.isNearCapacity)
    }
    
    @Test
    fun `test buffer statistics creation`() {
        // Test that BufferStatistics data class works correctly
        val stats = BufferStatistics(
            inertialBufferSize = 10,
            biometricBufferSize = 5,
            maxBufferSize = 100,
            isTransmitting = false,
            isConnected = true
        )
        
        assertEquals(10, stats.inertialBufferSize)
        assertEquals(5, stats.biometricBufferSize)
        assertEquals(15, stats.totalBufferSize)
        assertTrue(stats.isConnected)
        assertFalse(stats.isTransmitting)
    }
}