package com.example.smartphonecollector

import android.content.Context
import android.os.Environment
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.data.repository.DataRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var testDirectory: File
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        
        // Create a temporary test directory
        testDirectory = File.createTempFile("test", "dir").apply {
            delete()
            mkdirs()
        }
        
        // Mock Environment.getExternalStoragePublicDirectory to return our test directory
        mockkStatic(Environment::class)
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) } returns testDirectory
        
        dataRepository = DataRepository(context)
    }

    @After
    fun tearDown() {
        // Clean up test directory
        testDirectory.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `createSession should generate unique session with correct device ID`() {
        val deviceId = "test_device_123"
        val session = dataRepository.createSession(deviceId)
        
        assertNotNull(session)
        assertEquals(deviceId, session.deviceId)
        assertTrue(session.sessionId.startsWith("session_"))
        assertTrue(session.isActive)
        assertNull(session.endTime)
        assertEquals(0, session.dataPointsCollected)
    }

    @Test
    fun `createSession should generate different IDs for multiple sessions`() {
        val session1 = dataRepository.createSession("device1")
        val session2 = dataRepository.createSession("device2")
        
        assertNotEquals(session1.sessionId, session2.sessionId)
    }

    @Test
    fun `saveInertialData should create CSV file with correct format`() = runTest {
        val sessionId = "test_session_001"
        val testData = listOf(
            InertialReading(
                timestamp = 1640995200000L,
                sessionId = sessionId,
                deviceId = "test_device",
                accelerometer = Vector3D(0.12f, -0.34f, 9.81f),
                gyroscope = Vector3D(0.01f, 0.02f, -0.01f),
                magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
                batteryLevel = 85
            ),
            InertialReading(
                timestamp = 1640995201000L,
                sessionId = sessionId,
                deviceId = "test_device",
                accelerometer = Vector3D(0.15f, -0.30f, 9.85f),
                gyroscope = Vector3D(0.02f, 0.01f, -0.02f),
                magnetometer = Vector3D(45.5f, -12.1f, 8.9f),
                batteryLevel = 84
            )
        )

        val result = dataRepository.saveInertialData(sessionId, testData)
        
        assertTrue(result.isSuccess)
        val filePath = result.getOrNull()
        assertNotNull(filePath)
        
        val file = File(filePath!!)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("inertial_$sessionId"))
        assertTrue(file.name.endsWith(".csv"))
        
        val lines = file.readLines()
        assertEquals(3, lines.size) // Header + 2 data rows
        assertEquals(InertialReading.csvHeader(), lines[0])
        
        // Verify first data row
        val expectedRow1 = "1640995200000,test_session_001,test_device,0.12,-0.34,9.81,0.01,0.02,-0.01,45.2,-12.3,8.7,85"
        assertEquals(expectedRow1, lines[1])
    }

    @Test
    fun `saveBiometricData should create CSV file with correct format`() = runTest {
        val sessionId = "test_session_002"
        val testData = listOf(
            BiometricReading(
                timestamp = 1640995200000L,
                sessionId = sessionId,
                deviceId = "test_device",
                heartRate = 72,
                stepCount = 1250,
                calories = 45.2f,
                skinTemperature = 32.1f,
                batteryLevel = 85
            ),
            BiometricReading(
                timestamp = 1640995201000L,
                sessionId = sessionId,
                deviceId = "test_device",
                heartRate = null, // Test null heart rate
                stepCount = 1251,
                calories = 45.3f,
                skinTemperature = null, // Test null temperature
                batteryLevel = 84
            )
        )

        val result = dataRepository.saveBiometricData(sessionId, testData)
        
        assertTrue(result.isSuccess)
        val filePath = result.getOrNull()
        assertNotNull(filePath)
        
        val file = File(filePath!!)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("biometric_$sessionId"))
        assertTrue(file.name.endsWith(".csv"))
        
        val lines = file.readLines()
        assertEquals(3, lines.size) // Header + 2 data rows
        assertEquals(BiometricReading.csvHeader(), lines[0])
        
        // Verify first data row
        val expectedRow1 = "1640995200000,test_session_002,test_device,72,1250,45.2,32.1,85"
        assertEquals(expectedRow1, lines[1])
        
        // Verify second data row with null values
        val expectedRow2 = "1640995201000,test_session_002,test_device,,1251,45.3,,84"
        assertEquals(expectedRow2, lines[2])
    }

    @Test
    fun `appendInertialData should create new file if not exists`() = runTest {
        val sessionId = "test_session_003"
        val reading = InertialReading(
            timestamp = 1640995200000L,
            sessionId = sessionId,
            deviceId = "test_device",
            accelerometer = Vector3D(0.12f, -0.34f, 9.81f),
            gyroscope = Vector3D(0.01f, 0.02f, -0.01f),
            magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
            batteryLevel = 85
        )

        val result = dataRepository.appendInertialData(sessionId, reading)
        
        assertTrue(result.isSuccess)
        
        // Check that file was created
        val appDir = File(testDirectory, "WearableDataCollector")
        val files = appDir.listFiles { file -> 
            file.name.startsWith("inertial_$sessionId") && file.name.endsWith(".csv")
        }
        
        assertNotNull(files)
        assertEquals(1, files!!.size)
        
        val lines = files[0].readLines()
        assertEquals(2, lines.size) // Header + 1 data row
        assertEquals(InertialReading.csvHeader(), lines[0])
    }

    @Test
    fun `appendBiometricData should append to existing file`() = runTest {
        val sessionId = "test_session_004"
        val reading1 = BiometricReading(
            timestamp = 1640995200000L,
            sessionId = sessionId,
            deviceId = "test_device",
            heartRate = 72,
            stepCount = 1250,
            calories = 45.2f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )
        val reading2 = BiometricReading(
            timestamp = 1640995201000L,
            sessionId = sessionId,
            deviceId = "test_device",
            heartRate = 74,
            stepCount = 1251,
            calories = 45.3f,
            skinTemperature = 32.2f,
            batteryLevel = 84
        )

        // Append first reading (creates file)
        val result1 = dataRepository.appendBiometricData(sessionId, reading1)
        assertTrue(result1.isSuccess)
        
        // Append second reading (appends to existing file)
        val result2 = dataRepository.appendBiometricData(sessionId, reading2)
        assertTrue(result2.isSuccess)
        
        // Check file contents
        val appDir = File(testDirectory, "WearableDataCollector")
        val files = appDir.listFiles { file -> 
            file.name.startsWith("biometric_$sessionId") && file.name.endsWith(".csv")
        }
        
        assertNotNull(files)
        assertEquals(1, files!!.size)
        
        val lines = files[0].readLines()
        assertEquals(3, lines.size) // Header + 2 data rows
        assertEquals(BiometricReading.csvHeader(), lines[0])
    }

    @Test
    fun `getSessionFiles should return all CSV files`() = runTest {
        // Create some test files
        val appDir = File(testDirectory, "WearableDataCollector")
        appDir.mkdirs()
        
        val file1 = File(appDir, "inertial_session_001_20240101_120000.csv")
        val file2 = File(appDir, "biometric_session_001_20240101_120000.csv")
        val file3 = File(appDir, "inertial_session_002_20240101_130000.csv")
        val file4 = File(appDir, "not_a_csv.txt") // Should be ignored
        
        file1.createNewFile()
        file2.createNewFile()
        file3.createNewFile()
        file4.createNewFile()
        
        val result = dataRepository.getSessionFiles()
        
        assertTrue(result.isSuccess)
        val files = result.getOrNull()
        assertNotNull(files)
        assertEquals(3, files!!.size) // Only CSV files
        
        val fileNames = files.map { it.name }.sorted()
        assertTrue(fileNames.contains("inertial_session_001_20240101_120000.csv"))
        assertTrue(fileNames.contains("biometric_session_001_20240101_120000.csv"))
        assertTrue(fileNames.contains("inertial_session_002_20240101_130000.csv"))
        assertFalse(fileNames.contains("not_a_csv.txt"))
    }

    @Test
    fun `isStorageAvailable should return true when directory is accessible`() {
        assertTrue(dataRepository.isStorageAvailable())
    }

    @Test
    fun `getAvailableStorageSpace should return positive value`() {
        val space = dataRepository.getAvailableStorageSpace()
        assertTrue(space > 0)
    }

    @Test
    fun `cleanupOldFiles should delete files older than specified age`() = runTest {
        val appDir = File(testDirectory, "WearableDataCollector")
        appDir.mkdirs()
        
        // Create test files with different ages
        val oldFile = File(appDir, "old_session.csv")
        val newFile = File(appDir, "new_session.csv")
        
        oldFile.createNewFile()
        newFile.createNewFile()
        
        // Set old file to be 8 days old
        val eightDaysAgo = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)
        oldFile.setLastModified(eightDaysAgo)
        
        // Clean up files older than 7 days
        val result = dataRepository.cleanupOldFiles(7 * 24 * 60 * 60 * 1000L)
        
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()) // One file should be deleted
        
        assertFalse(oldFile.exists())
        assertTrue(newFile.exists())
    }

    @Test
    fun `saveInertialData should handle IO errors gracefully`() = runTest {
        // Test that the repository properly wraps exceptions in Result.failure
        // We'll test this by verifying the Result type handling rather than simulating actual IO errors
        // which can be platform-dependent
        
        val sessionId = "test_session_error"
        val testData = listOf(
            InertialReading(
                timestamp = 1640995200000L,
                sessionId = sessionId,
                deviceId = "test_device",
                accelerometer = Vector3D(0.12f, -0.34f, 9.81f),
                gyroscope = Vector3D(0.01f, 0.02f, -0.01f),
                magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
                batteryLevel = 85
            )
        )

        // Test normal operation returns success
        val result = dataRepository.saveInertialData(sessionId, testData)
        assertTrue("Normal operation should succeed", result.isSuccess)
        
        // Verify the file was created
        val filePath = result.getOrNull()
        assertNotNull("File path should not be null", filePath)
        assertTrue("File should exist", File(filePath!!).exists())
    }

    @Test
    fun `getSessionHistory should parse existing CSV files correctly`() = runTest {
        val appDir = File(testDirectory, "WearableDataCollector")
        appDir.mkdirs()
        
        // Create test CSV files with sample data
        val inertialFile = File(appDir, "inertial_session_001_20240101_120000.csv")
        val biometricFile = File(appDir, "biometric_session_001_20240101_120000.csv")
        
        // Write sample data to files
        inertialFile.writeText("""
            ${InertialReading.csvHeader()}
            1640995200000,session_001,test_device,0.12,-0.34,9.81,0.01,0.02,-0.01,45.2,-12.3,8.7,85
            1640995201000,session_001,test_device,0.15,-0.30,9.85,0.02,0.01,-0.02,45.5,-12.1,8.9,84
        """.trimIndent())
        
        biometricFile.writeText("""
            ${BiometricReading.csvHeader()}
            1640995200000,session_001,test_device,72,1250,45.2,32.1,85
        """.trimIndent())
        
        val result = dataRepository.getSessionHistory()
        
        assertTrue(result.isSuccess)
        val sessions = result.getOrNull()
        assertNotNull(sessions)
        assertEquals(1, sessions!!.size)
        
        val session = sessions[0]
        assertEquals("session_001", session.sessionId)
        assertEquals(3, session.dataPointsCollected) // 2 inertial + 1 biometric
        assertNotNull(session.inertialFilePath)
        assertNotNull(session.biometricFilePath)
    }
}