package com.example.smartphonecollector.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.communication.WearableCommunicationService
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.data.repository.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing data collection state and business logic
 * Handles session management, real-time data updates, and connection status
 * Requirements: 1.1, 1.2, 1.3, 5.4
 */
class CollectionViewModel(
    private val context: Context,
    private val dataRepository: DataRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CollectionViewModel"
    }

    // UI State Properties
    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val _sessionData = MutableStateFlow<SessionData?>(null)
    val sessionData: StateFlow<SessionData?> = _sessionData.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _realTimeInertialData = MutableStateFlow<InertialReading?>(null)
    val realTimeInertialData: StateFlow<InertialReading?> = _realTimeInertialData.asStateFlow()

    private val _realTimeBiometricData = MutableStateFlow<BiometricReading?>(null)
    val realTimeBiometricData: StateFlow<BiometricReading?> = _realTimeBiometricData.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _dataPointsCount = MutableStateFlow(0)
    val dataPointsCount: StateFlow<Int> = _dataPointsCount.asStateFlow()

    // Communication service
    private var communicationService: WearableCommunicationService? = null

    init {
        initializeCommunicationService()
        startPeriodicMonitoring()
    }

    /**
     * Initialize the wearable communication service
     */
    private fun initializeCommunicationService() {
        try {
            communicationService = WearableCommunicationService(
                context = context,
                onInertialDataReceived = { inertialReading ->
                    handleInertialDataReceived(inertialReading)
                },
                onBiometricDataReceived = { biometricReading ->
                    handleBiometricDataReceived(biometricReading)
                }
            )

            // Monitor connection status changes
            viewModelScope.launch {
                communicationService?.connectionStatus?.collect { status ->
                    _connectionStatus.value = status
                    Log.d(TAG, "Connection status updated: $status")
                }
            }

            Log.d(TAG, "Communication service initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize communication service", e)
            _errorMessage.value = "Failed to initialize wearable communication: ${e.message}"
        }
    }

    /**
     * Start a new data collection session
     * Requirements: 1.1, 1.2, 3.5 - Storage capacity monitoring
     */
    fun startCollection() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting collection session")

                // Check if already collecting
                if (_isCollecting.value) {
                    _errorMessage.value = "Collection is already in progress"
                    return@launch
                }

                // Check connection status
                val isConnected = communicationService?.checkConnectionStatus() ?: false
                if (!isConnected) {
                    _errorMessage.value = "No smartwatch connected. Please ensure your Galaxy Watch 5 is paired and nearby."
                    return@launch
                }

                // Check storage availability
                if (!dataRepository.isStorageAvailable()) {
                    _errorMessage.value = "Storage is not available. Please check permissions."
                    return@launch
                }

                // Check storage space and perform cleanup if needed
                if (dataRepository.isStorageSpaceLow()) {
                    Log.w(TAG, "Storage space is low, attempting cleanup...")
                    val cleanupResult = dataRepository.cleanupOldFiles()
                    if (cleanupResult.isSuccess) {
                        val deletedCount = cleanupResult.getOrNull() ?: 0
                        if (deletedCount > 0) {
                            Log.i(TAG, "Cleaned up $deletedCount old files")
                        }
                    }
                    
                    // Check again after cleanup
                    if (dataRepository.isStorageSpaceLow()) {
                        _errorMessage.value = "Storage space is critically low. Please free up space or delete old data files."
                        return@launch
                    }
                }

                // Create new session
                val session = dataRepository.createSession("galaxy_watch_5")
                _sessionData.value = session

                // Start collection on wearable
                val success = communicationService?.startCollection(session.sessionId) ?: false
                if (success) {
                    _isCollecting.value = true
                    _dataPointsCount.value = 0
                    _errorMessage.value = null
                    Log.d(TAG, "Collection started successfully with session: ${session.sessionId}")
                } else {
                    _errorMessage.value = "Failed to start collection on smartwatch"
                    _sessionData.value = null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error starting collection", e)
                _errorMessage.value = "Error starting collection: ${e.message}"
                _sessionData.value = null
            }
        }
    }

    /**
     * Stop the current data collection session
     * Requirements: 1.3
     */
    fun stopCollection() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Stopping collection session")

                if (!_isCollecting.value) {
                    _errorMessage.value = "No active collection session"
                    return@launch
                }

                // Stop collection on wearable
                val success = communicationService?.stopCollection() ?: false
                if (success) {
                    // Complete the session
                    _sessionData.value?.let { session ->
                        val completedSession = session.complete().updateDataPoints(_dataPointsCount.value)
                        _sessionData.value = completedSession
                        Log.d(TAG, "Session completed: ${completedSession.sessionId}, Duration: ${completedSession.getDuration()}ms, Data points: ${completedSession.dataPointsCollected}")
                    }

                    _isCollecting.value = false
                    _errorMessage.value = null
                    Log.d(TAG, "Collection stopped successfully")
                } else {
                    _errorMessage.value = "Failed to stop collection on smartwatch"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping collection", e)
                _errorMessage.value = "Error stopping collection: ${e.message}"
            }
        }
    }

    /**
     * Handle incoming inertial data from wearable
     * Requirements: 5.4 - Real-time data update handlers, 5.5 - Low battery warning
     */
    private fun handleInertialDataReceived(inertialReading: InertialReading) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Inertial data received: ${inertialReading.timestamp}")

                // Update real-time data for UI visualization
                _realTimeInertialData.value = inertialReading

                // Check battery level and handle low battery conditions
                handleLowBatteryCondition(inertialReading.batteryLevel)

                // Save data to repository
                val result = dataRepository.appendInertialData(inertialReading.sessionId, inertialReading)
                if (result.isFailure) {
                    Log.e(TAG, "Failed to save inertial data", result.exceptionOrNull())
                    
                    // Check if failure is due to storage issues
                    if (dataRepository.isStorageSpaceLow()) {
                        Log.w(TAG, "Storage space critically low, attempting emergency cleanup")
                        val cleanupResult = dataRepository.performEmergencyCleanup()
                        if (cleanupResult.isSuccess) {
                            val cleanup = cleanupResult.getOrNull()
                            _errorMessage.value = "Storage was full. Emergency cleanup freed ${cleanup?.spaceFreedMB}MB by deleting ${cleanup?.filesDeleted} old files."
                        } else {
                            _errorMessage.value = "Storage full and cleanup failed. Collection may be interrupted."
                            // Consider stopping collection if storage is critically low
                            if (_isCollecting.value) {
                                stopCollection()
                            }
                        }
                    } else {
                        _errorMessage.value = "Failed to save inertial data: ${result.exceptionOrNull()?.message}"
                    }
                } else {
                    // Update data points count
                    _dataPointsCount.value = _dataPointsCount.value + 1
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling inertial data", e)
                _errorMessage.value = "Error processing inertial data: ${e.message}"
            }
        }
    }

    /**
     * Handle incoming biometric data from wearable
     * Requirements: 5.4 - Real-time data update handlers, 5.5 - Low battery warning
     */
    private fun handleBiometricDataReceived(biometricReading: BiometricReading) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Biometric data received: ${biometricReading.timestamp}")

                // Update real-time data for UI visualization
                _realTimeBiometricData.value = biometricReading

                // Check battery level and handle low battery conditions
                handleLowBatteryCondition(biometricReading.batteryLevel)

                // Save data to repository
                val result = dataRepository.appendBiometricData(biometricReading.sessionId, biometricReading)
                if (result.isFailure) {
                    Log.e(TAG, "Failed to save biometric data", result.exceptionOrNull())
                    
                    // Check if failure is due to storage issues
                    if (dataRepository.isStorageSpaceLow()) {
                        Log.w(TAG, "Storage space critically low, attempting emergency cleanup")
                        val cleanupResult = dataRepository.performEmergencyCleanup()
                        if (cleanupResult.isSuccess) {
                            val cleanup = cleanupResult.getOrNull()
                            _errorMessage.value = "Storage was full. Emergency cleanup freed ${cleanup?.spaceFreedMB}MB by deleting ${cleanup?.filesDeleted} old files."
                        } else {
                            _errorMessage.value = "Storage full and cleanup failed. Collection may be interrupted."
                            // Consider stopping collection if storage is critically low
                            if (_isCollecting.value) {
                                stopCollection()
                            }
                        }
                    } else {
                        _errorMessage.value = "Failed to save biometric data: ${result.exceptionOrNull()?.message}"
                    }
                } else {
                    // Update data points count
                    _dataPointsCount.value = _dataPointsCount.value + 1
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling biometric data", e)
                _errorMessage.value = "Error processing biometric data: ${e.message}"
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Check connection status manually
     */
    fun checkConnection() {
        viewModelScope.launch {
            try {
                val isConnected = communicationService?.checkConnectionStatus() ?: false
                Log.d(TAG, "Manual connection check: $isConnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection", e)
                _errorMessage.value = "Error checking connection: ${e.message}"
            }
        }
    }

    /**
     * Get current session duration in milliseconds
     */
    fun getCurrentSessionDuration(): Long {
        return _sessionData.value?.getDuration() ?: 0L
    }

    /**
     * Get current battery level from latest readings
     */
    fun getCurrentBatteryLevel(): Int? {
        return _realTimeInertialData.value?.batteryLevel 
            ?: _realTimeBiometricData.value?.batteryLevel
    }

    /**
     * Check if battery is low (below 20%)
     * Requirements: 5.5 - Low battery warning
     */
    fun isBatteryLow(): Boolean {
        val batteryLevel = getCurrentBatteryLevel()
        return batteryLevel != null && batteryLevel < 20
    }
    
    /**
     * Check if battery is critically low (below 15%) and should stop collection
     * Requirements: 7.5 - Low battery detection and warning systems
     */
    fun isBatteryCriticallyLow(): Boolean {
        val batteryLevel = getCurrentBatteryLevel()
        return batteryLevel != null && batteryLevel < 15
    }
    
    /**
     * Handle low battery condition by stopping collection if critically low
     * Requirements: 7.5 - Automatic collection stop at low battery
     */
    private fun handleLowBatteryCondition(batteryLevel: Int) {
        when {
            batteryLevel < 15 -> {
                if (_isCollecting.value) {
                    Log.w(TAG, "Battery critically low ($batteryLevel%), stopping collection automatically")
                    _errorMessage.value = "Collection stopped automatically due to critically low battery ($batteryLevel%)"
                    viewModelScope.launch {
                        stopCollection()
                    }
                }
            }
            batteryLevel < 20 -> {
                _errorMessage.value = "Warning: Battery is low ($batteryLevel%). Consider stopping collection soon."
            }
        }
    }

    /**
     * Get storage information
     * Requirements: 3.5 - Storage capacity monitoring
     */
    fun getStorageInfo(): Pair<Boolean, Long> {
        return Pair(
            dataRepository.isStorageAvailable(),
            dataRepository.getAvailableStorageSpace()
        )
    }
    
    /**
     * Get detailed storage statistics
     * Requirements: 3.5 - Storage capacity monitoring
     */
    fun getStorageStatistics() {
        viewModelScope.launch {
            try {
                val result = dataRepository.getStorageStatistics()
                if (result.isSuccess) {
                    val stats = result.getOrNull()
                    Log.d(TAG, "Storage statistics: ${stats?.freeSpaceMB}MB free, ${stats?.appDataSizeMB}MB app data, ${stats?.fileCount} files")
                    
                    // Check if storage is getting low and warn user
                    if (stats?.isLowSpace == true) {
                        _errorMessage.value = "Warning: Storage space is low (${stats.freeSpaceMB}MB remaining). Consider cleaning up old data files."
                    }
                } else {
                    Log.e(TAG, "Failed to get storage statistics", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting storage statistics", e)
            }
        }
    }
    
    /**
     * Perform manual cleanup of old files
     * Requirements: 3.5 - Storage capacity monitoring and cleanup mechanisms
     */
    fun performCleanup() {
        viewModelScope.launch {
            try {
                val result = dataRepository.cleanupOldFiles()
                if (result.isSuccess) {
                    val deletedCount = result.getOrNull() ?: 0
                    if (deletedCount > 0) {
                        _errorMessage.value = "Cleanup completed: deleted $deletedCount old files"
                    } else {
                        _errorMessage.value = "No old files found to clean up"
                    }
                } else {
                    _errorMessage.value = "Cleanup failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing cleanup", e)
                _errorMessage.value = "Error performing cleanup: ${e.message}"
            }
        }
    }

    /**
     * Start periodic monitoring of storage and connection status
     * Requirements: 3.5, 5.4 - Storage capacity monitoring
     */
    private fun startPeriodicMonitoring() {
        viewModelScope.launch {
            while (true) {
                try {
                    // Check storage statistics periodically
                    getStorageStatistics()
                    
                    // Wait 30 seconds before next check
                    kotlinx.coroutines.delay(30000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic monitoring", e)
                    kotlinx.coroutines.delay(60000) // Wait longer on error
                }
            }
        }
    }
    
    /**
     * Clean up resources when ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        communicationService?.cleanup()
        Log.d(TAG, "CollectionViewModel cleared")
    }
}