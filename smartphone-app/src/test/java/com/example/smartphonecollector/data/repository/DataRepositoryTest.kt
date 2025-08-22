package com.example.smartphonecollector.data.repository

import android.content.Context
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DataRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: DataRepository
    private lateinit var testDirectory: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repository = DataRepository(context)
        
        // Create a temporary test directory
        testDirectory = File(context.cacheDir, "test_wearable_data")
        if (testDirectory.exists()) {
            testDirectory.deleteRecursively()
        }
        testDirectory.mkdirs()
    }

    @After
    fun cleanup() {
        if (::testDirectory.isInitialized && testDirectory.exists()) {
            testDirectory.deleteRecursively()
        }
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
    fun `createSession generates unique session with correct device ID`() {
        val deviceId = "test_device_123"
        val session = repository.createSession(deviceId)
        
        assertTrue("Session ID should start with 'session_'", session.sessionId.startsWith("session_"))
        assertEquals(deviceId, session.deviceId)
        assertTrue("Session should be active", session.isActive)
        assertNull("New session should not have end time", session.endTime)
        assertEquals("New session should have zero data points", 0, session.dataPointsCollected)
    }

    @Test
    fun `createSession with default device ID`() {
        val session = repository.createSession()
        
        assertEquals("default_device", session.deviceId)
        assertTrue("Session should be active", session.isActive)
    }

    @Test
    fun `isStorageAvailable returns true when storage is accessible`() {
        val isAvailable = repository.isStorageAvailable()
        
        // In test environment, this should typically return true
        assertTrue("Storage should be available in test environment", isAvailable)
    }

    @Test
    fun `getAvailableStorageSpace returns positive value`() {
        val availableSpace = repository.getAvailableStorageSpace()
        
        assertTrue("Available storage space should be positive", availableSpace > 0)
    }

    @Test
    fun `isStorageSpaceLow returns false when sufficient space available`() {
        val isLowSpace = repository.isStorageSpaceLow()
        
        // In test environment with sufficient space, this should be false
        assertFalse("Storage space should not be low in test environment", isLowSpace)
    }

    @Test
    fun `getStorageStatistics returns valid statistics`() = runTest {
        val result = repository.getStorageStatistics()
        
        assertTrue("Storage statistics should be successful", result.isSuccess)
        
        val stats = result.getOrThrow()
        assertTrue("Total space should be positive", stats.totalSpace > 0)
        assertTrue("Free space should be positive", stats.freeSpace > 0)
        assertTrue("Used space should be non-negative", stats.usedSpace >= 0)
        assertTrue("App data size should be non-negative", stats.appDataSize >= 0)
        assertTrue("File count should be non-negative", stats.fileCount >= 0)
        assertTrue("Free space percentage should be valid", stats.freeSpacePercentage in 0..100)
    }

    @Test
    fun `getSessionFiles returns empty list when no files exist`() = runTest {
        val result = repository.getSessionFiles()
        
        assertTrue("Getting session files should be successful", result.isSuccess)
        val files = result.getOrThrow()
        assertTrue("File list should be empty initially", files.isEmpty())
    }

    @Test
    fun `getSessionHistory returns empty list when no sessions exist`() = runTest {
        val result = repository.getSessionHistory()
        
        assertTrue("Getting session history should be successful", result.isSuccess)
        val sessions = result.getOrThrow()
        assertTrue("Session history should be empty initially", sessions.isEmpty())
    }

    @Test
    fun `cleanupOldFiles returns zero when no old files exist`() = runTest {
        val result = repository.cleanupOldFiles(maxAgeMillis = 1000L)
        
        assertTrue("Cleanup should be successful", result.isSuccess)
        val deletedCount = result.getOrThrow()
        assertEquals("No files should be deleted when none exist", 0, deletedCount)
    }

    @Test
    fun `performEmergencyCleanup returns valid result`() = runTest {
        val result = repository.performEmergencyCleanup()
        
        assertTrue("Emergency cleanup should be successful", result.isSuccess)
        
        val cleanupResult = result.getOrThrow()
        assertTrue("Files deleted should be non-negative", cleanupResult.filesDeleted >= 0)
        assertTrue("Space freed should be non-negative", cleanupResult.spaceFreed >= 0)
        assertTrue("Final free space should be positive", cleanupResult.finalFreeSpace > 0)
        assertTrue("Space freed MB should be non-negative", cleanupResult.spaceFreedMB >= 0)
        assertTrue("Final free space MB should be positive", cleanupResult.finalFreeSpaceMB > 0)
    }

    // Note: File I/O tests are limited in this test environment due to Android storage restrictions
    // In a real test environment with proper file system access, we would test:
    // - saveInertialData with actual file creation
    // - saveBiometricData with actual file creation
    // - appendInertialData with file appending
    // - appendBiometricData with file appending
    // - File content validation
    // - Error handling for I/O failures

    @Test
    fun `data models integration - inertial reading CSV format`() {
        val reading = createSampleInertialReading("test_session_123")
        val csvRow = reading.toCsvRow()
        
        assertTrue("CSV row should contain session ID", csvRow.contains("test_session_123"))
        assertTrue("CSV row should contain device ID", csvRow.contains("test_device"))
        assertTrue("CSV row should contain battery level", csvRow.contains("85"))
    }

    @Test
    fun `data models integration - biometric reading CSV format`() {
        val reading = createSampleBiometricReading("test_session_456")
        val csvRow = reading.toCsvRow()
        
        assertTrue("CSV row should contain session ID", csvRow.contains("test_session_456"))
        assertTrue("CSV row should contain heart rate", csvRow.contains("72"))
        assertTrue("CSV row should contain step count", csvRow.contains("1000"))
        assertTrue("CSV row should contain calories", csvRow.contains("50.5"))
    }

    @Test
    fun `storage statistics calculation`() {
        val stats = StorageStatistics(
            totalSpace = 1000L,
            freeSpace = 300L,
            usedSpace = 700L,
            appDataSize = 50L,
            fileCount = 5,
            isLowSpace = false
        )
        
        assertEquals("Free space percentage should be calculated correctly", 30, stats.freeSpacePercentage)
        assertEquals("App data size MB should be calculated correctly", 0L, stats.appDataSizeMB) // 50 bytes = 0 MB
        assertEquals("Free space MB should be calculated correctly", 0L, stats.freeSpaceMB) // 300 bytes = 0 MB
    }

    @Test
    fun `cleanup result calculation`() {
        val result = CleanupResult(
            filesDeleted = 3,
            spaceFreed = 5 * 1024 * 1024L, // 5 MB
            finalFreeSpace = 100 * 1024 * 1024L // 100 MB
        )
        
        assertEquals("Space freed MB should be calculated correctly", 5L, result.spaceFreedMB)
        assertEquals("Final free space MB should be calculated correctly", 100L, result.finalFreeSpaceMB)
    }
}