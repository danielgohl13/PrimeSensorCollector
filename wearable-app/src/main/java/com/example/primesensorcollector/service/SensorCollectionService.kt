package com.example.primesensorcollector.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.hardware.SensorEventListener2
import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.Vector3D
import com.example.primesensorcollector.data.transmission.DataTransmissionManager
import com.example.primesensorcollector.data.transmission.WearableCommunicationClientImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Background service for continuous sensor data collection
 * Collects inertial sensor data at 50Hz and biometric data when available
 */
class SensorCollectionService : Service(), SensorEventListener {
    
    companion object {
        private const val TAG = "SensorCollectionService"
        private const val NOTIFICATION_ID = 1001
        private const val SENSOR_DELAY_MICROSECONDS = 20000 // 50Hz = 20ms = 20000 microseconds
        
        const val ACTION_START_COLLECTION = "START_COLLECTION"
        const val ACTION_STOP_COLLECTION = "STOP_COLLECTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_DEVICE_ID = "device_id"
    }
    
    // Service components
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Data transmission
    private lateinit var dataTransmissionManager: DataTransmissionManager
    private lateinit var communicationClient: WearableCommunicationClientImpl
    
    // Sensors
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    
    // Collection state
    private var isCollecting = false
    private var currentSessionId: String? = null
    private var deviceId: String? = null
    
    // Sensor data storage
    private var lastAccelerometerReading: Vector3D = Vector3D.ZERO
    private var lastGyroscopeReading: Vector3D = Vector3D.ZERO
    private var lastMagnetometerReading: Vector3D = Vector3D.ZERO
    
    // Biometric data
    private var lastHeartRate: Int? = null
    private var stepCount = AtomicInteger(0)
    private var calories = 0f
    
    // Data flows
    private val _inertialDataFlow = MutableSharedFlow<InertialReading>()
    val inertialDataFlow: SharedFlow<InertialReading> = _inertialDataFlow.asSharedFlow()
    
    private val _biometricDataFlow = MutableSharedFlow<BiometricReading>()
    val biometricDataFlow: SharedFlow<BiometricReading> = _biometricDataFlow.asSharedFlow()
    
    // Coroutine scope
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SensorCollectionService created")
        
        // Initialize system services
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        // Initialize communication and data transmission
        initializeDataTransmission()
        
        // Initialize sensors
        initializeSensors()
        
        // Initialize biometric data collection
        initializeBiometricCollection()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_COLLECTION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                if (sessionId != null && deviceId != null) {
                    startCollection(sessionId, deviceId)
                } else {
                    Log.e(TAG, "Missing session ID or device ID")
                }
            }
            ACTION_STOP_COLLECTION -> {
                stopCollection()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SensorCollectionService destroyed")
        stopCollection()
        
        // Clean up transmission components
        if (::dataTransmissionManager.isInitialized) {
            dataTransmissionManager.stop()
        }
        if (::communicationClient.isInitialized) {
            communicationClient.cleanup()
        }
    }
    
    /**
     * Initialize data transmission components
     */
    private fun initializeDataTransmission() {
        // Initialize communication client
        communicationClient = WearableCommunicationClientImpl(this)
        
        // Initialize data transmission manager
        dataTransmissionManager = DataTransmissionManager(communicationClient)
        
        // Set up command listener for start/stop commands from smartphone
        communicationClient.setCommandListener { command, data ->
            handleRemoteCommand(command, data)
        }
        
        serviceScope.launch {
            // Initialize communication client
            val success = communicationClient.initialize()
            if (success) {
                Log.d(TAG, "Communication client initialized successfully")
                dataTransmissionManager.start()
            } else {
                Log.e(TAG, "Failed to initialize communication client")
            }
        }
    }
    
    /**
     * Handle remote commands from smartphone
     */
    private fun handleRemoteCommand(command: String, data: String?) {
        Log.d(TAG, "Received remote command: $command")
        
        when (command) {
            WearableCommunicationClientImpl.COMMAND_START_COLLECTION -> {
                data?.let { sessionData ->
                    val parts = sessionData.split("|")
                    if (parts.size >= 2) {
                        val sessionId = parts[0]
                        val deviceId = parts[1]
                        startCollection(sessionId, deviceId)
                    }
                }
            }
            WearableCommunicationClientImpl.COMMAND_STOP_COLLECTION -> {
                stopCollection()
            }
        }
    }
    
    /**
     * Initialize inertial sensors
     */
    private fun initializeSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        Log.d(TAG, "Sensors initialized:")
        Log.d(TAG, "  Accelerometer: ${accelerometer != null}")
        Log.d(TAG, "  Gyroscope: ${gyroscope != null}")
        Log.d(TAG, "  Magnetometer: ${magnetometer != null}")
    }
    
    /**
     * Initialize biometric data collection using simple sensors
     * Note: For a full implementation, Health Services would be preferred
     * but this provides a basic implementation for the MVP
     */
    private fun initializeBiometricCollection() {
        // For now, we'll use mock/placeholder values for biometric data
        // In a full implementation, this would integrate with Health Services
        // or use heart rate sensor directly if available
        
        val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor != null) {
            Log.d(TAG, "Heart rate sensor available")
        } else {
            Log.d(TAG, "Heart rate sensor not available, using mock data")
        }
        
        // Initialize with default values
        lastHeartRate = null
        stepCount.set(0)
        calories = 0f
    }
    
    /**
     * Start sensor data collection
     */
    fun startCollection(sessionId: String, deviceId: String) {
        if (isCollecting) {
            Log.w(TAG, "Collection already in progress")
            return
        }
        
        Log.d(TAG, "Starting collection for session: $sessionId")
        
        this.currentSessionId = sessionId
        this.deviceId = deviceId
        this.isCollecting = true
        
        // Acquire wake lock to keep CPU active
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::CollectionWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
        }
        
        // Register sensor listeners
        registerSensorListeners()
        
        // Report status to smartphone
        serviceScope.launch {
            communicationClient.reportStatus(WearableCommunicationClientImpl.STATUS_COLLECTING)
        }
        
        Log.d(TAG, "Sensor collection started")
    }
    
    /**
     * Stop sensor data collection
     */
    fun stopCollection() {
        if (!isCollecting) {
            Log.w(TAG, "Collection not in progress")
            return
        }
        
        Log.d(TAG, "Stopping collection")
        
        isCollecting = false
        currentSessionId = null
        deviceId = null
        
        // Unregister sensor listeners
        unregisterSensorListeners()
        
        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        
        // Force transmission of any remaining buffered data
        serviceScope.launch {
            dataTransmissionManager.forceTransmission()
            communicationClient.reportStatus(WearableCommunicationClientImpl.STATUS_IDLE)
        }
        
        Log.d(TAG, "Sensor collection stopped")
    }
    
    /**
     * Register sensor listeners for inertial sensors
     */
    private fun registerSensorListeners() {
        accelerometer?.let { sensor ->
            val success = sensorManager.registerListener(
                this, 
                sensor, 
                SENSOR_DELAY_MICROSECONDS
            )
            Log.d(TAG, "Accelerometer registration: $success")
        }
        
        gyroscope?.let { sensor ->
            val success = sensorManager.registerListener(
                this, 
                sensor, 
                SENSOR_DELAY_MICROSECONDS
            )
            Log.d(TAG, "Gyroscope registration: $success")
        }
        
        magnetometer?.let { sensor ->
            val success = sensorManager.registerListener(
                this, 
                sensor, 
                SENSOR_DELAY_MICROSECONDS
            )
            Log.d(TAG, "Magnetometer registration: $success")
        }
    }
    
    /**
     * Unregister sensor listeners
     */
    private fun unregisterSensorListeners() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "All sensor listeners unregistered")
    }
    
    /**
     * Handle sensor data changes
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isCollecting || event == null) return
        
        val timestamp = System.currentTimeMillis()
        val vector = Vector3D(event.values[0], event.values[1], event.values[2])
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelerometerReading = vector
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroscopeReading = vector
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lastMagnetometerReading = vector
            }
        }
        
        // Emit inertial reading with current sensor states
        emitInertialReading(timestamp)
    }
    
    /**
     * Handle sensor accuracy changes
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} -> $accuracy")
    }
    
    /**
     * Emit inertial sensor reading
     */
    private fun emitInertialReading(timestamp: Long) {
        val sessionId = currentSessionId ?: return
        val deviceId = deviceId ?: return
        
        val batteryLevel = getBatteryLevel()
        
        val reading = InertialReading(
            timestamp = timestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = lastAccelerometerReading,
            gyroscope = lastGyroscopeReading,
            magnetometer = lastMagnetometerReading,
            batteryLevel = batteryLevel
        )
        
        // Buffer data for transmission to smartphone
        dataTransmissionManager.bufferInertialReading(reading)
        
        // Also emit to local flow for any local listeners
        serviceScope.launch {
            _inertialDataFlow.emit(reading)
        }
    }
    
    /**
     * Emit biometric sensor reading
     */
    fun emitBiometricReading() {
        if (!isCollecting) return
        
        val sessionId = currentSessionId ?: return
        val deviceId = deviceId ?: return
        val timestamp = System.currentTimeMillis()
        val batteryLevel = getBatteryLevel()
        
        val reading = BiometricReading(
            timestamp = timestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = lastHeartRate,
            stepCount = stepCount.get(),
            calories = calories,
            skinTemperature = null, // Not available on most Wear OS devices
            batteryLevel = batteryLevel
        )
        
        // Buffer data for transmission to smartphone
        dataTransmissionManager.bufferBiometricReading(reading)
        
        // Also emit to local flow for any local listeners
        serviceScope.launch {
            _biometricDataFlow.emit(reading)
        }
    }
    
    /**
     * Get current battery level
     */
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    /**
     * Check if collection is currently active
     */
    fun isCollectionActive(): Boolean = isCollecting
    
    /**
     * Get current session ID
     */
    fun getCurrentSessionId(): String? = currentSessionId
}