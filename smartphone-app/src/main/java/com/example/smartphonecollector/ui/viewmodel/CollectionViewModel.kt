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
     * Requirements: 1.1, 1.2
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
     * Requirements: 5.4 - Real-time data update handlers
     */
    private fun handleInertialDataReceived(inertialReading: InertialReading) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Inertial data received: ${inertialReading.timestamp}")

                // Update real-time data for UI visualization
                _realTimeInertialData.value = inertialReading

                // Save data to repository
                val result = dataRepository.appendInertialData(inertialReading.sessionId, inertialReading)
                if (result.isFailure) {
                    Log.e(TAG, "Failed to save inertial data", result.exceptionOrNull())
                    _errorMessage.value = "Failed to save inertial data: ${result.exceptionOrNull()?.message}"
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
     * Requirements: 5.4 - Real-time data update handlers
     */
    private fun handleBiometricDataReceived(biometricReading: BiometricReading) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Biometric data received: ${biometricReading.timestamp}")

                // Update real-time data for UI visualization
                _realTimeBiometricData.value = biometricReading

                // Save data to repository
                val result = dataRepository.appendBiometricData(biometricReading.sessionId, biometricReading)
                if (result.isFailure) {
                    Log.e(TAG, "Failed to save biometric data", result.exceptionOrNull())
                    _errorMessage.value = "Failed to save biometric data: ${result.exceptionOrNull()?.message}"
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
     * Get storage information
     */
    fun getStorageInfo(): Pair<Boolean, Long> {
        return Pair(
            dataRepository.isStorageAvailable(),
            dataRepository.getAvailableStorageSpace()
        )
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