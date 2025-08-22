package com.example.smartphonecollector.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.communication.WearableCommunicationService
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.data.repository.DataRepository
import com.example.smartphonecollector.ui.viewmodel.CollectionViewModel
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
import java.io.File

/**
 * Integration test for the complete wearable data collection system
 * Tests the end-to-end pipeline from sensor data to CSV files
 * Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3
 */
@RunWith(AndroidJUnit4::class)
class SystemIntegrationTest {

    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var viewModel: CollectionViewModel
    private lateinit var communicationService: WearableCommunicationService
    
    // Test data
    private val testSessionId = "test_session_${System.currentTimeMillis()}"
    private val testDeviceId = "test_galaxy_watch_5"
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataRepository = DataRepository(context)
        
        // Create mock communication service for testing
        communicationService = createMockCommunicationService()
        
        // Initialize ViewModel with test dependencies
        viewModel = CollectionViewModel(context, dataRepository)
    }
    
    @After
    fun tearDown() {
        // Clean up test files
        runBlocking {
            cleanupTestFiles()
        }
        
        // Clean up communication service
        communicationService.cleanup()
    }
    
    /**
     * Test complete data collection pipeline from start to CSV files
     * Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 6.1, 6.2, 6.3
     */
    @Test
    fun testCompleteDataCollectionPipeline() = runTest {
        // Step 1: Start collection session
        viewModel.startCollection()
        
        // Wait for collection to start
        withTimeout(5000) {
            while (!viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertTrue("Collection should be active", viewModel.isCollecting.first())
        assertNotNull("Session data should be created", viewModel.sessionData.first())
        
        val sessionData = viewModel.sessionData.first()!!
        assertTrue("Session should be active", sessionData.isActive)
        
        // Step 2: Simulate incoming sensor data from wearable
        val testInertialReadings = generateTestInertialData(sessionData.sessionId, 10)
        val testBiometricReadings = generateTestBiometricData(sessionData.sessionId, 5)
        
        // Send test data through communication service
        testInertialReadings.forEach { reading ->
            simulateInertialDataReceived(reading)
            delay(50) // Simulate realistic timing
        }
        
        testBiometricReadings.forEach { reading ->
            simulateBiometricDataReceived(reading)
            delay(100) // Simulate realistic timing
        }
        
        // Wait for data to be processed
        delay(1000)
        
        // Step 3: Verify real-time data updates
        assertNotNull("Real-time inertial data should be available", 
            viewModel.realTimeInertialData.first())
        assertNotNull("Real-time biometric data should be available", 
            viewModel.realTimeBiometricData.first())
        
        val realTimeInertial = viewModel.realTimeInertialData.first()!!
        val realTimeBiometric = viewModel.realTimeBiometricData.first()!!
        
        assertEquals("Session ID should match", sessionData.sessionId, realTimeInertial.sessionId)
        assertEquals("Device ID should match", testDeviceId, realTimeInertial.deviceId)
        assertEquals("Session ID should match", sessionData.sessionId, realTimeBiometric.sessionId)
        assertEquals("Device ID should match", testDeviceId, realTimeBiometric.deviceId)
        
        // Step 4: Verify data points count
        assertTrue("Data points should be counted", viewModel.dataPointsCount.first() > 0)
        assertEquals("Data points count should match sent data", 
            testInertialReadings.size + testBiometricReadings.size, 
            viewModel.dataPointsCount.first())
        
        // Step 5: Stop collection
        viewModel.stopCollection()
        
        // Wait for collection to stop
        withTimeout(5000) {
            while (viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertFalse("Collection should be stopped", viewModel.isCollecting.first())
        
        val finalSessionData = viewModel.sessionData.first()!!
        assertFalse("Session should be completed", finalSessionData.isActive)
        assertNotNull("Session should have end time", finalSessionData.endTime)
        assertTrue("Session duration should be positive", finalSessionData.getDuration() > 0)
        
        // Step 6: Verify CSV files were created
        val sessionFiles = dataRepository.getSessionFiles().getOrThrow()
        val inertialFiles = sessionFiles.filter { it.name.contains("inertial_${sessionData.sessionId}") }
        val biometricFiles = sessionFiles.filter { it.name.contains("biometric_${sessionData.sessionId}") }
        
        assertTrue("Inertial CSV file should be created", inertialFiles.isNotEmpty())
        assertTrue("Biometric CSV file should be created", biometricFiles.isNotEmpty())
        
        // Step 7: Verify CSV file contents
        val inertialFile = inertialFiles.first()
        val biometricFile = biometricFiles.first()
        
        verifyInertialCsvContent(inertialFile, testInertialReadings)
        verifyBiometricCsvContent(biometricFile, testBiometricReadings)
    }
    
    /**
     * Test real-time data visualization updates
     * Requirements: 5.1, 5.2, 5.3
     */
    @Test
    fun testRealTimeDataVisualization() = runTest {
        // Start collection
        viewModel.startCollection()
        
        withTimeout(5000) {
            while (!viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        val sessionData = viewModel.sessionData.first()!!
        
        // Send test data with specific values for verification
        val testReading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionData.sessionId,
            deviceId = testDeviceId,
            accelerometer = Vector3D(1.0f, 2.0f, 3.0f),
            gyroscope = Vector3D(0.1f, 0.2f, 0.3f),
            magnetometer = Vector3D(10.0f, 20.0f, 30.0f),
            batteryLevel = 85
        )
        
        simulateInertialDataReceived(testReading)
        
        // Wait for data to be processed
        delay(500)
        
        // Verify real-time data matches sent data
        val realTimeData = viewModel.realTimeInertialData.first()!!
        assertEquals("Accelerometer X should match", 1.0f, realTimeData.accelerometer.x, 0.001f)
        assertEquals("Accelerometer Y should match", 2.0f, realTimeData.accelerometer.y, 0.001f)
        assertEquals("Accelerometer Z should match", 3.0f, realTimeData.accelerometer.z, 0.001f)
        assertEquals("Battery level should match", 85, realTimeData.batteryLevel)
        
        // Test biometric data
        val biometricReading = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionData.sessionId,
            deviceId = testDeviceId,
            heartRate = 72,
            stepCount = 1500,
            calories = 45.5f,
            skinTemperature = null,
            batteryLevel = 85
        )
        
        simulateBiometricDataReceived(biometricReading)
        delay(500)
        
        val realTimeBiometric = viewModel.realTimeBiometricData.first()!!
        assertEquals("Heart rate should match", 72, realTimeBiometric.heartRate)
        assertEquals("Step count should match", 1500, realTimeBiometric.stepCount)
        assertEquals("Calories should match", 45.5f, realTimeBiometric.calories, 0.1f)
        
        viewModel.stopCollection()
    }
    
    /**
     * Test session management functionality
     * Requirements: 1.1, 1.2, 1.3
     */
    @Test
    fun testSessionManagement() = runTest {
        // Test session creation
        assertFalse("Should not be collecting initially", viewModel.isCollecting.first())
        assertNull("Should not have session data initially", viewModel.sessionData.first())
        
        // Start collection
        viewModel.startCollection()
        
        withTimeout(5000) {
            while (!viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertTrue("Should be collecting after start", viewModel.isCollecting.first())
        
        val sessionData = viewModel.sessionData.first()!!
        assertNotNull("Session ID should be generated", sessionData.sessionId)
        assertTrue("Session should be active", sessionData.isActive)
        assertNotNull("Session should have start time", sessionData.startTime)
        assertNull("Session should not have end time while active", sessionData.endTime)
        
        // Test session duration tracking
        delay(1000) // Wait 1 second
        val duration = viewModel.getCurrentSessionDuration()
        assertTrue("Session duration should be positive", duration > 0)
        assertTrue("Session duration should be reasonable", duration >= 1000) // At least 1 second
        
        // Stop collection
        viewModel.stopCollection()
        
        withTimeout(5000) {
            while (viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertFalse("Should not be collecting after stop", viewModel.isCollecting.first())
        
        val finalSessionData = viewModel.sessionData.first()!!
        assertFalse("Session should not be active after stop", finalSessionData.isActive)
        assertNotNull("Session should have end time after stop", finalSessionData.endTime)
        assertTrue("Final session duration should be positive", finalSessionData.getDuration() > 0)
    }
    
    /**
     * Test CSV file format and data integrity
     * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5
     */
    @Test
    fun testCsvFileFormatAndIntegrity() = runTest {
        // Create test session
        val session = dataRepository.createSession(testDeviceId)
        
        // Create test data
        val inertialReadings = generateTestInertialData(session.sessionId, 5)
        val biometricReadings = generateTestBiometricData(session.sessionId, 3)
        
        // Save data to CSV files
        val inertialResult = dataRepository.saveInertialData(session.sessionId, inertialReadings)
        val biometricResult = dataRepository.saveBiometricData(session.sessionId, biometricReadings)
        
        assertTrue("Inertial data should be saved successfully", inertialResult.isSuccess)
        assertTrue("Biometric data should be saved successfully", biometricResult.isSuccess)
        
        val inertialFilePath = inertialResult.getOrThrow()
        val biometricFilePath = biometricResult.getOrThrow()
        
        // Verify file names follow the required format
        assertTrue("Inertial file should follow naming convention", 
            File(inertialFilePath).name.matches(Regex("inertial_${session.sessionId}_\\d{8}_\\d{6}\\.csv")))
        assertTrue("Biometric file should follow naming convention", 
            File(biometricFilePath).name.matches(Regex("biometric_${session.sessionId}_\\d{8}_\\d{6}\\.csv")))
        
        // Verify file contents
        verifyInertialCsvContent(File(inertialFilePath), inertialReadings)
        verifyBiometricCsvContent(File(biometricFilePath), biometricReadings)
        
        // Verify files are stored in correct directory
        val expectedDir = "Documents/WearableDataCollector"
        assertTrue("Files should be in correct directory", 
            inertialFilePath.contains(expectedDir))
        assertTrue("Files should be in correct directory", 
            biometricFilePath.contains(expectedDir))
    }
    
    /**
     * Test connection status monitoring and error handling
     * Requirements: 8.1, 8.5
     */
    @Test
    fun testConnectionStatusAndErrorHandling() = runTest {
        // Test initial connection status
        assertEquals("Initial connection should be disconnected", 
            ConnectionStatus.DISCONNECTED, viewModel.connectionStatus.first())
        
        // Simulate connection establishment
        simulateConnectionStatusChange(ConnectionStatus.CONNECTED)
        delay(100)
        
        assertEquals("Connection status should update to connected", 
            ConnectionStatus.CONNECTED, viewModel.connectionStatus.first())
        
        // Test starting collection with connection
        viewModel.startCollection()
        
        withTimeout(5000) {
            while (!viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertTrue("Collection should start when connected", viewModel.isCollecting.first())
        
        // Simulate connection loss during collection
        simulateConnectionStatusChange(ConnectionStatus.DISCONNECTED)
        delay(100)
        
        assertEquals("Connection status should update to disconnected", 
            ConnectionStatus.DISCONNECTED, viewModel.connectionStatus.first())
        
        // Collection should continue even with connection loss
        assertTrue("Collection should continue despite connection loss", 
            viewModel.isCollecting.first())
        
        // Simulate connection restoration
        simulateConnectionStatusChange(ConnectionStatus.CONNECTED)
        delay(100)
        
        assertEquals("Connection status should restore", 
            ConnectionStatus.CONNECTED, viewModel.connectionStatus.first())
        
        viewModel.stopCollection()
    }
    
    // Helper methods
    
    private fun createMockCommunicationService(): WearableCommunicationService {
        return WearableCommunicationService(
            context = context,
            onInertialDataReceived = { reading ->
                // This will be called by the mock service
            },
            onBiometricDataReceived = { reading ->
                // This will be called by the mock service
            }
        )
    }
    
    private fun generateTestInertialData(sessionId: String, count: Int): List<InertialReading> {
        return (1..count).map { i ->
            InertialReading(
                timestamp = System.currentTimeMillis() + i * 100,
                sessionId = sessionId,
                deviceId = testDeviceId,
                accelerometer = Vector3D(i.toFloat(), i * 2.0f, i * 3.0f),
                gyroscope = Vector3D(i * 0.1f, i * 0.2f, i * 0.3f),
                magnetometer = Vector3D(i * 10.0f, i * 20.0f, i * 30.0f),
                batteryLevel = 100 - i
            )
        }
    }
    
    private fun generateTestBiometricData(sessionId: String, count: Int): List<BiometricReading> {
        return (1..count).map { i ->
            BiometricReading(
                timestamp = System.currentTimeMillis() + i * 200,
                sessionId = sessionId,
                deviceId = testDeviceId,
                heartRate = 70 + i,
                stepCount = 1000 + i * 100,
                calories = 40.0f + i * 5.0f,
                skinTemperature = null,
                batteryLevel = 100 - i
            )
        }
    }
    
    private fun simulateInertialDataReceived(reading: InertialReading) {
        // Simulate data received through communication service
        runBlocking {
            // This would normally come through the communication service
            // For testing, we directly call the ViewModel's data handler
            viewModel.javaClass.getDeclaredMethod("handleInertialDataReceived", InertialReading::class.java)
                .apply { isAccessible = true }
                .invoke(viewModel, reading)
        }
    }
    
    private fun simulateBiometricDataReceived(reading: BiometricReading) {
        // Simulate data received through communication service
        runBlocking {
            // This would normally come through the communication service
            // For testing, we directly call the ViewModel's data handler
            viewModel.javaClass.getDeclaredMethod("handleBiometricDataReceived", BiometricReading::class.java)
                .apply { isAccessible = true }
                .invoke(viewModel, reading)
        }
    }
    
    private fun simulateConnectionStatusChange(status: ConnectionStatus) {
        // Simulate connection status change
        // In a real implementation, this would come from the communication service
    }
    
    private fun verifyInertialCsvContent(file: File, expectedReadings: List<InertialReading>) {
        assertTrue("Inertial CSV file should exist", file.exists())
        
        val lines = file.readLines()
        assertTrue("CSV should have header + data lines", lines.size >= expectedReadings.size + 1)
        
        // Verify header
        val header = lines[0]
        assertTrue("Header should contain timestamp", header.contains("timestamp"))
        assertTrue("Header should contain session_id", header.contains("session_id"))
        assertTrue("Header should contain device_id", header.contains("device_id"))
        assertTrue("Header should contain accelerometer data", header.contains("accel_x"))
        assertTrue("Header should contain gyroscope data", header.contains("gyro_x"))
        assertTrue("Header should contain magnetometer data", header.contains("mag_x"))
        assertTrue("Header should contain battery_level", header.contains("battery_level"))
        
        // Verify data rows
        for (i in expectedReadings.indices) {
            val dataLine = lines[i + 1] // Skip header
            val reading = expectedReadings[i]
            
            assertTrue("Data line should contain session ID", dataLine.contains(reading.sessionId))
            assertTrue("Data line should contain device ID", dataLine.contains(reading.deviceId))
            assertTrue("Data line should contain battery level", dataLine.contains(reading.batteryLevel.toString()))
        }
    }
    
    private fun verifyBiometricCsvContent(file: File, expectedReadings: List<BiometricReading>) {
        assertTrue("Biometric CSV file should exist", file.exists())
        
        val lines = file.readLines()
        assertTrue("CSV should have header + data lines", lines.size >= expectedReadings.size + 1)
        
        // Verify header
        val header = lines[0]
        assertTrue("Header should contain timestamp", header.contains("timestamp"))
        assertTrue("Header should contain session_id", header.contains("session_id"))
        assertTrue("Header should contain device_id", header.contains("device_id"))
        assertTrue("Header should contain heart_rate", header.contains("heart_rate"))
        assertTrue("Header should contain step_count", header.contains("step_count"))
        assertTrue("Header should contain calories", header.contains("calories"))
        assertTrue("Header should contain battery_level", header.contains("battery_level"))
        
        // Verify data rows
        for (i in expectedReadings.indices) {
            val dataLine = lines[i + 1] // Skip header
            val reading = expectedReadings[i]
            
            assertTrue("Data line should contain session ID", dataLine.contains(reading.sessionId))
            assertTrue("Data line should contain device ID", dataLine.contains(reading.deviceId))
            assertTrue("Data line should contain step count", dataLine.contains(reading.stepCount.toString()))
            assertTrue("Data line should contain battery level", dataLine.contains(reading.batteryLevel.toString()))
        }
    }
    
    private suspend fun cleanupTestFiles() {
        try {
            val sessionFiles = dataRepository.getSessionFiles().getOrNull() ?: return
            sessionFiles.filter { it.name.contains("test_session") }.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}