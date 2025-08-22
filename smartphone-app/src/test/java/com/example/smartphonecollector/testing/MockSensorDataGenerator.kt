package com.example.smartphonecollector.testing

import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.data.models.Vector3D
import kotlin.math.*
import kotlin.random.Random

/**
 * Generates realistic mock sensor data for testing without physical sensors
 * Provides various data patterns including normal operation, edge cases, and error scenarios
 */
class MockSensorDataGenerator(private val random: Random = Random.Default) {

    companion object {
        // Realistic sensor value ranges
        private const val GRAVITY = 9.81f
        private const val TYPICAL_HEART_RATE_MIN = 60
        private const val TYPICAL_HEART_RATE_MAX = 100
        private const val EXERCISE_HEART_RATE_MIN = 120
        private const val EXERCISE_HEART_RATE_MAX = 180
        
        // Noise levels for realistic sensor simulation
        private const val ACCELEROMETER_NOISE = 0.1f
        private const val GYROSCOPE_NOISE = 0.05f
        private const val MAGNETOMETER_NOISE = 2.0f
        
        // Battery simulation
        private const val BATTERY_DRAIN_RATE = 0.1f // % per minute during collection
    }

    private var currentTimestamp = System.currentTimeMillis()
    private var currentBatteryLevel = 100
    private var stepCount = 0
    private var totalCalories = 0.0f
    private var sessionStartTime = currentTimestamp

    /**
     * Reset generator state for a new session
     */
    fun resetForNewSession() {
        currentTimestamp = System.currentTimeMillis()
        sessionStartTime = currentTimestamp
        currentBatteryLevel = random.nextInt(80, 101) // Start with 80-100% battery
        stepCount = 0
        totalCalories = 0.0f
    }

    /**
     * Generate a realistic inertial reading for stationary device
     */
    fun generateStationaryInertialReading(
        sessionId: String,
        deviceId: String = "mock_device"
    ): InertialReading {
        currentTimestamp += random.nextLong(15, 25) // ~20ms intervals (50Hz)
        
        return InertialReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = Vector3D(
                x = addNoise(0.0f, ACCELEROMETER_NOISE),
                y = addNoise(0.0f, ACCELEROMETER_NOISE),
                z = addNoise(GRAVITY, ACCELEROMETER_NOISE)
            ),
            gyroscope = Vector3D(
                x = addNoise(0.0f, GYROSCOPE_NOISE),
                y = addNoise(0.0f, GYROSCOPE_NOISE),
                z = addNoise(0.0f, GYROSCOPE_NOISE)
            ),
            magnetometer = Vector3D(
                x = addNoise(45.0f, MAGNETOMETER_NOISE),
                y = addNoise(-12.0f, MAGNETOMETER_NOISE),
                z = addNoise(8.0f, MAGNETOMETER_NOISE)
            ),
            batteryLevel = updateBatteryLevel()
        )
    }

    /**
     * Generate a realistic inertial reading for walking motion
     */
    fun generateWalkingInertialReading(
        sessionId: String,
        deviceId: String = "mock_device",
        walkingIntensity: Float = 1.0f
    ): InertialReading {
        currentTimestamp += random.nextLong(15, 25)
        
        // Simulate walking motion with periodic patterns
        val walkingPhase = (currentTimestamp - sessionStartTime) / 1000.0 * 2.0 * PI // 2 steps per second
        val stepAmplitude = 2.0f * walkingIntensity
        
        return InertialReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = Vector3D(
                x = addNoise(sin(walkingPhase).toFloat() * stepAmplitude, ACCELEROMETER_NOISE),
                y = addNoise(cos(walkingPhase * 0.5).toFloat() * stepAmplitude * 0.5f, ACCELEROMETER_NOISE),
                z = addNoise(GRAVITY + sin(walkingPhase * 2).toFloat() * stepAmplitude * 0.3f, ACCELEROMETER_NOISE)
            ),
            gyroscope = Vector3D(
                x = addNoise(sin(walkingPhase + PI/4).toFloat() * 0.2f * walkingIntensity, GYROSCOPE_NOISE),
                y = addNoise(cos(walkingPhase).toFloat() * 0.1f * walkingIntensity, GYROSCOPE_NOISE),
                z = addNoise(sin(walkingPhase * 1.5).toFloat() * 0.15f * walkingIntensity, GYROSCOPE_NOISE)
            ),
            magnetometer = Vector3D(
                x = addNoise(45.0f + sin(walkingPhase * 0.1).toFloat() * 3.0f, MAGNETOMETER_NOISE),
                y = addNoise(-12.0f + cos(walkingPhase * 0.1).toFloat() * 2.0f, MAGNETOMETER_NOISE),
                z = addNoise(8.0f + sin(walkingPhase * 0.15).toFloat() * 1.5f, MAGNETOMETER_NOISE)
            ),
            batteryLevel = updateBatteryLevel()
        )
    }

    /**
     * Generate a realistic inertial reading for running motion
     */
    fun generateRunningInertialReading(
        sessionId: String,
        deviceId: String = "mock_device",
        runningIntensity: Float = 2.0f
    ): InertialReading {
        currentTimestamp += random.nextLong(15, 25)
        
        // Simulate running motion with higher frequency and amplitude
        val runningPhase = (currentTimestamp - sessionStartTime) / 1000.0 * 3.0 * PI // 3 steps per second
        val stepAmplitude = 4.0f * runningIntensity
        
        return InertialReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = Vector3D(
                x = addNoise(sin(runningPhase).toFloat() * stepAmplitude, ACCELEROMETER_NOISE * 2),
                y = addNoise(cos(runningPhase * 0.7).toFloat() * stepAmplitude * 0.6f, ACCELEROMETER_NOISE * 2),
                z = addNoise(GRAVITY + sin(runningPhase * 2).toFloat() * stepAmplitude * 0.5f, ACCELEROMETER_NOISE * 2)
            ),
            gyroscope = Vector3D(
                x = addNoise(sin(runningPhase + PI/3).toFloat() * 0.5f * runningIntensity, GYROSCOPE_NOISE * 2),
                y = addNoise(cos(runningPhase * 1.2).toFloat() * 0.3f * runningIntensity, GYROSCOPE_NOISE * 2),
                z = addNoise(sin(runningPhase * 1.8).toFloat() * 0.4f * runningIntensity, GYROSCOPE_NOISE * 2)
            ),
            magnetometer = Vector3D(
                x = addNoise(45.0f + sin(runningPhase * 0.2).toFloat() * 5.0f, MAGNETOMETER_NOISE * 1.5f),
                y = addNoise(-12.0f + cos(runningPhase * 0.2).toFloat() * 4.0f, MAGNETOMETER_NOISE * 1.5f),
                z = addNoise(8.0f + sin(runningPhase * 0.25).toFloat() * 3.0f, MAGNETOMETER_NOISE * 1.5f)
            ),
            batteryLevel = updateBatteryLevel()
        )
    }

    /**
     * Generate a realistic biometric reading for resting state
     */
    fun generateRestingBiometricReading(
        sessionId: String,
        deviceId: String = "mock_device"
    ): BiometricReading {
        currentTimestamp += random.nextLong(900, 1100) // ~1 second intervals
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = random.nextInt(TYPICAL_HEART_RATE_MIN, TYPICAL_HEART_RATE_MAX + 1),
            stepCount = stepCount, // No steps while resting
            calories = totalCalories + random.nextFloat() * 0.1f, // Minimal calorie burn
            skinTemperature = 32.0f + random.nextFloat() * 2.0f, // 32-34°C
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate a realistic biometric reading for walking activity
     */
    fun generateWalkingBiometricReading(
        sessionId: String,
        deviceId: String = "mock_device",
        walkingIntensity: Float = 1.0f
    ): BiometricReading {
        currentTimestamp += random.nextLong(900, 1100)
        
        // Simulate step counting during walking
        val elapsedSeconds = (currentTimestamp - sessionStartTime) / 1000.0
        val expectedSteps = (elapsedSeconds * 2.0 * walkingIntensity).toInt() // 2 steps per second
        stepCount = maxOf(stepCount, expectedSteps + random.nextInt(-2, 3)) // Add some variance
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = random.nextInt(80, 120), // Elevated heart rate for walking
            stepCount = stepCount,
            calories = totalCalories + random.nextFloat() * 0.5f * walkingIntensity, // Moderate calorie burn
            skinTemperature = 33.0f + random.nextFloat() * 2.0f, // Slightly elevated temperature
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate a realistic biometric reading for running activity
     */
    fun generateRunningBiometricReading(
        sessionId: String,
        deviceId: String = "mock_device",
        runningIntensity: Float = 2.0f
    ): BiometricReading {
        currentTimestamp += random.nextLong(900, 1100)
        
        // Simulate step counting during running
        val elapsedSeconds = (currentTimestamp - sessionStartTime) / 1000.0
        val expectedSteps = (elapsedSeconds * 3.0 * runningIntensity).toInt() // 3 steps per second
        stepCount = maxOf(stepCount, expectedSteps + random.nextInt(-3, 4))
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = random.nextInt(EXERCISE_HEART_RATE_MIN, EXERCISE_HEART_RATE_MAX + 1),
            stepCount = stepCount,
            calories = totalCalories + random.nextFloat() * 1.5f * runningIntensity, // High calorie burn
            skinTemperature = 34.0f + random.nextFloat() * 2.0f, // Elevated temperature
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate biometric reading with missing heart rate (sensor unavailable)
     */
    fun generateBiometricReadingWithMissingHeartRate(
        sessionId: String,
        deviceId: String = "mock_device"
    ): BiometricReading {
        currentTimestamp += random.nextLong(900, 1100)
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = null, // Heart rate sensor unavailable
            stepCount = stepCount,
            calories = totalCalories + random.nextFloat() * 0.2f,
            skinTemperature = 32.5f + random.nextFloat() * 1.5f,
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate biometric reading with missing skin temperature
     */
    fun generateBiometricReadingWithMissingSkinTemp(
        sessionId: String,
        deviceId: String = "mock_device"
    ): BiometricReading {
        currentTimestamp += random.nextLong(900, 1100)
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = random.nextInt(TYPICAL_HEART_RATE_MIN, TYPICAL_HEART_RATE_MAX + 1),
            stepCount = stepCount,
            calories = totalCalories + random.nextFloat() * 0.3f,
            skinTemperature = null, // Skin temperature sensor unavailable
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate a batch of inertial readings for testing bulk operations
     */
    fun generateInertialReadingBatch(
        sessionId: String,
        count: Int,
        motionType: MotionType = MotionType.STATIONARY,
        deviceId: String = "mock_device"
    ): List<InertialReading> {
        return (1..count).map {
            when (motionType) {
                MotionType.STATIONARY -> generateStationaryInertialReading(sessionId, deviceId)
                MotionType.WALKING -> generateWalkingInertialReading(sessionId, deviceId)
                MotionType.RUNNING -> generateRunningInertialReading(sessionId, deviceId)
            }
        }
    }

    /**
     * Generate a batch of biometric readings for testing bulk operations
     */
    fun generateBiometricReadingBatch(
        sessionId: String,
        count: Int,
        activityType: ActivityType = ActivityType.RESTING,
        deviceId: String = "mock_device"
    ): List<BiometricReading> {
        return (1..count).map {
            when (activityType) {
                ActivityType.RESTING -> generateRestingBiometricReading(sessionId, deviceId)
                ActivityType.WALKING -> generateWalkingBiometricReading(sessionId, deviceId)
                ActivityType.RUNNING -> generateRunningBiometricReading(sessionId, deviceId)
            }
        }
    }

    /**
     * Generate readings with low battery scenario
     */
    fun generateLowBatteryInertialReading(
        sessionId: String,
        batteryLevel: Int,
        deviceId: String = "mock_device"
    ): InertialReading {
        currentBatteryLevel = batteryLevel
        return generateStationaryInertialReading(sessionId, deviceId)
    }

    /**
     * Generate readings with critically low battery scenario
     */
    fun generateCriticalBatteryBiometricReading(
        sessionId: String,
        batteryLevel: Int,
        deviceId: String = "mock_device"
    ): BiometricReading {
        currentBatteryLevel = batteryLevel
        return generateRestingBiometricReading(sessionId, deviceId)
    }

    /**
     * Create a mock session for testing
     */
    fun createMockSession(deviceId: String = "mock_device"): SessionData {
        resetForNewSession()
        return SessionData.createNew(deviceId)
    }

    /**
     * Add noise to a sensor value for realistic simulation
     */
    private fun addNoise(baseValue: Float, noiseLevel: Float): Float {
        return baseValue + (random.nextFloat() - 0.5f) * 2 * noiseLevel
    }

    /**
     * Update battery level with realistic drain
     */
    private fun updateBatteryLevel(): Int {
        val elapsedMinutes = (currentTimestamp - sessionStartTime) / 60000.0
        val drainAmount = (elapsedMinutes * BATTERY_DRAIN_RATE).toInt()
        currentBatteryLevel = maxOf(0, 100 - drainAmount)
        return currentBatteryLevel
    }

    enum class MotionType {
        STATIONARY,
        WALKING,
        RUNNING
    }

    enum class ActivityType {
        RESTING,
        WALKING,
        RUNNING
    }
}