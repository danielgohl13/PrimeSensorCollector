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
     * Initialize inertial sensors with availability checking
     * Requirements: 2.5 - Sensor availability checking and graceful degradation
     */
    private fun initializeSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        Log.d(TAG, "Sensors initialized:")
        Log.d(TAG, "  Accelerometer: ${accelerometer != null}")
        Log.d(TAG, "  Gyroscope: ${gyroscope != null}")
        Log.d(TAG, "  Magnetometer: ${magnetometer != null}")
        
        // Check sensor availability and warn about missing sensors
        val availableSensors = mutableListOf<String>()
        val missingSensors = mutableListOf<String>()
        
        if (accelerometer != null) {
            availableSensors.add("Accelerometer")
        } else {
            missingSensors.add("Accelerometer")
            Log.w(TAG, "Accelerometer not available - motion data will be incomplete")
        }
        
        if (gyroscope != null) {
            availableSensors.add("Gyroscope")
        } else {
            missingSensors.add("Gyroscope")
            Log.w(TAG, "Gyroscope not available - rotation data will be incomplete")
        }
        
        if (magnetometer != null) {
            availableSensors.add("Magnetometer")
        } else {
            missingSensors.add("Magnetometer")
            Log.w(TAG, "Magnetometer not available - magnetic field data will be incomplete")
        }
        
        if (availableSensors.isEmpty()) {
            Log.e(TAG, "No inertial sensors available - data collection will not function properly")
        } else {
            Log.i(TAG, "Available sensors: ${availableSensors.joinToString(", ")}")
            if (missingSensors.isNotEmpty()) {
                Log.w(TAG, "Missing sensors: ${missingSensors.joinToString(", ")} - collection will continue with available sensors")
            }
        }
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
     * Register sensor listeners for inertial sensors with error handling
     * Requirements: 2.5 - Graceful degradation when sensors become unavailable
     */
    private fun registerSensorListeners() {
        var registeredSensors = 0
        
        accelerometer?.let { sensor ->
            try {
                val success = sensorManager.registerListener(
                    this, 
                    sensor, 
                    SENSOR_DELAY_MICROSECONDS
                )
                if (success) {
                    registeredSensors++
                    Log.d(TAG, "Accelerometer registration: success")
                } else {
                    Log.e(TAG, "Accelerometer registration failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering accelerometer listener", e)
            }
        } ?: Log.w(TAG, "Accelerometer not available for registration")
        
        gyroscope?.let { sensor ->
            try {
                val success = sensorManager.registerListener(
                    this, 
                    sensor, 
                    SENSOR_DELAY_MICROSECONDS
                )
                if (success) {
                    registeredSensors++
                    Log.d(TAG, "Gyroscope registration: success")
                } else {
                    Log.e(TAG, "Gyroscope registration failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering gyroscope listener", e)
            }
        } ?: Log.w(TAG, "Gyroscope not available for registration")
        
        magnetometer?.let { sensor ->
            try {
                val success = sensorManager.registerListener(
                    this, 
                    sensor, 
                    SENSOR_DELAY_MICROSECONDS
                )
                if (success) {
                    registeredSensors++
                    Log.d(TAG, "Magnetometer registration: success")
                } else {
                    Log.e(TAG, "Magnetometer registration failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering magnetometer listener", e)
            }
        } ?: Log.w(TAG, "Magnetometer not available for registration")
        
        if (registeredSensors == 0) {
            Log.e(TAG, "No sensors successfully registered - data collection will not function")
            // Report error to smartphone
            serviceScope.launch {
                communicationClient.reportStatus("ERROR_NO_SENSORS")
            }
        } else {
            Log.i(TAG, "Successfully registered $registeredSensors sensors")
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
     * Handle sensor accuracy changes with error detection
     * Requirements: 2.5 - Detect and handle sensor failures
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val sensorName = sensor?.name ?: "Unknown"
        Log.d(TAG, "Sensor accuracy changed: $sensorName -> $accuracy")
        
        when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT -> {
                Log.e(TAG, "Sensor $sensorName lost contact - data may be unreliable")
            }
            SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                Log.w(TAG, "Sensor $sensorName is unreliable - data quality may be poor")
            }
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
                Log.w(TAG, "Sensor $sensorName has low accuracy")
            }
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> {
                Log.d(TAG, "Sensor $sensorName has medium accuracy")
            }
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> {
                Log.d(TAG, "Sensor $sensorName has high accuracy")
            }
        }
        
        // If sensor becomes unreliable or loses contact, continue with available sensors
        if (accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT || 
            accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.w(TAG, "Sensor $sensorName degraded - continuing collection with remaining sensors")
        }
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
     * Get current battery level and handle low battery conditions
     * Requirements: 7.5 - Low battery detection and warning systems
     */
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        // Check for critically low battery and stop collection if needed
        if (batteryLevel <= 15 && isCollecting) {
            Log.w(TAG, "Battery critically low ($batteryLevel%), stopping collection automatically")
            serviceScope.launch {
                communicationClient.reportStatus("BATTERY_CRITICAL_STOPPING")
                stopCollection()
            }
        } else if (batteryLevel <= 20 && isCollecting) {
            Log.w(TAG, "Battery low ($batteryLevel%), consider stopping collection soon")
            serviceScope.launch {
                communicationClient.reportStatus("BATTERY_LOW_WARNING")
            }
        }
        
        return batteryLevel
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