package com.example.smartphonecollector.data.repository

import android.content.Context
import android.os.Environment
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.data.models.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository class responsible for data persistence and file operations
 * Handles CSV file creation, writing, and session management
 */
class DataRepository(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    companion object {
        private const val APP_DIRECTORY = "WearableDataCollector"
        private const val INERTIAL_FILE_PREFIX = "inertial_"
        private const val BIOMETRIC_FILE_PREFIX = "biometric_"
        private const val CSV_EXTENSION = ".csv"
    }
    
    /**
     * Get the app's data directory in Documents
     */
    private fun getAppDirectory(): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val appDir = File(documentsDir, APP_DIRECTORY)
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir
    }
    
    /**
     * Find existing inertial file for a session
     */
    private fun findExistingInertialFile(sessionId: String): String? {
        val appDir = getAppDirectory()
        return appDir.listFiles { file ->
            file.name.startsWith("$INERTIAL_FILE_PREFIX$sessionId") && file.name.endsWith(CSV_EXTENSION)
        }?.firstOrNull()?.name
    }
    
    /**
     * Find existing biometric file for a session
     */
    private fun findExistingBiometricFile(sessionId: String): String? {
        val appDir = getAppDirectory()
        return appDir.listFiles { file ->
            file.name.startsWith("$BIOMETRIC_FILE_PREFIX$sessionId") && file.name.endsWith(CSV_EXTENSION)
        }?.firstOrNull()?.name
    }
    
    /**
     * Create a new session with unique ID
     */
    fun createSession(deviceId: String = "default_device"): SessionData {
        return SessionData.createNew(deviceId)
    }
    
    /**
     * Save inertial data to CSV file
     * Requirements: 6.1, 6.3, 6.4
     */
    suspend fun saveInertialData(sessionId: String, data: List<InertialReading>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = dateFormat.format(Date())
                val filename = "$INERTIAL_FILE_PREFIX${sessionId}_$timestamp$CSV_EXTENSION"
                val file = File(getAppDirectory(), filename)
                
                FileWriter(file).use { writer ->
                    // Write header
                    writer.write(InertialReading.csvHeader())
                    writer.write("\n")
                    
                    // Write data rows
                    data.forEach { reading ->
                        writer.write(reading.toCsvRow())
                        writer.write("\n")
                    }
                }
                
                Result.success(file.absolutePath)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Save biometric data to CSV file
     * Requirements: 6.2, 6.3, 6.4
     */
    suspend fun saveBiometricData(sessionId: String, data: List<BiometricReading>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = dateFormat.format(Date())
                val filename = "$BIOMETRIC_FILE_PREFIX${sessionId}_$timestamp$CSV_EXTENSION"
                val file = File(getAppDirectory(), filename)
                
                FileWriter(file).use { writer ->
                    // Write header
                    writer.write(BiometricReading.csvHeader())
                    writer.write("\n")
                    
                    // Write data rows
                    data.forEach { reading ->
                        writer.write(reading.toCsvRow())
                        writer.write("\n")
                    }
                }
                
                Result.success(file.absolutePath)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Append inertial data to existing CSV file or create new one
     */
    suspend fun appendInertialData(sessionId: String, reading: InertialReading): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Use session start date for consistent filename across appends
                val filename = findExistingInertialFile(sessionId) 
                    ?: "$INERTIAL_FILE_PREFIX${sessionId}_${dateFormat.format(Date())}.csv"
                val file = File(getAppDirectory(), filename)
                
                val isNewFile = !file.exists()
                
                FileWriter(file, true).use { writer ->
                    if (isNewFile) {
                        writer.write(InertialReading.csvHeader())
                        writer.write("\n")
                    }
                    writer.write(reading.toCsvRow())
                    writer.write("\n")
                }
                
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Append biometric data to existing CSV file or create new one
     */
    suspend fun appendBiometricData(sessionId: String, reading: BiometricReading): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Use session start date for consistent filename across appends
                val filename = findExistingBiometricFile(sessionId) 
                    ?: "$BIOMETRIC_FILE_PREFIX${sessionId}_${dateFormat.format(Date())}.csv"
                val file = File(getAppDirectory(), filename)
                
                val isNewFile = !file.exists()
                
                FileWriter(file, true).use { writer ->
                    if (isNewFile) {
                        writer.write(BiometricReading.csvHeader())
                        writer.write("\n")
                    }
                    writer.write(reading.toCsvRow())
                    writer.write("\n")
                }
                
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get list of all CSV files in the app directory
     * Requirement: 6.5
     */
    suspend fun getSessionFiles(): Result<List<File>> {
        return withContext(Dispatchers.IO) {
            try {
                val appDir = getAppDirectory()
                val csvFiles = appDir.listFiles { file ->
                    file.isFile && file.name.endsWith(CSV_EXTENSION)
                }?.toList() ?: emptyList()
                
                Result.success(csvFiles)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check if storage directory is accessible and writable
     */
    fun isStorageAvailable(): Boolean {
        return try {
            val appDir = getAppDirectory()
            appDir.exists() && appDir.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get available storage space in bytes
     */
    fun getAvailableStorageSpace(): Long {
        return try {
            getAppDirectory().freeSpace
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get session history from existing CSV files
     */
    suspend fun getSessionHistory(): Result<List<SessionSummary>> {
        return withContext(Dispatchers.IO) {
            try {
                val appDir = getAppDirectory()
                val csvFiles = appDir.listFiles { file ->
                    file.isFile && file.name.endsWith(CSV_EXTENSION)
                }?.toList() ?: emptyList()
                
                // Group files by session ID
                val sessionGroups = csvFiles.groupBy { file ->
                    // Extract session ID from filename (e.g., "inertial_session_abc123_20240101_120000.csv")
                    val parts = file.nameWithoutExtension.split("_")
                    if (parts.size >= 3) {
                        "${parts[1]}_${parts[2]}" // session_abc123
                    } else {
                        file.nameWithoutExtension
                    }
                }
                
                val sessionSummaries = sessionGroups.map { (sessionId, files) ->
                    val inertialFile = files.find { it.name.startsWith(INERTIAL_FILE_PREFIX) }
                    val biometricFile = files.find { it.name.startsWith(BIOMETRIC_FILE_PREFIX) }
                    
                    // Use file modification time as approximation for session times
                    val startTime = files.minOfOrNull { it.lastModified() } ?: 0L
                    val endTime = files.maxOfOrNull { it.lastModified() } ?: startTime
                    
                    // Count data points by reading the files (excluding header)
                    var dataPointsCount = 0
                    files.forEach { file ->
                        try {
                            val lines = file.readLines()
                            if (lines.size > 1) { // Exclude header
                                dataPointsCount += lines.size - 1
                            }
                        } catch (e: Exception) {
                            // Ignore read errors for individual files
                        }
                    }
                    
                    SessionSummary(
                        sessionId = sessionId,
                        startTime = startTime,
                        endTime = endTime,
                        duration = endTime - startTime,
                        dataPointsCollected = dataPointsCount,
                        deviceId = "unknown", // Cannot determine from filename
                        inertialFilePath = inertialFile?.absolutePath,
                        biometricFilePath = biometricFile?.absolutePath
                    )
                }
                
                Result.success(sessionSummaries.sortedByDescending { it.startTime })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Delete old session files to free up space
     */
    suspend fun cleanupOldFiles(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val appDir = getAppDirectory()
                val currentTime = System.currentTimeMillis()
                var deletedCount = 0
                
                appDir.listFiles { file ->
                    file.isFile && file.name.endsWith(CSV_EXTENSION)
                }?.forEach { file ->
                    if (currentTime - file.lastModified() > maxAgeMillis) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
                
                Result.success(deletedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}