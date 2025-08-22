package com.example.smartphonecollector.integration

import android.content.Context
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.communication.WearableCommunicationService
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.data.repository.DataRepository
import com.example.smartphonecollector.ui.viewmodel.CollectionViewModel
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import java.io.File

/**
 * Complete system integration test that validates the entire data collection pipeline
 * from smartphone-wearable communication to CSV file generation
 * Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3
 */
@RunWith(RobolectricTestRunner::class)
class CompleteSystemIntegrationTest {

    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var viewModel: CollectionViewModel
    private lateinit var communicationService: WearableCommunicationService
    
    // Test configuration
    private val testSessionId = "integration_test_${System.currentTimeMillis()}"
    private val testDeviceId = "galaxy_watch_5_test"
    private val testDataPoints = 50 // Number of data points to simulate
    
    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        dataRepository = DataRepository(context)
        
        // Initialize communication service with test callbacks
        communicationService = WearableCommunicationService(
            context = context,
            onInertialDataReceived = { reading ->
                // Simulate data received from wearable
                simulateDataReceived(reading)
            },
            onBiometricDataReceived = { reading ->
                // Simulate data received from wearable
                simulateDataReceived(reading)
            }
        )
        
        viewModel = CollectionViewModel(context, dataRepository)
    }
    
    @After
    fun tearDown() {
        runBlocking {
            // Stop any active collection
            if (viewModel.isCollecting.first()) {
                viewModel.stopCollection()
                delay(1000)
            }
            
            // Clean up test files
            cleanupTestFiles()
        }
        
        communicationService.cleanup()
    }
    
    /**
     * Test complete system integration from start to finish
     * Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3
     */
    @Test
    fun testCompleteSystemIntegration() = runTest {
        println("Starting complete system integration test...")
        
        // Phase 1: Initialize and verify system state
        assertFalse("System should not be collecting initially", viewModel.isCollecting.first())
        assertNull("No session should exist initially", viewModel.sessionData.first())
        assertEquals("Connection should be disconnected initially", 
            ConnectionStatus.DISCONNECTED, viewModel.connectionStatus.first())
        
        // Phase 2: Simulate wearable connection
        println("Simulating wearable connection...")
        simulateWearableConnection()
        delay(1000)
        
        // Phase 3: Start data collection session
        println("Starting data collection session...")
        viewModel.startCollection()
        
        // Wait for collection to start
        withTimeout(10000) {
            while (!viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertTrue("Collection should be active", viewModel.isCollecting.first())
        assertNotNull("Session should be created", viewModel.sessionData.first())
        
        val session = viewModel.sessionData.first()!!
        assertTrue("Session should be active", session.isActive)
        assertNotNull("Session should have start time", session.startTime)
        println("Session started: ${session.sessionId}")
        
        // Phase 4: Simulate continuous data collection from wearable
        println("Simulating data collection from wearable...")
        val startTime = System.currentTimeMillis()
        
        // Generate and send test data in batches to simulate real-time collection
        for (batch in 1..5) {
            println("Sending data batch $batch/5...")
            
            // Send inertial data batch (10 readings per batch)
            repeat(10) { i ->
                val reading = createTestInertialReading(
                    session.sessionId, 
                    startTime + (batch - 1) * 1000 + i * 100,
                    batch * 10 + i
                )
                simulateInertialDataFromWearable(reading)
                delay(20) // Simulate 50Hz data rate
            }
            
            // Send biometric data batch (2 readings per batch)
            repeat(2) { i ->
                val reading = createTestBiometricReading(
                    session.sessionId,
                    startTime + (batch - 1) * 1000 + i * 500,
                    batch * 2 + i
                )
                simulateBiometricDataFromWearable(reading)
                delay(50)
            }
            
            // Wait between batches to simulate realistic timing
            delay(200)
        }
        
        // Wait for all data to be processed
        delay(2000)
        
        // Phase 5: Verify real-time data updates
        println("Verifying real-time data updates...")
        assertNotNull("Real-time inertial data should be available", 
            viewModel.realTimeInertialData.first())
        assertNotNull("Real-time biometric data should be available", 
            viewModel.realTimeBiometricData.first())
        
        val latestInertial = viewModel.realTimeInertialData.first()!!
        val latestBiometric = viewModel.realTimeBiometricData.first()!!
        
        assertEquals("Inertial session ID should match", session.sessionId, latestInertial.sessionId)
        assertEquals("Biometric session ID should match", session.sessionId, latestBiometric.sessionId)
        assertEquals("Device ID should match", testDeviceId, latestInertial.deviceId)
        assertEquals("Device ID should match", testDeviceId, latestBiometric.deviceId)
        
        // Phase 6: Verify data points counting
        val dataPointsCount = viewModel.dataPointsCount.first()
        assertTrue("Data points should be counted", dataPointsCount > 0)
        assertEquals("Data points count should match sent data", 60, dataPointsCount) // 50 inertial + 10 biometric
        
        // Phase 7: Test session duration tracking
        val sessionDuration = viewModel.getCurrentSessionDuration()
        assertTrue("Session duration should be positive", sessionDuration > 0)
        assertTrue("Session duration should be reasonable", sessionDuration >= 1000) // At least 1 second
        
        // Phase 8: Stop data collection
        println("Stopping data collection...")
        viewModel.stopCollection()
        
        // Wait for collection to stop
        withTimeout(10000) {
            while (viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertFalse("Collection should be stopped", viewModel.isCollecting.first())
        
        val finalSession = viewModel.sessionData.first()!!
        assertFalse("Session should be completed", finalSession.isActive)
        assertNotNull("Session should have end time", finalSession.endTime)
        assertTrue("Final session duration should be positive", finalSession.getDuration() > 0)
        
        // Phase 9: Verify CSV files were created and contain correct data
        println("Verifying CSV file creation and content...")
        val sessionFiles = dataRepository.getSessionFiles().getOrThrow()
        val inertialFiles = sessionFiles.filter { it.name.contains("inertial_${session.sessionId}") }
        val biometricFiles = sessionFiles.filter { it.name.contains("biometric_${session.sessionId}") }
        
        assertTrue("Inertial CSV file should be created", inertialFiles.isNotEmpty())
        assertTrue("Biometric CSV file should be created", biometricFiles.isNotEmpty())
        
        val inertialFile = inertialFiles.first()
        val biometricFile = biometricFiles.first()
        
        // Verify file naming convention
        assertTrue("Inertial file should follow naming convention",
            inertialFile.name.matches(Regex("inertial_${session.sessionId}_\\d{8}_\\d{6}\\.csv")))
        assertTrue("Biometric file should follow naming convention",
            biometricFile.name.matches(Regex("biometric_${session.sessionId}_\\d{8}_\\d{6}\\.csv")))
        
        // Verify file contents
        verifyInertialCsvFile(inertialFile, session.sessionId)
        verifyBiometricCsvFile(biometricFile, session.sessionId)
        
        // Phase 10: Verify data integrity across the pipeline
        println("Verifying data integrity...")
        verifyDataIntegrity(inertialFile, biometricFile, session.sessionId)
        
        println("Complete system integration test passed successfully!")
    }
    
    /**
     * Test error handling and recovery scenarios
     * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5
     */
    @Test
    fun testErrorHandlingAndRecovery() = runTest {
        println("Testing error handling and recovery...")
        
        // Test 1: Start collection without connection
        viewModel.startCollection()
        delay(1000)
        
        // Should fail to start without connection
        assertFalse("Collection should not start without connection", viewModel.isCollecting.first())
        assertNotNull("Error message should be set", viewModel.errorMessage.first())
        
        viewModel.clearError()
        
        // Test 2: Connection loss during collection
        simulateWearableConnection()
        delay(500)
        
        viewModel.startCollection()
        withTimeout(5000) {
            while (!viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        assertTrue("Collection should start with connection", viewModel.isCollecting.first())
        
        // Simulate connection loss
        simulateConnectionLoss()
        delay(1000)
        
        // Collection should continue despite connection loss
        assertTrue("Collection should continue despite connection loss", viewModel.isCollecting.first())
        
        // Test 3: Connection recovery
        simulateWearableConnection()
        delay(1000)
        
        assertEquals("Connection should be restored", 
            ConnectionStatus.CONNECTED, viewModel.connectionStatus.first())
        
        viewModel.stopCollection()
        delay(1000)
    }
    
    /**
     * Test battery level monitoring and low battery handling
     * Requirements: 5.5, 7.5
     */
    @Test
    fun testBatteryLevelMonitoring() = runTest {
        println("Testing battery level monitoring...")
        
        simulateWearableConnection()
        viewModel.startCollection()
        
        withTimeout(5000) {
            while (!viewModel.isCollecting.first()) {
                delay(100)
            }
        }
        
        val session = viewModel.sessionData.first()!!
        
        // Test normal battery level
        val normalBatteryReading = createTestInertialReading(session.sessionId, System.currentTimeMillis(), 1)
            .copy(batteryLevel = 50)
        simulateInertialDataFromWearable(normalBatteryReading)
        delay(500)
        
        assertEquals("Battery level should be updated", 50, viewModel.getCurrentBatteryLevel())
        assertFalse("Battery should not be low", viewModel.isBatteryLow())
        assertFalse("Battery should not be critically low", viewModel.isBatteryCriticallyLow())
        
        // Test low battery warning
        val lowBatteryReading = normalBatteryReading.copy(batteryLevel = 18)
        simulateInertialDataFromWearable(lowBatteryReading)
        delay(500)
        
        assertEquals("Battery level should be updated", 18, viewModel.getCurrentBatteryLevel())
        assertTrue("Battery should be low", viewModel.isBatteryLow())
        assertFalse("Battery should not be critically low yet", viewModel.isBatteryCriticallyLow())
        
        // Test critical battery level (should stop collection)
        val criticalBatteryReading = normalBatteryReading.copy(batteryLevel = 12)
        simulateInertialDataFromWearable(criticalBatteryReading)
        delay(2000) // Wait for automatic stop
        
        assertEquals("Battery level should be updated", 12, viewModel.getCurrentBatteryLevel())
        assertTrue("Battery should be critically low", viewModel.isBatteryCriticallyLow())
        assertFalse("Collection should be stopped automatically", viewModel.isCollecting.first())
        assertNotNull("Error message should indicate low battery stop", viewModel.errorMessage.first())
    }
    
    /**
     * Test storage monitoring and cleanup
     * Requirements: 3.5
     */
    @Test
    fun testStorageMonitoringAndCleanup() = runTest {
        println("Testing storage monitoring and cleanup...")
        
        // Verify storage is available
        val (isAvailable, freeSpace) = viewModel.getStorageInfo()
        assertTrue("Storage should be available", isAvailable)
        assertTrue("Free space should be positive", freeSpace > 0)
        
        // Test storage statistics
        viewModel.getStorageStatistics()
        delay(1000) // Wait for statistics to be processed
        
        // Test cleanup functionality
        viewModel.performCleanup()
        delay(1000) // Wait for cleanup to complete
        
        // Should complete without errors (error message would be set if cleanup failed)
        println("Storage monitoring and cleanup test completed")
    }
    
    // Helper methods for simulation
    
    private fun simulateWearableConnection() {
        // Simulate successful wearable connection
        runBlocking {
            // This would normally be handled by the communication service
            // For testing, we simulate the connection status change
        }
    }
    
    private fun simulateConnectionLoss() {
        // Simulate connection loss to wearable
        runBlocking {
            // This would normally be detected by the communication service
        }
    }
    
    private fun simulateDataReceived(reading: Any) {
        // This method would be called by the communication service
        // when data is received from the wearable
    }
    
    private fun simulateInertialDataFromWearable(reading: InertialReading) {
        // Simulate inertial data received from wearable through communication service
        runBlocking {
            try {
                // Use reflection to call the private method for testing
                val method = viewModel.javaClass.getDeclaredMethod("handleInertialDataReceived", InertialReading::class.java)
                method.isAccessible = true
                method.invoke(viewModel, reading)
            } catch (e: Exception) {
                // If reflection fails, we can't simulate the data reception
                println("Warning: Could not simulate inertial data reception: ${e.message}")
            }
        }
    }
    
    private fun simulateBiometricDataFromWearable(reading: BiometricReading) {
        // Simulate biometric data received from wearable through communication service
        runBlocking {
            try {
                // Use reflection to call the private method for testing
                val method = viewModel.javaClass.getDeclaredMethod("handleBiometricDataReceived", BiometricReading::class.java)
                method.isAccessible = true
                method.invoke(viewModel, reading)
            } catch (e: Exception) {
                // If reflection fails, we can't simulate the data reception
                println("Warning: Could not simulate biometric data reception: ${e.message}")
            }
        }
    }
    
    private fun createTestInertialReading(sessionId: String, timestamp: Long, index: Int): InertialReading {
        return InertialReading(
            timestamp = timestamp,
            sessionId = sessionId,
            deviceId = testDeviceId,
            accelerometer = Vector3D(
                x = index * 0.1f,
                y = index * 0.2f,
                z = 9.8f + index * 0.05f
            ),
            gyroscope = Vector3D(
                x = index * 0.01f,
                y = index * 0.02f,
                z = index * 0.03f
            ),
            magnetometer = Vector3D(
                x = 45.0f + index * 0.5f,
                y = -12.0f + index * 0.3f,
                z = 8.0f + index * 0.2f
            ),
            batteryLevel = 100 - (index / 10) // Gradually decrease battery
        )
    }
    
    private fun createTestBiometricReading(sessionId: String, timestamp: Long, index: Int): BiometricReading {
        return BiometricReading(
            timestamp = timestamp,
            sessionId = sessionId,
            deviceId = testDeviceId,
            heartRate = 70 + (index % 20), // Vary heart rate
            stepCount = 1000 + index * 10,
            calories = 40.0f + index * 2.5f,
            skinTemperature = 32.0f + index * 0.1f,
            batteryLevel = 100 - (index / 5) // Gradually decrease battery
        )
    }
    
    private fun verifyInertialCsvFile(file: File, sessionId: String) {
        assertTrue("Inertial CSV file should exist", file.exists())
        assertTrue("Inertial CSV file should not be empty", file.length() > 0)
        
        val lines = file.readLines()
        assertTrue("CSV should have header and data lines", lines.size > 1)
        
        // Verify header
        val header = lines[0]
        val expectedColumns = listOf("timestamp", "session_id", "device_id", "accel_x", "accel_y", "accel_z",
            "gyro_x", "gyro_y", "gyro_z", "mag_x", "mag_y", "mag_z", "battery_level")
        
        expectedColumns.forEach { column ->
            assertTrue("Header should contain $column", header.contains(column))
        }
        
        // Verify data rows contain session ID and device ID
        lines.drop(1).forEach { line ->
            assertTrue("Data line should contain session ID", line.contains(sessionId))
            assertTrue("Data line should contain device ID", line.contains(testDeviceId))
        }
        
        println("Inertial CSV file verified: ${lines.size - 1} data rows")
    }
    
    private fun verifyBiometricCsvFile(file: File, sessionId: String) {
        assertTrue("Biometric CSV file should exist", file.exists())
        assertTrue("Biometric CSV file should not be empty", file.length() > 0)
        
        val lines = file.readLines()
        assertTrue("CSV should have header and data lines", lines.size > 1)
        
        // Verify header
        val header = lines[0]
        val expectedColumns = listOf("timestamp", "session_id", "device_id", "heart_rate", 
            "step_count", "calories", "skin_temp", "battery_level")
        
        expectedColumns.forEach { column ->
            assertTrue("Header should contain $column", header.contains(column))
        }
        
        // Verify data rows contain session ID and device ID
        lines.drop(1).forEach { line ->
            assertTrue("Data line should contain session ID", line.contains(sessionId))
            assertTrue("Data line should contain device ID", line.contains(testDeviceId))
        }
        
        println("Biometric CSV file verified: ${lines.size - 1} data rows")
    }
    
    private fun verifyDataIntegrity(inertialFile: File, biometricFile: File, sessionId: String) {
        // Verify that all data points were properly saved
        val inertialLines = inertialFile.readLines().drop(1) // Skip header
        val biometricLines = biometricFile.readLines().drop(1) // Skip header
        
        assertEquals("Should have 50 inertial data points", 50, inertialLines.size)
        assertEquals("Should have 10 biometric data points", 10, biometricLines.size)
        
        // Verify timestamps are in chronological order
        val inertialTimestamps = inertialLines.map { line ->
            line.split(",")[0].toLong()
        }
        val biometricTimestamps = biometricLines.map { line ->
            line.split(",")[0].toLong()
        }
        
        assertTrue("Inertial timestamps should be in order", 
            inertialTimestamps.zipWithNext().all { (a, b) -> a <= b })
        assertTrue("Biometric timestamps should be in order", 
            biometricTimestamps.zipWithNext().all { (a, b) -> a <= b })
        
        // Verify all data contains correct session and device IDs
        inertialLines.forEach { line ->
            val parts = line.split(",")
            assertEquals("Session ID should match", sessionId, parts[1])
            assertEquals("Device ID should match", testDeviceId, parts[2])
        }
        
        biometricLines.forEach { line ->
            val parts = line.split(",")
            assertEquals("Session ID should match", sessionId, parts[1])
            assertEquals("Device ID should match", testDeviceId, parts[2])
        }
        
        println("Data integrity verification completed successfully")
    }
    
    private suspend fun cleanupTestFiles() {
        try {
            val sessionFiles = dataRepository.getSessionFiles().getOrNull() ?: return
            sessionFiles.filter { 
                it.name.contains("integration_test") || it.name.contains("test_session")
            }.forEach { file ->
                file.delete()
                println("Cleaned up test file: ${file.name}")
            }
        } catch (e: Exception) {
            println("Warning: Could not clean up test files: ${e.message}")
        }
    }
}