package com.example.smartphonecollector.ui.viewmodel

import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.data.repository.DataRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Comprehensive tests for CollectionViewModel
 * Requirements: 1.1, 1.2, 1.3, 5.4
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CollectionViewModelTest {

    private lateinit var mockDataRepository: DataRepository
    private lateinit var viewModel: CollectionViewModel

    @Before
    fun setup() {
        mockDataRepository = mockk(relaxed = true)
        
        // Mock basic repository methods
        every { mockDataRepository.isStorageAvailable() } returns true
        every { mockDataRepository.getAvailableStorageSpace() } returns 1000000L
        every { mockDataRepository.createSession(any()) } returns SessionData.createNew("test_device")
        coEvery { mockDataRepository.appendInertialData(any(), any()) } returns Result.success(Unit)
        coEvery { mockDataRepository.appendBiometricData(any(), any()) } returns Result.success(Unit)
        
        // Use Robolectric context
        val context = RuntimeEnvironment.getApplication()
        viewModel = CollectionViewModel(context, mockDataRepository)
    }

    @Test
    fun `viewModel should initialize with correct default state`() {
        // Verify ViewModel can be instantiated
        assertNotNull(viewModel)
        
        // Verify StateFlow properties exist and have correct initial values
        assertNotNull(viewModel.isCollecting)
        assertFalse("Should not be collecting initially", viewModel.isCollecting.value)
        
        assertNotNull(viewModel.sessionData)
        assertNull("Session data should be null initially", viewModel.sessionData.value)
        
        assertNotNull(viewModel.connectionStatus)
        assertEquals("Initial connection status should be DISCONNECTED", 
            ConnectionStatus.DISCONNECTED, viewModel.connectionStatus.value)
        
        assertNotNull(viewModel.realTimeInertialData)
        assertNull("Real-time inertial data should be null initially", viewModel.realTimeInertialData.value)
        
        assertNotNull(viewModel.realTimeBiometricData)
        assertNull("Real-time biometric data should be null initially", viewModel.realTimeBiometricData.value)
        
        assertNotNull(viewModel.errorMessage)
        assertNull("Error message should be null initially", viewModel.errorMessage.value)
        
        assertNotNull(viewModel.dataPointsCount)
        assertEquals("Data points count should be 0 initially", 0, viewModel.dataPointsCount.value)
    }

    @Test
    fun `viewModel should have required methods for session management`() {
        // Verify required methods exist (Requirements 1.1, 1.2, 1.3)
        try {
            viewModel.startCollection()
            viewModel.stopCollection()
            viewModel.clearError()
            viewModel.checkConnection()
            
            // Verify utility methods exist (Requirements 5.4)
            val duration = viewModel.getCurrentSessionDuration()
            val batteryLevel = viewModel.getCurrentBatteryLevel()
            val isLowBattery = viewModel.isBatteryLow()
            val storageInfo = viewModel.getStorageInfo()
            
            // If we get here, all methods exist
            assertTrue("All required methods exist", true)
        } catch (e: Exception) {
            fail("Required methods are missing or have incorrect signatures: ${e.message}")
        }
    }

    @Test
    fun `getStorageInfo should return repository values`() {
        val (isAvailable, space) = viewModel.getStorageInfo()
        
        assertTrue("Storage should be available", isAvailable)
        assertEquals("Storage space should match repository", 1000000L, space)
        
        verify { mockDataRepository.isStorageAvailable() }
        verify { mockDataRepository.getAvailableStorageSpace() }
    }

    @Test
    fun `getCurrentSessionDuration should return 0 when no session`() {
        val duration = viewModel.getCurrentSessionDuration()
        assertEquals("Duration should be 0 when no session", 0L, duration)
    }

    @Test
    fun `getCurrentBatteryLevel should return null when no data`() {
        val batteryLevel = viewModel.getCurrentBatteryLevel()
        assertNull("Battery level should be null when no data", batteryLevel)
    }

    @Test
    fun `isBatteryLow should return false when no battery data`() {
        val isLow = viewModel.isBatteryLow()
        assertFalse("Battery should not be considered low when no data", isLow)
    }

    @Test
    fun `isBatteryLow should return true when battery level is below 20 percent`() = runTest {
        // Create test inertial reading with low battery
        val lowBatteryReading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            accelerometer = Vector3D(0f, 0f, 9.8f),
            gyroscope = Vector3D(0f, 0f, 0f),
            magnetometer = Vector3D(0f, 0f, 0f),
            batteryLevel = 15 // Below 20%
        )
        
        // Simulate receiving data with low battery
        viewModel.javaClass.getDeclaredMethod("handleInertialDataReceived", InertialReading::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, lowBatteryReading)
        
        assertTrue("Should detect low battery", viewModel.isBatteryLow())
    }

    @Test
    fun `clearError should reset error message to null`() {
        // Set an error message first (using reflection to access private field)
        val errorField = viewModel.javaClass.getDeclaredField("_errorMessage")
        errorField.isAccessible = true
        val errorStateFlow = errorField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<String?>
        errorStateFlow.value = "Test error"
        
        // Verify error is set
        assertEquals("Test error", viewModel.errorMessage.value)
        
        // Clear error
        viewModel.clearError()
        
        // Verify error is cleared
        assertNull("Error should be cleared", viewModel.errorMessage.value)
    }

    @Test
    fun `startCollection should fail when storage is not available`() = runTest {
        // Mock storage unavailable
        every { mockDataRepository.isStorageAvailable() } returns false
        
        viewModel.startCollection()
        
        // Wait a bit for the coroutine to complete
        kotlinx.coroutines.delay(100)
        
        // Should set error message and not start collecting
        assertNotNull("Should have error message when storage unavailable", viewModel.errorMessage.value)
        assertFalse("Should not be collecting when storage unavailable", viewModel.isCollecting.value)
    }

    @Test
    fun `getCurrentBatteryLevel should return battery from latest inertial data`() = runTest {
        val testReading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            accelerometer = Vector3D(0f, 0f, 9.8f),
            gyroscope = Vector3D(0f, 0f, 0f),
            magnetometer = Vector3D(0f, 0f, 0f),
            batteryLevel = 85
        )
        
        // Simulate receiving inertial data
        viewModel.javaClass.getDeclaredMethod("handleInertialDataReceived", InertialReading::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, testReading)
        
        assertEquals("Should return battery level from inertial data", 85, viewModel.getCurrentBatteryLevel())
    }

    @Test
    fun `getCurrentBatteryLevel should return battery from latest biometric data`() = runTest {
        val testReading = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            heartRate = 72,
            stepCount = 1000,
            calories = 50.5f,
            skinTemperature = 36.5f,
            batteryLevel = 75
        )
        
        // Simulate receiving biometric data
        viewModel.javaClass.getDeclaredMethod("handleBiometricDataReceived", BiometricReading::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, testReading)
        
        assertEquals("Should return battery level from biometric data", 75, viewModel.getCurrentBatteryLevel())
    }

    @Test
    fun `data handlers should update real-time data and increment count`() = runTest {
        val inertialReading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            accelerometer = Vector3D(1f, 2f, 3f),
            gyroscope = Vector3D(0.1f, 0.2f, 0.3f),
            magnetometer = Vector3D(10f, 20f, 30f),
            batteryLevel = 80
        )
        
        val biometricReading = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            heartRate = 75,
            stepCount = 1500,
            calories = 60.0f,
            skinTemperature = 36.8f,
            batteryLevel = 80
        )
        
        // Initial count should be 0
        assertEquals("Initial count should be 0", 0, viewModel.dataPointsCount.value)
        
        // Handle inertial data
        viewModel.javaClass.getDeclaredMethod("handleInertialDataReceived", InertialReading::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, inertialReading)
        
        // Verify inertial data is updated and count incremented
        assertEquals("Inertial data should be updated", inertialReading, viewModel.realTimeInertialData.value)
        assertEquals("Count should be incremented", 1, viewModel.dataPointsCount.value)
        
        // Handle biometric data
        viewModel.javaClass.getDeclaredMethod("handleBiometricDataReceived", BiometricReading::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, biometricReading)
        
        // Verify biometric data is updated and count incremented again
        assertEquals("Biometric data should be updated", biometricReading, viewModel.realTimeBiometricData.value)
        assertEquals("Count should be incremented again", 2, viewModel.dataPointsCount.value)
        
        // Verify repository methods were called
        coVerify { mockDataRepository.appendInertialData("test_session", inertialReading) }
        coVerify { mockDataRepository.appendBiometricData("test_session", biometricReading) }
    }
}