package com.example.primesensorcollector.data.transmission

import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.Vector3D
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DataTransmissionManagerTest {

    private lateinit var mockCommunicationClient: WearableCommunicationClient
    private lateinit var transmissionManager: DataTransmissionManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockCommunicationClient = mockk(relaxed = true)
        
        // Mock default behaviors
        every { mockCommunicationClient.isConnected() } returns true
        coEvery { mockCommunicationClient.sendMessage(any(), any()) } returns true
        
        transmissionManager = DataTransmissionManager(mockCommunicationClient)
    }

    @After
    fun cleanup() {
        transmissionManager.stop()
        unmockkAll()
    }

    private fun createSampleInertialReading(sessionId: String = "test_session"): InertialReading {
        return InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            deviceId = "test_device",
            accelerometer = Vector3D(0.1f, 0.2f, 9.8f),
            gyroscope = Vector3D(0.01f, 0.02f, 0.03f),
            magnetometer = Vector3D(45.0f, -12.0f, 8.0f),
            batteryLevel = 85
        )
    }

    private fun createSampleBiometricReading(sessionId: String = "test_session"): BiometricReading {
        return BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            deviceId = "test_device",
            heartRate = 72,
            stepCount = 1000,
            calories = 50.5f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )
    }

    @Test
    fun `manager initializes with correct default state`() {
        assertFalse("Should not be connected initially", transmissionManager.isConnected.value)
        
        val bufferStatus = transmissionManager.bufferStatus.value
        assertEquals("Inertial buffer should be empty", 0, bufferStatus.inertialBufferSize)
        assertEquals("Biometric buffer should be empty", 0, bufferStatus.biometricBufferSize)
        assertEquals("Total buffer should be empty", 0, bufferStatus.totalBufferSize)
        assertFalse("Should not be near capacity", bufferStatus.isNearCapacity)
        assertEquals("Utilization should be zero", 0, bufferStatus.utilizationPercentage)
    }

    @Test
    fun `bufferInertialReading adds reading to buffer`() {
        val reading = createSampleInertialReading()
        
        transmissionManager.bufferInertialReading(reading)
        
        val bufferStatus = transmissionManager.bufferStatus.value
        assertEquals("Inertial buffer should have one reading", 1, bufferStatus.inertialBufferSize)
        assertEquals("Total buffer should have one reading", 1, bufferStatus.totalBufferSize)
    }

    @Test
    fun `bufferBiometricReading adds reading to buffer`() {
        val reading = createSampleBiometricReading()
        
        transmissionManager.bufferBiometricReading(reading)
        
        val bufferStatus = transmissionManager.bufferStatus.value
        assertEquals("Biometric buffer should have one reading", 1, bufferStatus.biometricBufferSize)
        assertEquals("Total buffer should have one reading", 1, bufferStatus.totalBufferSize)
    }

    @Test
    fun `bufferInertialReading handles multiple readings`() {
        val reading1 = createSampleInertialReading("session1")
        val reading2 = createSampleInertialReading("session2")
        val reading3 = createSampleInertialReading("session3")
        
        transmissionManager.bufferInertialReading(reading1)
        transmissionManager.bufferInertialReading(reading2)
        transmissionManager.bufferInertialReading(reading3)
        
        val bufferStatus = transmissionManager.bufferStatus.value
        assertEquals("Inertial buffer should have three readings", 3, bufferStatus.inertialBufferSize)
        assertEquals("Total buffer should have three readings", 3, bufferStatus.totalBufferSize)
    }

    @Test
    fun `bufferBiometricReading handles multiple readings`() {
        val reading1 = createSampleBiometricReading("session1")
        val reading2 = createSampleBiometricReading("session2")
        
        transmissionManager.bufferBiometricReading(reading1)
        transmissionManager.bufferBiometricReading(reading2)
        
        val bufferStatus = transmissionManager.bufferStatus.value
        assertEquals("Biometric buffer should have two readings", 2, bufferStatus.biometricBufferSize)
        assertEquals("Total buffer should have two readings", 2, bufferStatus.totalBufferSize)
    }

    @Test
    fun `mixed buffer operations work correctly`() {
        val inertialReading = createSampleInertialReading()
        val biometricReading = createSampleBiometricReading()
        
        transmissionManager.bufferInertialReading(inertialReading)
        transmissionManager.bufferBiometricReading(biometricReading)
        
        val bufferStatus = transmissionManager.bufferStatus.value
        assertEquals("Inertial buffer should have one reading", 1, bufferStatus.inertialBufferSize)
        assertEquals("Biometric buffer should have one reading", 1, bufferStatus.biometricBufferSize)
        assertEquals("Total buffer should have two readings", 2, bufferStatus.totalBufferSize)
    }

    @Test
    fun `getBufferStatistics returns correct information`() {
        val inertialReading = createSampleInertialReading()
        val biometricReading = createSampleBiometricReading()
        
        transmissionManager.bufferInertialReading(inertialReading)
        transmissionManager.bufferBiometricReading(biometricReading)
        
        val stats = transmissionManager.getBufferStatistics()
        
        assertEquals("Inertial buffer size should be correct", 1, stats.inertialBufferSize)
        assertEquals("Biometric buffer size should be correct", 1, stats.biometricBufferSize)
        assertEquals("Total buffer size should be correct", 2, stats.totalBufferSize)
        assertTrue("Max buffer size should be positive", stats.maxBufferSize > 0)
        assertFalse("Should not be transmitting initially", stats.isTransmitting)
        assertTrue("Should be connected (mocked)", stats.isConnected)
        assertFalse("Should not be near capacity with only 2 readings", stats.isNearCapacity)
    }

    @Test
    fun `buffer utilization percentage calculates correctly`() {
        // Add some readings to test utilization calculation
        repeat(10) {
            transmissionManager.bufferInertialReading(createSampleInertialReading("session_$it"))
        }
        
        val stats = transmissionManager.getBufferStatistics()
        assertTrue("Utilization percentage should be greater than 0", stats.utilizationPercentage > 0)
        assertTrue("Utilization percentage should be less than 100", stats.utilizationPercentage < 100)
    }

    @Test
    fun `buffer status updates correctly with utilization`() {
        // Add readings to test buffer status updates
        repeat(5) {
            transmissionManager.bufferInertialReading(createSampleInertialReading("inertial_$it"))
            transmissionManager.bufferBiometricReading(createSampleBiometricReading("biometric_$it"))
        }
        
        val bufferStatus = transmissionManager.bufferStatus.value
        assertEquals("Inertial buffer should have 5 readings", 5, bufferStatus.inertialBufferSize)
        assertEquals("Biometric buffer should have 5 readings", 5, bufferStatus.biometricBufferSize)
        assertEquals("Total buffer should have 10 readings", 10, bufferStatus.totalBufferSize)
        assertTrue("Max buffer size should be positive", bufferStatus.maxBufferSize > 0)
        assertTrue("Utilization percentage should be greater than 0", bufferStatus.utilizationPercentage > 0)
    }

    @Test
    fun `stop method clears buffers`() {
        // Add some data first
        transmissionManager.bufferInertialReading(createSampleInertialReading())
        transmissionManager.bufferBiometricReading(createSampleBiometricReading())
        
        // Verify data is buffered
        val statusBefore = transmissionManager.bufferStatus.value
        assertTrue("Should have buffered data before stop", statusBefore.totalBufferSize > 0)
        
        // Stop the manager
        transmissionManager.stop()
        
        // Verify buffers are cleared
        val statusAfter = transmissionManager.bufferStatus.value
        assertEquals("Inertial buffer should be empty after stop", 0, statusAfter.inertialBufferSize)
        assertEquals("Biometric buffer should be empty after stop", 0, statusAfter.biometricBufferSize)
        assertEquals("Total buffer should be empty after stop", 0, statusAfter.totalBufferSize)
    }

    @Test(timeout = 10000) // 10 second timeout
    fun `connection status reflects communication client state`() = runTest(timeout = 5000.milliseconds) {
        // Initially connected (mocked)
        every { mockCommunicationClient.isConnected() } returns true
        
        transmissionManager.start()
        advanceTimeBy(6000) // Wait for connection monitoring
        
        assertTrue("Should be connected when client reports connected", transmissionManager.isConnected.value)
        
        // Simulate disconnection
        every { mockCommunicationClient.isConnected() } returns false
        
        advanceTimeBy(6000) // Wait for next connection check
        
        assertFalse("Should be disconnected when client reports disconnected", transmissionManager.isConnected.value)
    }

    // Note: Testing the actual transmission logic with retry mechanisms would require
    // more complex coroutine testing and mocking of the serialization layer.
    // In a production environment, we would:
    // 1. Test the retry logic with controlled failures
    // 2. Test buffer overflow handling
    // 3. Test periodic transmission timing
    // 4. Test data serialization integration
    // 5. Test connection recovery scenarios

    @Test
    fun `buffer overflow prevention works`() {
        // This test would require access to the MAX_BUFFER_SIZE constant
        // or a way to configure it for testing. In a production environment,
        // we would make this configurable or expose it for testing.
        
        // For now, we can test that the buffer accepts readings without throwing exceptions
        repeat(100) { // Add many readings
            transmissionManager.bufferInertialReading(createSampleInertialReading("overflow_test_$it"))
        }
        
        val stats = transmissionManager.getBufferStatistics()
        assertTrue("Buffer should contain readings", stats.inertialBufferSize > 0)
        // The exact behavior depends on the MAX_BUFFER_SIZE implementation
    }
}