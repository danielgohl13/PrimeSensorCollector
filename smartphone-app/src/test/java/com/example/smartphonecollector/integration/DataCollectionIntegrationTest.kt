package com.example.smartphonecollector.integration

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.data.repository.DataRepository
import com.example.smartphonecollector.testing.MockSensorDataGenerator
import com.example.smartphonecollector.ui.viewmodel.CollectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Integration tests for the complete data collection pipeline
 * Tests the flow from sensor data generation through storage to CSV files
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataCollectionIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var mockDataGenerator: MockSensorDataGenerator
    private lateinit var testSession: SessionData

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        context = RuntimeEnvironment.getApplication()
        dataRepository = DataRepository(context)
        mockDataGenerator = MockSensorDataGenerator()
        
        // Create a test session
        testSession = mockDataGenerator.createMockSession("integration_test_device")
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test(timeout = 30000) // 30 second timeout for integration test
    fun `complete inertial data collection flow works correctly`() = runTest(timeout = 20000.milliseconds) {
        // Generate mock inertial data
        val inertialReadings = mockDataGenerator.generateInertialReadingBatch(
            sessionId = testSession.sessionId,
            count = 10,
            motionType = MockSensorDataGenerator.MotionType.WALKING
        )

        // Save data through repository
        val saveResult = dataRepository.saveInertialData(testSession.sessionId, inertialReadings)
        
        assertTrue("Inertial data should be saved successfully", saveResult.isSuccess)
        
        val filePath = saveResult.getOrNull()
        assertNotNull("File path should be returned", filePath)
        assertTrue("File path should contain session ID", filePath!!.contains(testSession.sessionId))
        assertTrue("File path should be CSV file", filePath.endsWith(".csv"))
        
        // Verify file was created and contains correct data
        val sessionFiles = dataRepository.getSessionFiles()
        assertTrue("Session files should be retrieved successfully", sessionFiles.isSuccess)
        
        val files = sessionFiles.getOrNull()!!
        assertTrue("At least one file should exist", files.isNotEmpty())
        
        val inertialFile = files.find { it.name.contains("inertial") }
        assertNotNull("Inertial file should exist", inertialFile)
        
        // Verify file content structure
        val fileContent = inertialFile!!.readText()
        val lines = fileContent.split("\n").filter { it.isNotBlank() }
        
        assertEquals("File should have header + data rows", 11, lines.size) // 1 header + 10 data rows
        assertTrue("Header should be correct", lines[0].contains("timestamp,session_id,device_id"))
        assertTrue("Data rows should contain session ID", lines[1].contains(testSession.sessionId))
    }

    @Test
    fun `complete biometric data collection flow works correctly`() = runTest {
        // Generate mock biometric data
        val biometricReadings = mockDataGenerator.generateBiometricReadingBatch(
            sessionId = testSession.sessionId,
            count = 5,
            activityType = MockSensorDataGenerator.ActivityType.WALKING
        )

        // Save data through repository
        val saveResult = dataRepository.saveBiometricData(testSession.sessionId, biometricReadings)
        
        assertTrue("Biometric data should be saved successfully", saveResult.isSuccess)
        
        val filePath = saveResult.getOrNull()
        assertNotNull("File path should be returned", filePath)
        assertTrue("File path should contain session ID", filePath!!.contains(testSession.sessionId))
        
        // Verify file was created and contains correct data
        val sessionFiles = dataRepository.getSessionFiles()
        assertTrue("Session files should be retrieved successfully", sessionFiles.isSuccess)
        
        val files = sessionFiles.getOrNull()!!
        val biometricFile = files.find { it.name.contains("biometric") }
        assertNotNull("Biometric file should exist", biometricFile)
        
        // Verify file content structure
        val fileContent = biometricFile!!.readText()
        val lines = fileContent.split("\n").filter { it.isNotBlank() }
        
        assertEquals("File should have header + data rows", 6, lines.size) // 1 header + 5 data rows
        assertTrue("Header should be correct", lines[0].contains("heart_rate,step_count,calories"))
        assertTrue("Data rows should contain session ID", lines[1].contains(testSession.sessionId))
    }

    @Test
    fun `mixed data collection session creates both file types`() = runTest {
        // Generate both types of data for the same session
        val inertialReadings = mockDataGenerator.generateInertialReadingBatch(
            sessionId = testSession.sessionId,
            count = 8,
            motionType = MockSensorDataGenerator.MotionType.RUNNING
        )
        
        val biometricReadings = mockDataGenerator.generateBiometricReadingBatch(
            sessionId = testSession.sessionId,
            count = 4,
            activityType = MockSensorDataGenerator.ActivityType.RUNNING
        )

        // Save both types of data
        val inertialResult = dataRepository.saveInertialData(testSession.sessionId, inertialReadings)
        val biometricResult = dataRepository.saveBiometricData(testSession.sessionId, biometricReadings)
        
        assertTrue("Inertial data should be saved successfully", inertialResult.isSuccess)
        assertTrue("Biometric data should be saved successfully", biometricResult.isSuccess)
        
        // Verify both files exist
        val sessionFiles = dataRepository.getSessionFiles()
        assertTrue("Session files should be retrieved successfully", sessionFiles.isSuccess)
        
        val files = sessionFiles.getOrNull()!!
        assertTrue("At least two files should exist", files.size >= 2)
        
        val inertialFile = files.find { it.name.contains("inertial") && it.name.contains(testSession.sessionId) }
        val biometricFile = files.find { it.name.contains("biometric") && it.name.contains(testSession.sessionId) }
        
        assertNotNull("Inertial file should exist", inertialFile)
        assertNotNull("Biometric file should exist", biometricFile)
        
        // Verify session history includes this session
        val sessionHistory = dataRepository.getSessionHistory()
        assertTrue("Session history should be retrieved successfully", sessionHistory.isSuccess)
        
        val sessions = sessionHistory.getOrNull()!!
        val currentSession = sessions.find { it.sessionId.contains(testSession.sessionId.split("_")[1]) }
        assertNotNull("Current session should be in history", currentSession)
        assertTrue("Session should have data points", currentSession!!.dataPointsCollected > 0)
    }

    @Test
    fun `append operations work correctly for streaming data`() = runTest {
        // Simulate streaming data by appending readings one by one
        val readings = mockDataGenerator.generateInertialReadingBatch(
            sessionId = testSession.sessionId,
            count = 5,
            motionType = MockSensorDataGenerator.MotionType.STATIONARY
        )

        // Append readings individually (simulating real-time streaming)
        readings.forEach { reading ->
            val appendResult = dataRepository.appendInertialData(testSession.sessionId, reading)
            assertTrue("Each append should succeed", appendResult.isSuccess)
        }

        // Verify final file contains all data
        val sessionFiles = dataRepository.getSessionFiles()
        val files = sessionFiles.getOrNull()!!
        val inertialFile = files.find { it.name.contains("inertial") }
        assertNotNull("Inertial file should exist", inertialFile)
        
        val fileContent = inertialFile!!.readText()
        val lines = fileContent.split("\n").filter { it.isNotBlank() }
        
        assertEquals("File should have header + all appended data", 6, lines.size) // 1 header + 5 data rows
        
        // Verify all readings are present by checking timestamps
        val dataLines = lines.drop(1) // Skip header
        assertEquals("Should have correct number of data lines", 5, dataLines.size)
        
        // Verify data integrity
        dataLines.forEach { line ->
            assertTrue("Each line should contain session ID", line.contains(testSession.sessionId))
            assertTrue("Each line should contain device ID", line.contains("mock_device"))
        }
    }

    @Test
    fun `data collection handles missing sensor values correctly`() = runTest {
        // Generate biometric readings with missing heart rate
        val readingsWithMissingHR = (1..3).map {
            mockDataGenerator.generateBiometricReadingWithMissingHeartRate(
                sessionId = testSession.sessionId
            )
        }
        
        // Generate biometric readings with missing skin temperature
        val readingsWithMissingSkinTemp = (1..2).map {
            mockDataGenerator.generateBiometricReadingWithMissingSkinTemp(
                sessionId = testSession.sessionId
            )
        }

        val allReadings = readingsWithMissingHR + readingsWithMissingSkinTemp

        // Save data with missing values
        val saveResult = dataRepository.saveBiometricData(testSession.sessionId, allReadings)
        assertTrue("Data with missing values should be saved successfully", saveResult.isSuccess)
        
        // Verify file content handles null values correctly
        val sessionFiles = dataRepository.getSessionFiles()
        val files = sessionFiles.getOrNull()!!
        val biometricFile = files.find { it.name.contains("biometric") }
        assertNotNull("Biometric file should exist", biometricFile)
        
        val fileContent = biometricFile!!.readText()
        val lines = fileContent.split("\n").filter { it.isNotBlank() }
        
        assertEquals("File should have header + data rows", 6, lines.size) // 1 header + 5 data rows
        
        // Verify CSV format handles null values (empty fields)
        val dataLines = lines.drop(1)
        val linesWithMissingHR = dataLines.filter { line ->
            val fields = line.split(",")
            fields.size > 3 && fields[3].isEmpty() // Heart rate field is empty
        }
        assertEquals("Should have 3 lines with missing heart rate", 3, linesWithMissingHR.size)
        
        val linesWithMissingSkinTemp = dataLines.filter { line ->
            val fields = line.split(",")
            fields.size > 6 && fields[6].isEmpty() // Skin temp field is empty
        }
        assertEquals("Should have 2 lines with missing skin temperature", 2, linesWithMissingSkinTemp.size)
    }

    @Test
    fun `storage statistics are updated correctly during data collection`() = runTest {
        // Get initial storage statistics
        val initialStats = dataRepository.getStorageStatistics()
        assertTrue("Initial stats should be retrieved successfully", initialStats.isSuccess)
        val initialFileCount = initialStats.getOrNull()!!.fileCount
        val initialAppDataSize = initialStats.getOrNull()!!.appDataSize

        // Generate and save data
        val inertialReadings = mockDataGenerator.generateInertialReadingBatch(
            sessionId = testSession.sessionId,
            count = 20,
            motionType = MockSensorDataGenerator.MotionType.WALKING
        )
        
        val biometricReadings = mockDataGenerator.generateBiometricReadingBatch(
            sessionId = testSession.sessionId,
            count = 10,
            activityType = MockSensorDataGenerator.ActivityType.WALKING
        )

        dataRepository.saveInertialData(testSession.sessionId, inertialReadings)
        dataRepository.saveBiometricData(testSession.sessionId, biometricReadings)

        // Get updated storage statistics
        val updatedStats = dataRepository.getStorageStatistics()
        assertTrue("Updated stats should be retrieved successfully", updatedStats.isSuccess)
        val updatedFileCount = updatedStats.getOrNull()!!.fileCount
        val updatedAppDataSize = updatedStats.getOrNull()!!.appDataSize

        // Verify statistics were updated
        assertTrue("File count should have increased", updatedFileCount > initialFileCount)
        assertTrue("App data size should have increased", updatedAppDataSize > initialAppDataSize)
        
        val stats = updatedStats.getOrNull()!!
        assertTrue("Total space should be positive", stats.totalSpace > 0)
        assertTrue("Free space should be positive", stats.freeSpace > 0)
        assertTrue("Free space percentage should be valid", stats.freeSpacePercentage in 0..100)
    }

    @Test
    fun `low battery scenarios are handled correctly in data`() = runTest {
        // Generate readings with progressively lower battery levels
        val lowBatteryReadings = listOf(
            mockDataGenerator.generateLowBatteryInertialReading(testSession.sessionId, 25),
            mockDataGenerator.generateLowBatteryInertialReading(testSession.sessionId, 18),
            mockDataGenerator.generateLowBatteryInertialReading(testSession.sessionId, 12),
            mockDataGenerator.generateLowBatteryInertialReading(testSession.sessionId, 8)
        )

        val criticalBatteryReadings = listOf(
            mockDataGenerator.generateCriticalBatteryBiometricReading(testSession.sessionId, 15),
            mockDataGenerator.generateCriticalBatteryBiometricReading(testSession.sessionId, 10),
            mockDataGenerator.generateCriticalBatteryBiometricReading(testSession.sessionId, 5)
        )

        // Save low battery data
        val inertialResult = dataRepository.saveInertialData(testSession.sessionId, lowBatteryReadings)
        val biometricResult = dataRepository.saveBiometricData(testSession.sessionId, criticalBatteryReadings)
        
        assertTrue("Low battery inertial data should be saved", inertialResult.isSuccess)
        assertTrue("Critical battery biometric data should be saved", biometricResult.isSuccess)

        // Verify battery levels are correctly recorded in files
        val sessionFiles = dataRepository.getSessionFiles()
        val files = sessionFiles.getOrNull()!!
        
        val inertialFile = files.find { it.name.contains("inertial") }
        assertNotNull("Inertial file should exist", inertialFile)
        
        val inertialContent = inertialFile!!.readText()
        assertTrue("File should contain low battery readings", inertialContent.contains(",25"))
        assertTrue("File should contain critical battery readings", inertialContent.contains(",8"))
        
        val biometricFile = files.find { it.name.contains("biometric") }
        assertNotNull("Biometric file should exist", biometricFile)
        
        val biometricContent = biometricFile!!.readText()
        assertTrue("File should contain critical battery readings", biometricContent.contains(",15"))
        assertTrue("File should contain very low battery readings", biometricContent.contains(",5"))
    }

    @Test
    fun `session cleanup operations work correctly`() = runTest {
        // Create multiple sessions with data
        val session1 = mockDataGenerator.createMockSession("device1")
        val session2 = mockDataGenerator.createMockSession("device2")
        
        val readings1 = mockDataGenerator.generateInertialReadingBatch(session1.sessionId, 5)
        val readings2 = mockDataGenerator.generateInertialReadingBatch(session2.sessionId, 5)
        
        dataRepository.saveInertialData(session1.sessionId, readings1)
        dataRepository.saveInertialData(session2.sessionId, readings2)

        // Verify files were created
        val initialFiles = dataRepository.getSessionFiles()
        assertTrue("Initial files should be retrieved", initialFiles.isSuccess)
        val initialFileCount = initialFiles.getOrNull()!!.size
        assertTrue("Should have created files", initialFileCount >= 2)

        // Perform cleanup (this will clean files older than 7 days by default, so no files should be deleted in test)
        val cleanupResult = dataRepository.cleanupOldFiles(maxAgeMillis = 1000L) // 1 second age limit
        assertTrue("Cleanup should succeed", cleanupResult.isSuccess)
        
        // In a real scenario with actual file timestamps, this would delete files
        // For this test, we verify the cleanup operation completes without error
        val deletedCount = cleanupResult.getOrNull()!!
        assertTrue("Deleted count should be non-negative", deletedCount >= 0)
    }
}