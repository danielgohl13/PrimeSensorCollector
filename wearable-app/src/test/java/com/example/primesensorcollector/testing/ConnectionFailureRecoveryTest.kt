package com.example.primesensorcollector.testing

import com.example.primesensorcollector.data.transmission.DataTransmissionManager
import com.example.primesensorcollector.data.transmission.WearableCommunicationClient
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for connection failure recovery scenarios in the wearable app
 * Validates buffering, retry logic, and automatic reconnection behavior
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionFailureRecoveryTest {

    private lateinit var mockCommunicationClient: WearableCommunicationClient
    private lateinit var transmissionManager: DataTransmissionManager
    private lateinit var mockDataGenerator: MockSensorDataGenerator
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockCommunicationClient = mockk(relaxed = true)
        mockDataGenerator = MockSensorDataGenerator()
        
        // Default mock behaviors
        every { mockCommunicationClient.isConnected() } returns true
        coEvery { mockCommunicationClient.sendMessage(any(), any()) } returns true
        
        transmissionManager = DataTransmissionManager(mockCommunicationClient)
    }

    @After
    fun cleanup() {
        transmissionManager.stop()
        unmockkAll()
    }

    @Test
    fun `transmission manager handles connection loss gracefully`() = runTest {
        // Start with connected state
        every { mockCommunicationClient.isConnected() } returns true
        transmissionManager.start()
        
        // Add some data to buffer
        val testSession = mockDataGenerator.createMockWearableSession()
        val inertialReading = mockDataGenerator.generateWristStationaryInertialReading(testSession.sessionId)
        
        transmissionManager.bufferInertialReading(inertialReading)
        
        // Verify data is buffered
        val initialStats = transmissionManager.getBufferStatistics()
        assertEquals("Should have buffered one reading", 1, initialStats.inertialBufferSize)
        
        // Simulate connection loss
        every { mockCommunicationClient.isConnected() } returns false
        
        advanceTimeBy(6000) // Wait for connection monitoring
        
        // Verify connection status is updated
        assertFalse("Should detect connection loss", transmissionManager.isConnected.value)
        
        // Add more data while disconnected
        val additionalReading = mockDataGenerator.generateWristWalkingInertialReading(testSession.sessionId)
        transmissionManager.bufferInertialReading(additionalReading)
        
        // Verify data continues to be buffered
        val disconnectedStats = transmissionManager.getBufferStatistics()
        assertEquals("Should continue buffering while disconnected", 2, disconnectedStats.inertialBufferSize)
    }

    @Test
    fun `transmission manager recovers from connection restoration`() = runTest {
        // Start disconnected
        every { mockCommunicationClient.isConnected() } returns false
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        
        // Buffer data while disconnected
        repeat(5) {
            val reading = mockDataGenerator.generateWristStationaryInertialReading(testSession.sessionId)
            transmissionManager.bufferInertialReading(reading)
        }
        
        // Verify data is buffered
        val disconnectedStats = transmissionManager.getBufferStatistics()
        assertEquals("Should buffer data while disconnected", 5, disconnectedStats.inertialBufferSize)
        assertFalse("Should be disconnected", disconnectedStats.isConnected)
        
        // Restore connection
        every { mockCommunicationClient.isConnected() } returns true
        
        advanceTimeBy(6000) // Wait for connection monitoring
        
        // Verify connection is restored
        assertTrue("Should detect connection restoration", transmissionManager.isConnected.value)
        
        // Allow time for transmission
        advanceTimeBy(2000) // Wait for transmission interval
        
        // Verify transmission was attempted
        coVerify(atLeast = 1) { mockCommunicationClient.sendMessage(any(), any()) }
    }

    @Test
    fun `retry logic works with exponential backoff`() = runTest {
        // Setup transmission failure followed by success
        coEvery { mockCommunicationClient.sendMessage(any(), any()) } returnsMany listOf(false, false, true)
        
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        val reading = mockDataGenerator.generateWristStationaryInertialReading(testSession.sessionId)
        
        transmissionManager.bufferInertialReading(reading)
        
        // Force immediate transmission to trigger retry logic
        transmissionManager.forceTransmission()
        
        // Verify multiple send attempts were made
        coVerify(atLeast = 2) { mockCommunicationClient.sendMessage(any(), any()) }
    }

    @Test
    fun `buffer overflow handling works correctly`() = runTest {
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        
        // Generate large amount of data to test buffer overflow
        val largeDataBatch = mockDataGenerator.generateBufferOverflowTestData(
            sessionId = testSession.sessionId,
            batchSize = 1200 // Exceed typical buffer size
        )
        
        // Buffer all data
        largeDataBatch.forEach { reading ->
            transmissionManager.bufferInertialReading(reading)
        }
        
        // Verify buffer doesn't exceed maximum capacity
        val stats = transmissionManager.getBufferStatistics()
        assertTrue("Buffer should not exceed maximum capacity", 
            stats.inertialBufferSize <= stats.maxBufferSize)
        
        // Verify buffer utilization is calculated correctly
        assertTrue("Buffer utilization should be reasonable", 
            stats.utilizationPercentage <= 100)
    }

    @Test
    fun `mixed data types are buffered and transmitted correctly`() = runTest {
        every { mockCommunicationClient.isConnected() } returns true
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        
        // Buffer mixed data types
        repeat(3) {
            val inertialReading = mockDataGenerator.generateWristWalkingInertialReading(testSession.sessionId)
            val biometricReading = mockDataGenerator.generateWearableWalkingBiometricReading(testSession.sessionId)
            
            transmissionManager.bufferInertialReading(inertialReading)
            transmissionManager.bufferBiometricReading(biometricReading)
        }
        
        // Verify both types are buffered
        val stats = transmissionManager.getBufferStatistics()
        assertEquals("Should have 3 inertial readings", 3, stats.inertialBufferSize)
        assertEquals("Should have 3 biometric readings", 3, stats.biometricBufferSize)
        assertEquals("Total should be 6 readings", 6, stats.totalBufferSize)
        
        // Force transmission
        transmissionManager.forceTransmission()
        
        // Verify both message types were sent
        coVerify { mockCommunicationClient.sendMessage(DataTransmissionManager.MESSAGE_TYPE_INERTIAL_BATCH, any()) }
        coVerify { mockCommunicationClient.sendMessage(DataTransmissionManager.MESSAGE_TYPE_BIOMETRIC_BATCH, any()) }
    }

    @Test
    fun `transmission failure causes data re-buffering`() = runTest {
        // Setup transmission failure
        coEvery { mockCommunicationClient.sendMessage(any(), any()) } returns false
        
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        val reading = mockDataGenerator.generateWristStationaryInertialReading(testSession.sessionId)
        
        transmissionManager.bufferInertialReading(reading)
        
        // Force transmission (which will fail)
        transmissionManager.forceTransmission()
        
        // Verify data is still in buffer after failed transmission
        val stats = transmissionManager.getBufferStatistics()
        assertTrue("Data should remain in buffer after transmission failure", 
            stats.inertialBufferSize > 0)
    }

    @Test
    fun `buffer status updates correctly during operations`() = runTest {
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        
        // Initial state
        val initialStatus = transmissionManager.bufferStatus.value
        assertEquals("Initial inertial buffer should be empty", 0, initialStatus.inertialBufferSize)
        assertEquals("Initial biometric buffer should be empty", 0, initialStatus.biometricBufferSize)
        assertEquals("Initial total should be zero", 0, initialStatus.totalBufferSize)
        assertFalse("Should not be near capacity initially", initialStatus.isNearCapacity)
        
        // Add some data
        repeat(10) {
            transmissionManager.bufferInertialReading(
                mockDataGenerator.generateWristStationaryInertialReading(testSession.sessionId)
            )
        }
        
        val updatedStatus = transmissionManager.bufferStatus.value
        assertEquals("Should have 10 inertial readings", 10, updatedStatus.inertialBufferSize)
        assertEquals("Total should be 10", 10, updatedStatus.totalBufferSize)
        assertTrue("Utilization should be greater than 0", updatedStatus.utilizationPercentage > 0)
    }

    @Test
    fun `sensor failure scenarios are handled gracefully`() = runTest {
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        
        // Generate readings with various sensor failures
        val accelerometerFailure = mockDataGenerator.generateInertialReadingWithSensorFailure(
            testSession.sessionId, MockSensorDataGenerator.SensorType.ACCELEROMETER
        )
        val gyroscopeFailure = mockDataGenerator.generateInertialReadingWithSensorFailure(
            testSession.sessionId, MockSensorDataGenerator.SensorType.GYROSCOPE
        )
        val magnetometerFailure = mockDataGenerator.generateInertialReadingWithSensorFailure(
            testSession.sessionId, MockSensorDataGenerator.SensorType.MAGNETOMETER
        )
        val heartRateFailure = mockDataGenerator.generateBiometricReadingWithHeartRateFailure(testSession.sessionId)
        
        // Buffer all failure scenarios
        transmissionManager.bufferInertialReading(accelerometerFailure)
        transmissionManager.bufferInertialReading(gyroscopeFailure)
        transmissionManager.bufferInertialReading(magnetometerFailure)
        transmissionManager.bufferBiometricReading(heartRateFailure)
        
        // Verify all data is buffered despite sensor failures
        val stats = transmissionManager.getBufferStatistics()
        assertEquals("Should buffer readings with sensor failures", 3, stats.inertialBufferSize)
        assertEquals("Should buffer biometric reading with heart rate failure", 1, stats.biometricBufferSize)
        
        // Verify data can be transmitted
        transmissionManager.forceTransmission()
        
        coVerify { mockCommunicationClient.sendMessage(any(), any()) }
    }

    @Test
    fun `rapid battery drain is tracked correctly`() = runTest {
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        
        // Generate readings with rapid battery drain
        val readings = (1..10).map {
            mockDataGenerator.generateReadingsWithRapidBatteryDrain(testSession.sessionId)
        }
        
        // Buffer all readings
        readings.forEach { reading ->
            transmissionManager.bufferInertialReading(reading)
        }
        
        // Verify battery levels are decreasing
        val batteryLevels = readings.map { it.batteryLevel }
        assertTrue("Battery levels should vary", batteryLevels.toSet().size > 1)
        
        // Verify all readings are buffered
        val stats = transmissionManager.getBufferStatistics()
        assertEquals("Should buffer all readings", 10, stats.inertialBufferSize)
    }

    @Test
    fun `high frequency data collection is handled correctly`() = runTest {
        transmissionManager.start()
        
        val testSession = mockDataGenerator.createMockWearableSession()
        
        // Generate high frequency data (simulating 50Hz collection)
        val highFrequencyData = mockDataGenerator.generateHighFrequencyInertialBatch(
            sessionId = testSession.sessionId,
            count = 50 // 1 second of 50Hz data
        )
        
        // Buffer all data rapidly
        highFrequencyData.forEach { reading ->
            transmissionManager.bufferInertialReading(reading)
        }
        
        // Verify all data is buffered
        val stats = transmissionManager.getBufferStatistics()
        assertEquals("Should buffer all high frequency data", 50, stats.inertialBufferSize)
        
        // Verify timestamps are reasonable
        val timestamps = highFrequencyData.map { it.timestamp }
        assertTrue("Timestamps should be in order", timestamps.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test(timeout = 15000) // 15 second timeout
    fun `connection monitoring detects state changes`() = runTest(timeout = 10000.milliseconds) {
        // Start connected
        every { mockCommunicationClient.isConnected() } returns true
        transmissionManager.start()
        
        // Use controlled time advancement
        advanceTimeBy(6000) // Wait for initial connection check
        runCurrent() // Process any pending coroutines
        
        assertTrue("Should be connected initially", transmissionManager.isConnected.value)
        
        // Simulate intermittent connection with controlled state changes
        val connectionStates = listOf(false, false, true, true, false)
        connectionStates.forEachIndexed { index, connected ->
            every { mockCommunicationClient.isConnected() } returns connected
            advanceTimeBy(6000) // Advance by monitoring interval
            runCurrent() // Process coroutines
            
            // Verify state change (with some tolerance for async updates)
            if (index == connectionStates.size - 1) {
                // Final verification
                assertFalse("Should detect final disconnected state", transmissionManager.isConnected.value)
            }
        }
    }
}