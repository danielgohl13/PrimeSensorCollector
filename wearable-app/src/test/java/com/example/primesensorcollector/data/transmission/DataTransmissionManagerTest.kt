package com.example.primesensorcollector.data.transmission

import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.Vector3D
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class DataTransmissionManagerTest {
    
    @Mock
    private lateinit var mockCommunicationClient: WearableCommunicationClient
    
    private lateinit var dataTransmissionManager: DataTransmissionManager
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock the communication client to return success by default
        runTest {
            whenever(mockCommunicationClient.isConnected()).thenReturn(true)
            whenever(mockCommunicationClient.sendMessage(any(), any())).thenReturn(true)
            whenever(mockCommunicationClient.initialize()).thenReturn(true)
        }
        
        dataTransmissionManager = DataTransmissionManager(mockCommunicationClient)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `buffer should handle overflow by removing oldest entries`() = runTest {
        // Given - Fill buffer beyond capacity
        repeat(1010) { // More than MAX_BUFFER_SIZE (1000)
            dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        }
        
        // Then - Buffer should not exceed maximum size
        val stats = dataTransmissionManager.getBufferStatistics()
        assertTrue("Buffer size should be less than or equal to max", 
                  stats.inertialBufferSize <= 1000)
    }
    
    @Test
    fun `start should initialize periodic transmission`() = runTest {
        // Given
        whenever(mockCommunicationClient.isConnected()).thenReturn(true)
        whenever(mockCommunicationClient.sendMessage(any(), any())).thenReturn(true)
        
        // When
        dataTransmissionManager.start()
        
        // Add some data to buffer
        dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        
        // Advance time to trigger transmission
        testDispatcher.scheduler.advanceTimeBy(1100) // Just over 1 second
        
        // Then
        verify(mockCommunicationClient, timeout(2000)).isConnected()
    }
    
    @Test
    fun `forceTransmission should transmit when connected`() = runTest {
        // Given
        whenever(mockCommunicationClient.isConnected()).thenReturn(true)
        whenever(mockCommunicationClient.sendMessage(any(), any())).thenReturn(true)
        
        dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        dataTransmissionManager.bufferBiometricReading(createTestBiometricReading())
        
        // When
        dataTransmissionManager.forceTransmission()
        
        // Then
        verify(mockCommunicationClient, times(2)).sendMessage(any(), any())
    }
    
    @Test
    fun `forceTransmission should not transmit when disconnected`() = runTest {
        // Given
        whenever(mockCommunicationClient.isConnected()).thenReturn(false)
        
        dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        
        // When
        dataTransmissionManager.forceTransmission()
        
        // Then
        verify(mockCommunicationClient, never()).sendMessage(any(), any())
    }
    
    @Test
    fun `transmission should retry on failure with exponential backoff`() = runTest {
        // Given
        whenever(mockCommunicationClient.isConnected()).thenReturn(true)
        whenever(mockCommunicationClient.sendMessage(any(), any()))
            .thenReturn(false) // First attempts fail
            .thenReturn(false)
            .thenReturn(true)  // Third attempt succeeds
        
        dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        
        // When
        dataTransmissionManager.forceTransmission()
        
        // Then
        verify(mockCommunicationClient, times(3)).sendMessage(any(), any())
    }
    
    @Test
    fun `buffer status should update correctly`() = runTest {
        // Given
        val initialStatus = dataTransmissionManager.bufferStatus.first()
        assertEquals(0, initialStatus.totalBufferSize)
        
        // When
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
    fun `buffer should warn when approaching capacity`() = runTest {
        // Given - Fill buffer to warning threshold (80%)
        repeat(800) {
            dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        }
        repeat(800) {
            dataTransmissionManager.bufferBiometricReading(createTestBiometricReading())
        }
        
        // Then
        val stats = dataTransmissionManager.getBufferStatistics()
        assertTrue("Buffer should be near capacity", stats.isNearCapacity)
        assertTrue("Utilization should be >= 80%", stats.utilizationPercentage >= 80)
    }
    
    @Test
    fun `stop should clear all buffers`() = runTest {
        // Given
        dataTransmissionManager.bufferInertialReading(createTestInertialReading())
        dataTransmissionManager.bufferBiometricReading(createTestBiometricReading())
        
        val statsBeforeStop = dataTransmissionManager.getBufferStatistics()
        assertTrue("Buffer should have data before stop", statsBeforeStop.totalBufferSize > 0)
        
        // When
        dataTransmissionManager.stop()
        
        // Then
        val statsAfterStop = dataTransmissionManager.getBufferStatistics()
        assertEquals(0, statsAfterStop.totalBufferSize)
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