package com.example.primesensorcollector.data.transmission

import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.Vector3D
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DataTransmissionManagerSimpleTest {
    
    private lateinit var mockCommunicationClient: MockWearableCommunicationClient
    private lateinit var dataTransmissionManager: DataTransmissionManager
    
    @Before
    fun setup() {
        mockCommunicationClient = MockWearableCommunicationClient()
        dataTransmissionManager = DataTransmissionManager(mockCommunicationClient)
    }
    
    @After
    fun tearDown() {
        dataTransmissionManager.stop()
    }
    
    @Test
    fun `bufferInertialReading should add reading to buffer`() = runTest {
        // Given
        val reading = createTestInertialReading()
        
        // When
        dataTransmissionManager.bufferInertialReading(reading)
        
        // Then
        val stats = dataTransmissionManager.getBufferStatistics()
        assertEquals(1, stats.inertialBufferSize)
        assertEquals(0, stats.biometricBufferSize)
    }
    
    @Test
    fun `bufferBiometricReading should add reading to buffer`() = runTest {
        // Given
        val reading = createTestBiometricReading()
        
        // When
        dataTransmissionManager.bufferBiometricReading(reading)
        
        // Then
        val stats = dataTransmissionManager.getBufferStatistics()
        assertEquals(0, stats.inertialBufferSize)
        assertEquals(1, stats.biometricBufferSize)
    }
    
    @Test
    fun `buffer should handle multiple readings`() = runTest {
        // Given
        repeat(5) {
            dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        }
        repeat(3) {
            dataTransmissionManager.bufferBiometricReading(createTestBiometricReading())
        }
        
        // Then
        val stats = dataTransmissionManager.getBufferStatistics()
        assertEquals(5, stats.inertialBufferSize)
        assertEquals(3, stats.biometricBufferSize)
        assertEquals(8, stats.totalBufferSize)
    }
    
    @Test
    fun `forceTransmission should work when connected`() = runTest {
        // Given
        mockCommunicationClient.setConnected(true)
        dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        dataTransmissionManager.bufferBiometricReading(createTestBiometricReading())
        
        // When
        dataTransmissionManager.forceTransmission()
        
        // Then
        assertTrue("Should have sent messages", mockCommunicationClient.messagesSent.isNotEmpty())
    }
    
    @Test
    fun `forceTransmission should not transmit when disconnected`() = runTest {
        // Given
        mockCommunicationClient.setConnected(false)
        dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        
        // When
        dataTransmissionManager.forceTransmission()
        
        // Then
        assertTrue("Should not have sent messages", mockCommunicationClient.messagesSent.isEmpty())
    }
    
    private fun createTestInertialReading(): InertialReading {
        return InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            accelerometer = Vector3D(1.0f, 2.0f, 3.0f),
            gyroscope = Vector3D(0.1f, 0.2f, 0.3f),
            magnetometer = Vector3D(10.0f, 20.0f, 30.0f),
            batteryLevel = 85
        )
    }
    
    private fun createTestBiometricReading(): BiometricReading {
        return BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            heartRate = 72,
            stepCount = 1000,
            calories = 50.5f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )
    }
}

/**
 * Simple mock implementation for testing
 */
class MockWearableCommunicationClient : WearableCommunicationClient {
    private var connected = true
    val messagesSent = mutableListOf<Pair<String, String>>()
    private var commandListener: ((String, String?) -> Unit)? = null
    
    fun setConnected(isConnected: Boolean) {
        connected = isConnected
    }
    
    override suspend fun sendMessage(messageType: String, data: String): Boolean {
        return if (connected) {
            messagesSent.add(messageType to data)
            true
        } else {
            false
        }
    }
    
    override suspend fun isConnected(): Boolean = connected
    
    override fun setCommandListener(onCommandReceived: (command: String, data: String?) -> Unit) {
        commandListener = onCommandReceived
    }
    
    override suspend fun reportStatus(status: String): Boolean = connected
    
    override suspend fun initialize(): Boolean = true
    
    override fun cleanup() {
        // No-op for mock
    }
    
    fun simulateCommand(command: String, data: String? = null) {
        commandListener?.invoke(command, data)
    }
}