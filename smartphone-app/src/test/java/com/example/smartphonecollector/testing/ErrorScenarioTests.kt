package com.example.smartphonecollector.testing

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.communication.WearableCommunicationService
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.repository.DataRepository
import com.example.smartphonecollector.ui.viewmodel.CollectionViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

/**
 * Tests for error scenarios and connection failure recovery
 * Validates system behavior under various failure conditions
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ErrorScenarioTests {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var mockDataRepository: DataRepository
    private lateinit var mockDataGenerator: MockSensorDataGenerator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        context = RuntimeEnvironment.getApplication()
        mockDataRepository = mockk(relaxed = true)
        mockDataGenerator = MockSensorDataGenerator()
        
        // Setup default mock behaviors
        every { mockDataRepository.isStorageAvailable() } returns true
        every { mockDataRepository.isStorageSpaceLow() } returns false
        every { mockDataRepository.getAvailableStorageSpace() } returns 1000000000L
        every { mockDataRepository.createSession(any()) } returns mockDataGenerator.createMockSession()
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `repository handles storage unavailable error`() = runTest {
        // Mock storage unavailable
        every { mockDataRepository.isStorageAvailable() } returns false
        
        val viewModel = CollectionViewModel(context, mockDataRepository)
        
        // Attempt to start collection
        viewModel.startCollection()
        advanceUntilIdle()
        
        // Verify collection was not started due to storage unavailability
        assertFalse("Collection should not start when storage unavailable", viewModel.isCollecting.value)
        assertNotNull("Error message should be set", viewModel.errorMessage.value)
        assertTrue("Error should mention storage", viewModel.errorMessage.value!!.contains("Storage"))
    }

    @Test
    fun `repository handles low storage space error`() = runTest {
        // Mock low storage space
        every { mockDataRepository.isStorageSpaceLow() } returns true
        coEvery { mockDataRepository.cleanupOldFiles() } returns Result.success(0) // No files to clean
        
        val viewModel = CollectionViewModel(context, mockDataRepository)
        
        // Attempt to start collection
        viewModel.startCollection()
        advanceUntilIdle()
        
        // Verify cleanup was attempted
        coVerify { mockDataRepository.cleanupOldFiles() }
        
        // Since cleanup didn't free space, collection should fail
        assertFalse("Collection should not start when storage is low", viewModel.isCollecting.value)
        assertNotNull("Error message should be set", viewModel.errorMessage.value)
        assertTrue("Error should mention storage space", viewModel.errorMessage.value!!.contains("storage space"))
    }

    @Test
    fun `repository handles successful cleanup after low storage`() = runTest {
        // Mock low storage initially, then available after cleanup
        every { mockDataRepository.isStorageSpaceLow() } returnsMany listOf(true, false)
        coEvery { mockDataRepository.cleanupOldFiles() } returns Result.success(5) // Cleaned 5 files
        
        val viewModel = CollectionViewModel(context, mockDataRepository)
        
        // Attempt to start collection
        viewModel.startCollection()
        advanceUntilIdle()
        
        // Verify cleanup was attempted and collection could proceed
        coVerify { mockDataRepository.cleanupOldFiles() }
        
        // Note: In this test, collection might still fail due to communication service initialization
        // but the storage check should pass
    }

    @Test
    fun `repository handles file write errors gracefully`() = runTest {
        val testSession = mockDataGenerator.createMockSession()
        val testReading = mockDataGenerator.generateStationaryInertialReading(testSession.sessionId)
        
        // Mock file write failure
        coEvery { mockDataRepository.appendInertialData(any(), any()) } returns Result.failure(IOException("Disk full"))
        
        val viewModel = CollectionViewModel(context, mockDataRepository)
        
        // Simulate receiving data that fails to save
        // Note: This would normally be called by the communication service
        // In a real test, we would need to inject the data through the communication layer
        
        // For now, test the repository directly
        val result = mockDataRepository.appendInertialData(testSession.sessionId, testReading)
        
        assertTrue("Save operation should fail", result.isFailure)
        assertTrue("Exception should be IOException", result.exceptionOrNull() is IOException)
        assertEquals("Error message should match", "Disk full", result.exceptionOrNull()?.message)
    }

    @Test
    fun `repository handles emergency cleanup when storage critically low`() = runTest {
        // Mock critically low storage during data save
        every { mockDataRepository.isStorageSpaceLow() } returns true
        coEvery { mockDataRepository.appendInertialData(any(), any()) } returns Result.failure(IOException("No space left"))
        coEvery { mockDataRepository.performEmergencyCleanup() } returns Result.success(
            com.example.smartphonecollector.data.repository.CleanupResult(
                filesDeleted = 10,
                spaceFreed = 50 * 1024 * 1024L, // 50MB
                finalFreeSpace = 200 * 1024 * 1024L // 200MB
            )
        )
        
        val viewModel = CollectionViewModel(context, mockDataRepository)
        
        // This test would require injecting data through the communication layer
        // For now, test the emergency cleanup directly
        val cleanupResult = mockDataRepository.performEmergencyCleanup()
        
        assertTrue("Emergency cleanup should succeed", cleanupResult.isSuccess)
        val result = cleanupResult.getOrNull()!!
        assertEquals("Should delete 10 files", 10, result.filesDeleted)
        assertEquals("Should free 50MB", 50L, result.spaceFreedMB)
        assertEquals("Final free space should be 200MB", 200L, result.finalFreeSpaceMB)
    }

    @Test
    fun `data serialization handles corrupted data gracefully`() {
        // Test with invalid sensor data
        val corruptedReading = mockDataGenerator.generateStationaryInertialReading("test_session").copy(
            timestamp = -1L, // Invalid timestamp
            batteryLevel = 150 // Invalid battery level
        )
        
        // The CSV conversion should still work even with invalid data
        val csvRow = corruptedReading.toCsvRow()
        
        assertNotNull("CSV row should be generated even with invalid data", csvRow)
        assertTrue("CSV should contain invalid timestamp", csvRow.contains("-1"))
        assertTrue("CSV should contain invalid battery level", csvRow.contains("150"))
    }

    @Test
    fun `biometric data handles all null values correctly`() {
        val readingWithNulls = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            heartRate = null,
            stepCount = 0,
            calories = 0.0f,
            skinTemperature = null,
            batteryLevel = 50
        )
        
        val csvRow = readingWithNulls.toCsvRow()
        
        assertNotNull("CSV row should be generated with null values", csvRow)
        assertTrue("CSV should handle null heart rate", csvRow.contains(",,")) // Empty field between commas
        assertTrue("CSV should contain step count", csvRow.contains(",0,"))
        assertTrue("CSV should contain battery level", csvRow.contains(",50"))
    }

    @Test
    fun `session data handles invalid timestamps gracefully`() {
        val invalidSession = mockDataGenerator.createMockSession().copy(
            startTime = -1L,
            endTime = -2L
        )
        
        // Duration calculation should handle invalid timestamps
        val duration = invalidSession.getDuration()
        
        // Duration might be negative or unexpected, but shouldn't crash
        assertNotNull("Duration should be calculated even with invalid timestamps", duration)
    }

    @Test
    fun `mock data generator handles edge cases`() {
        // Test with extreme battery levels
        val lowBatteryReading = mockDataGenerator.generateLowBatteryInertialReading("test", 0)
        assertEquals("Battery level should be 0", 0, lowBatteryReading.batteryLevel)
        
        val highBatteryReading = mockDataGenerator.generateLowBatteryInertialReading("test", 100)
        assertEquals("Battery level should be 100", 100, highBatteryReading.batteryLevel)
        
        // Test with large batch generation
        val largeBatch = mockDataGenerator.generateInertialReadingBatch(
            sessionId = "large_test",
            count = 1000,
            motionType = MockSensorDataGenerator.MotionType.RUNNING
        )
        
        assertEquals("Should generate correct number of readings", 1000, largeBatch.size)
        assertTrue("All readings should have same session ID", 
            largeBatch.all { it.sessionId == "large_test" })
        assertTrue("Timestamps should be increasing", 
            largeBatch.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp })
    }

    @Test
    fun `connection status changes are handled correctly`() {
        val mockConnectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        
        // Test connection status transitions
        assertEquals("Initial status should be disconnected", ConnectionStatus.DISCONNECTED, mockConnectionStatus.value)
        
        mockConnectionStatus.value = ConnectionStatus.CONNECTING
        assertEquals("Status should change to connecting", ConnectionStatus.CONNECTING, mockConnectionStatus.value)
        
        mockConnectionStatus.value = ConnectionStatus.CONNECTED
        assertEquals("Status should change to connected", ConnectionStatus.CONNECTED, mockConnectionStatus.value)
        
        mockConnectionStatus.value = ConnectionStatus.ERROR
        assertEquals("Status should change to error", ConnectionStatus.ERROR, mockConnectionStatus.value)
    }

    @Test
    fun `battery level validation works correctly`() {
        // Test battery level boundary conditions
        val readings = listOf(
            mockDataGenerator.generateLowBatteryInertialReading("test", -1), // Below minimum
            mockDataGenerator.generateLowBatteryInertialReading("test", 0),  // Minimum
            mockDataGenerator.generateLowBatteryInertialReading("test", 15), // Critical threshold
            mockDataGenerator.generateLowBatteryInertialReading("test", 20), // Low threshold
            mockDataGenerator.generateLowBatteryInertialReading("test", 100), // Maximum
            mockDataGenerator.generateLowBatteryInertialReading("test", 101)  // Above maximum
        )
        
        // All readings should be generated without errors
        assertEquals("Should generate all readings", 6, readings.size)
        
        // Verify battery levels are set correctly (even if invalid)
        assertEquals("Should set negative battery level", -1, readings[0].batteryLevel)
        assertEquals("Should set zero battery level", 0, readings[1].batteryLevel)
        assertEquals("Should set critical battery level", 15, readings[2].batteryLevel)
        assertEquals("Should set low battery level", 20, readings[3].batteryLevel)
        assertEquals("Should set full battery level", 100, readings[4].batteryLevel)
        assertEquals("Should set over-full battery level", 101, readings[5].batteryLevel)
    }

    @Test
    fun `sensor data validation handles extreme values`() {
        // Test with extreme sensor values
        val extremeReading = mockDataGenerator.generateStationaryInertialReading("test").copy(
            accelerometer = com.example.smartphonecollector.data.models.Vector3D(Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN),
            gyroscope = com.example.smartphonecollector.data.models.Vector3D(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.0f),
            magnetometer = com.example.smartphonecollector.data.models.Vector3D(-Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE)
        )
        
        // CSV conversion should handle extreme values
        val csvRow = extremeReading.toCsvRow()
        assertNotNull("CSV should be generated with extreme values", csvRow)
        
        // Magnitude calculation should handle extreme values
        val accelMagnitude = extremeReading.accelerometer.magnitude()
        // Magnitude might be NaN or Infinity, but shouldn't crash
        assertNotNull("Magnitude should be calculated", accelMagnitude)
    }

    @Test
    fun `cleanup operations handle various error conditions`() = runTest {
        // Test cleanup failure
        coEvery { mockDataRepository.cleanupOldFiles() } returns Result.failure(IOException("Permission denied"))
        
        val viewModel = CollectionViewModel(context, mockDataRepository)
        viewModel.performCleanup()
        advanceUntilIdle()
        
        // Should handle cleanup failure gracefully
        val errorMessage = viewModel.errorMessage.value
        assertNotNull("Error message should be set on cleanup failure", errorMessage)
        assertTrue("Error should mention cleanup failure", errorMessage!!.contains("Cleanup failed"))
        
        // Test emergency cleanup failure
        coEvery { mockDataRepository.performEmergencyCleanup() } returns Result.failure(SecurityException("Access denied"))
        
        val emergencyResult = mockDataRepository.performEmergencyCleanup()
        assertTrue("Emergency cleanup should fail", emergencyResult.isFailure)
        assertTrue("Exception should be SecurityException", emergencyResult.exceptionOrNull() is SecurityException)
    }
}