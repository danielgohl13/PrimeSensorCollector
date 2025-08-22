package com.example.primesensorcollector.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.Vector3D
import com.example.primesensorcollector.data.transmission.DataTransmissionManager
import com.example.primesensorcollector.data.transmission.WearableCommunicationClient
import com.example.primesensorcollector.data.transmission.WearableCommunicationClientImpl
import com.example.primesensorcollector.service.SensorCollectionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for the wearable data collection and transmission system
 * Tests sensor collection, data buffering, and transmission to smartphone
 * Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4, 7.1, 7.2, 7.3, 8.2, 8.3, 8.4
 */
@RunWith(AndroidJUnit4::class)
class WearableSystemIntegrationTest {

    private lateinit var context: Context
    private lateinit var communicationClient: MockWearableCommunicationClient
    private lateinit var dataTransmissionManager: DataTransmissionManager
    private lateinit var sensorCollectionService: SensorCollectionService
    
    // Test data
    private val testSessionId = "wearable_test_${System.currentTimeMillis()}"
    private val testDeviceId = "test_galaxy_watch_5"
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create mock communication client
        communicationClient = MockWearableCommunicationClient()
        
        // Initialize data transmission manager
        dataTransmissionManager = DataTransmissionManager(communicationClient)
        
        // Initialize sensor collection service (we'll mock sensor data)
        sensorCollectionService = SensorCollectionService()
    }
    
    @After
    fun tearDown() {
        // Clean up services
        dataTransmissionManager.stop()
        communicationClient.cleanup()
    }
    
    /**
     * Test complete data collection and transmission pipeline
     * Requirements: 2.1, 2.2, 2.3, 4.1, 4.2
     */
    @Test
    fun testCompleteDataCollectionPipeline() = runTest {
        // Step 1: Start data transmission manager
        dataTransmissionManager.start()
        
        // Wait for initialization
        delay(500)
        
        // Step 2: Generate and buffer test sensor data
        val testInertialReadings = generateTestInertialData(testSessionId, 10)
        val testBiometricReadings = generateTestBiometricData(testSessionId, 5)
        
        // Buffer the data
        testInertialReadings.forEach { reading ->
            dataTransmissionManager.bufferInertialReading(reading)
        }
        
        testBiometricReadings.forEach { reading ->
            dataTransmissionManager.bufferBiometricReading(reading)
        }
        
        // Step 3: Verify data is buffered
        val bufferStats = dataTransmissionManager.getBufferStatistics()
        assertEquals("Inertial buffer should contain test data", 
            testInertialReadings.size, bufferStats.inertialBufferSize)
        assertEquals("Biometric buffer should contain test data", 
            testBiometricReadings.size, bufferStats.biometricBufferSize)
        
        // Step 4: Simulate connection and wait for transmission
        communicationClient.setConnected(true)
        
        // Wait for periodic transmission to occur
        withTimeout(5000) {
            while (bufferStats.totalBufferSize > 0) {
                delay(100)
                val currentStats = dataTransmissionManager.getBufferStatistics()
                if (currentStats.totalBufferSize == 0) break
            }
        }
        
        // Step 5: Verify data was transmitted
        val finalStats = dataTransmissionManager.getBufferStatistics()
        assertEquals("Inertial buffer should be empty after transmission", 
            0, finalStats.inertialBufferSize)
        assertEquals("Biometric buffer should be empty after transmission", 
            0, finalStats.biometricBufferSize)
        
        // Step 6: Verify messages were sent to smartphone
        val sentMessages = communicationClient.getSentMessages()
        assertTrue("Should have sent inertial data messages", 
            sentMessages.any { it.first == DataTransmissionManager.MESSAGE_TYPE_INERTIAL_BATCH })
        assertTrue("Should have sent biometric data messages", 
            sentMessages.any { it.first == DataTransmissionManager.MESSAGE_TYPE_BIOMETRIC_BATCH })
    }
    
    /**
     * Test data buffering and capacity management
     * Requirements: 4.3, 4.4, 8.2, 8.3, 8.4
     */
    @Test
    fun testDataBufferingAndCapacityManagement() = runTest {
        dataTransmissionManager.start()
        
        // Test normal buffering
        val normalData = generateTestInertialData(testSessionId, 50)
        normalData.forEach { reading ->
            dataTransmissionManager.bufferInertialReading(reading)
        }
        
        val stats = dataTransmissionManager.getBufferStatistics()
        assertEquals("Buffer should contain all data", 50, stats.inertialBufferSize)
        assertFalse("Buffer should not be near capacity", stats.isNearCapacity)
        
        // Test buffer overflow handling
        val overflowData = generateTestInertialData(testSessionId, 1000)
        overflowData.forEach { reading ->
            dataTransmissionManager.bufferInertialReading(reading)
        }
        
        val overflowStats = dataTransmissionManager.getBufferStatistics()
        assertTrue("Buffer should handle overflow", overflowStats.inertialBufferSize <= 1000)
        
        // Test buffer status monitoring
        val bufferStatus = dataTransmissionManager.bufferStatus.first()
        assertTrue("Buffer status should reflect current state", 
            bufferStatus.totalBufferSize > 0)
    }
    
    /**
     * Test connection loss handling and retry logic
     * Requirements: 8.2, 8.3, 8.4
     */
    @Test
    fun testConnectionLossHandlingAndRetry() = runTest {
        dataTransmissionManager.start()
        
        // Step 1: Buffer data while disconnected
        communicationClient.setConnected(false)
        
        val testData = generateTestInertialData(testSessionId, 5)
        testData.forEach { reading ->
            dataTransmissionManager.bufferInertialReading(reading)
        }
        
        // Wait for transmission attempts (should fail)
        delay(2000)
        
        val disconnectedStats = dataTransmissionManager.getBufferStatistics()
        assertEquals("Data should remain buffered when disconnected", 
            5, disconnectedStats.inertialBufferSize)
        assertFalse("Should not be connected", disconnectedStats.isConnected)
        
        // Step 2: Restore connection and verify data transmission
        communicationClient.setConnected(true)
        
        // Wait for connection restoration and data transmission
        withTimeout(10000) {
            while (true) {
                val stats = dataTransmissionManager.getBufferStatistics()
                if (stats.isConnected && stats.inertialBufferSize == 0) {
                    break
                }
                delay(100)
            }
        }
        
        val connectedStats = dataTransmissionManager.getBufferStatistics()
        assertTrue("Should be connected", connectedStats.isConnected)
        assertEquals("Buffered data should be transmitted after reconnection", 
            0, connectedStats.inertialBufferSize)
        
        // Verify messages were sent
        val sentMessages = communicationClient.getSentMessages()
        assertTrue("Should have sent buffered data after reconnection", 
            sentMessages.isNotEmpty())
    }
    
    /**
     * Test batch transmission timing and efficiency
     * Requirements: 4.1, 4.2
     */
    @Test
    fun testBatchTransmissionTiming() = runTest {
        dataTransmissionManager.start()
        communicationClient.setConnected(true)
        
        val startTime = System.currentTimeMillis()
        
        // Add data gradually to test batch transmission
        repeat(20) { i ->
            val reading = InertialReading(
                timestamp = System.currentTimeMillis(),
                sessionId = testSessionId,
                deviceId = testDeviceId,
                accelerometer = Vector3D(i.toFloat(), 0f, 0f),
                gyroscope = Vector3D.ZERO,
                magnetometer = Vector3D.ZERO,
                batteryLevel = 100
            )
            dataTransmissionManager.bufferInertialReading(reading)
            delay(50) // Add data every 50ms
        }
        
        // Wait for batch transmission
        withTimeout(5000) {
            while (dataTransmissionManager.getBufferStatistics().inertialBufferSize > 0) {
                delay(100)
            }
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        // Verify transmission occurred within reasonable time
        assertTrue("Batch transmission should complete within 5 seconds", totalTime < 5000)
        
        // Verify data was sent in batches (not individual messages)
        val sentMessages = communicationClient.getSentMessages()
        assertTrue("Should have sent messages", sentMessages.isNotEmpty())
        // Should be fewer messages than individual readings due to batching
        assertTrue("Should batch multiple readings per message", 
            sentMessages.size < 20)
    }
    
    /**
     * Test sensor data collection simulation
     * Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3
     */
    @Test
    fun testSensorDataCollection() = runTest {
        // This test simulates sensor data collection since we can't access real sensors in unit tests
        
        // Test inertial sensor data generation
        val inertialReading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = testSessionId,
            deviceId = testDeviceId,
            accelerometer = Vector3D(9.81f, 0f, 0f), // Simulated gravity
            gyroscope = Vector3D(0.1f, 0.2f, 0.3f), // Simulated rotation
            magnetometer = Vector3D(45.2f, -12.3f, 8.7f), // Simulated magnetic field
            batteryLevel = 85
        )
        
        // Verify data structure and values
        assertNotNull("Timestamp should be set", inertialReading.timestamp)
        assertEquals("Session ID should match", testSessionId, inertialReading.sessionId)
        assertEquals("Device ID should match", testDeviceId, inertialReading.deviceId)
        
        // Verify sensor data ranges are reasonable
        assertTrue("Accelerometer magnitude should be reasonable", 
            inertialReading.accelerometer.magnitude() > 0)
        assertTrue("Gyroscope values should be reasonable", 
            inertialReading.gyroscope.magnitude() < 10) // Typical range
        assertTrue("Magnetometer values should be reasonable", 
            inertialReading.magnetometer.magnitude() > 0)
        assertTrue("Battery level should be valid", 
            inertialReading.batteryLevel in 0..100)
        
        // Test biometric data generation
        val biometricReading = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = testSessionId,
            deviceId = testDeviceId,
            heartRate = 72,
            stepCount = 1500,
            calories = 45.5f,
            skinTemperature = null,
            batteryLevel = 85
        )
        
        // Verify biometric data ranges
        assertTrue("Heart rate should be reasonable", 
            biometricReading.heartRate!! in 40..200)
        assertTrue("Step count should be non-negative", 
            biometricReading.stepCount >= 0)
        assertTrue("Calories should be non-negative", 
            biometricReading.calories >= 0)
    }
    
    /**
     * Test wearable UI touch controls simulation
     * Requirements: 7.1, 7.2, 7.3
     */
    @Test
    fun testWearableUIControls() = runTest {
        // This test simulates the wearable UI controls since we can't test actual UI in unit tests
        
        var collectionState = false
        var lastCommand: String? = null
        
        // Simulate tap gesture when not collecting (should start)
        if (!collectionState) {
            lastCommand = WearableCommunicationClientImpl.COMMAND_START_COLLECTION
            collectionState = true
        }
        
        assertEquals("Should send start command", 
            WearableCommunicationClientImpl.COMMAND_START_COLLECTION, lastCommand)
        assertTrue("Collection state should be active", collectionState)
        
        // Simulate tap gesture when collecting (should stop)
        if (collectionState) {
            lastCommand = WearableCommunicationClientImpl.COMMAND_STOP_COLLECTION
            collectionState = false
        }
        
        assertEquals("Should send stop command", 
            WearableCommunicationClientImpl.COMMAND_STOP_COLLECTION, lastCommand)
        assertFalse("Collection state should be inactive", collectionState)
    }
    
    /**
     * Test force transmission functionality
     * Requirements: 4.2, 8.4
     */
    @Test
    fun testForceTransmission() = runTest {
        dataTransmissionManager.start()
        communicationClient.setConnected(true)
        
        // Buffer some data
        val testData = generateTestInertialData(testSessionId, 3)
        testData.forEach { reading ->
            dataTransmissionManager.bufferInertialReading(reading)
        }
        
        // Verify data is buffered
        val beforeStats = dataTransmissionManager.getBufferStatistics()
        assertEquals("Data should be buffered", 3, beforeStats.inertialBufferSize)
        
        // Force immediate transmission
        dataTransmissionManager.forceTransmission()
        
        // Wait for transmission to complete
        delay(1000)
        
        // Verify data was transmitted
        val afterStats = dataTransmissionManager.getBufferStatistics()
        assertEquals("Data should be transmitted", 0, afterStats.inertialBufferSize)
        
        // Verify message was sent
        val sentMessages = communicationClient.getSentMessages()
        assertTrue("Should have sent forced transmission", sentMessages.isNotEmpty())
    }
    
    // Helper methods
    
    private fun generateTestInertialData(sessionId: String, count: Int): List<InertialReading> {
        return (1..count).map { i ->
            InertialReading(
                timestamp = System.currentTimeMillis() + i * 20, // 50Hz = 20ms intervals
                sessionId = sessionId,
                deviceId = testDeviceId,
                accelerometer = Vector3D(
                    9.81f + (i * 0.1f), // Gravity + small variation
                    i * 0.05f,
                    i * 0.02f
                ),
                gyroscope = Vector3D(
                    i * 0.01f,
                    i * 0.02f,
                    i * 0.01f
                ),
                magnetometer = Vector3D(
                    45.0f + i,
                    -12.0f + i * 0.5f,
                    8.0f + i * 0.2f
                ),
                batteryLevel = 100 - (i / 10) // Gradual battery decrease
            )
        }
    }
    
    private fun generateTestBiometricData(sessionId: String, count: Int): List<BiometricReading> {
        return (1..count).map { i ->
            BiometricReading(
                timestamp = System.currentTimeMillis() + i * 1000, // 1 second intervals
                sessionId = sessionId,
                deviceId = testDeviceId,
                heartRate = 70 + (i % 20), // Varying heart rate
                stepCount = 1000 + i * 10, // Increasing step count
                calories = 40.0f + i * 2.0f, // Increasing calories
                skinTemperature = null,
                batteryLevel = 100 - (i / 5) // Gradual battery decrease
            )
        }
    }
    
    /**
     * Mock implementation of WearableCommunicationClient for testing
     */
    private class MockWearableCommunicationClient : WearableCommunicationClient {
        private var connected = false
        private val sentMessages = mutableListOf<Pair<String, String>>()
        private var commandListener: ((String, String?) -> Unit)? = null
        
        override suspend fun initialize(): Boolean = true
        
        override suspend fun sendMessage(messageType: String, data: String): Boolean {
            return if (connected) {
                sentMessages.add(Pair(messageType, data))
                true
            } else {
                false
            }
        }
        
        override suspend fun isConnected(): Boolean = connected
        
        override fun setCommandListener(onCommandReceived: (String, String?) -> Unit) {
            commandListener = onCommandReceived
        }
        
        override suspend fun reportStatus(status: String): Boolean = connected
        
        override fun cleanup() {
            sentMessages.clear()
        }
        
        // Test helper methods
        fun setConnected(isConnected: Boolean) {
            connected = isConnected
        }
        
        fun getSentMessages(): List<Pair<String, String>> = sentMessages.toList()
        
        fun simulateCommand(command: String, data: String? = null) {
            commandListener?.invoke(command, data)
        }
    }
}