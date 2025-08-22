package com.example.primesensorcollector.testing

import com.example.primesensorcollector.data.models.BiometricReading
import com.example.primesensorcollector.data.models.InertialReading
import com.example.primesensorcollector.data.models.SessionData
import com.example.primesensorcollector.data.models.Vector3D
import kotlin.math.*
import kotlin.random.Random

/**
 * Generates realistic mock sensor data for testing wearable app without physical sensors
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
        private const val BATTERY_DRAIN_RATE = 0.15f // % per minute during collection (higher for wearable)
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
        currentBatteryLevel = random.nextInt(70, 101) // Wearables typically start with 70-100% battery
        stepCount = 0
        totalCalories = 0.0f
    }

    /**
     * Generate a realistic inertial reading for wrist-worn device at rest
     */
    fun generateWristStationaryInertialReading(
        sessionId: String,
        deviceId: String = "galaxy_watch_5"
    ): InertialReading {
        currentTimestamp += random.nextLong(18, 22) // ~20ms intervals (50Hz)
        
        return InertialReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = Vector3D(
                x = addNoise(0.0f, ACCELEROMETER_NOISE),
                y = addNoise(0.0f, ACCELEROMETER_NOISE),
                z = addNoise(-GRAVITY, ACCELEROMETER_NOISE) // Wrist orientation
            ),
            gyroscope = Vector3D(
                x = addNoise(0.0f, GYROSCOPE_NOISE),
                y = addNoise(0.0f, GYROSCOPE_NOISE),
                z = addNoise(0.0f, GYROSCOPE_NOISE)
            ),
            magnetometer = Vector3D(
                x = addNoise(25.0f, MAGNETOMETER_NOISE), // Typical wrist orientation
                y = addNoise(-8.0f, MAGNETOMETER_NOISE),
                z = addNoise(-15.0f, MAGNETOMETER_NOISE)
            ),
            batteryLevel = updateBatteryLevel()
        )
    }

    /**
     * Generate a realistic inertial reading for arm swinging during walking
     */
    fun generateWristWalkingInertialReading(
        sessionId: String,
        deviceId: String = "galaxy_watch_5",
        walkingIntensity: Float = 1.0f
    ): InertialReading {
        currentTimestamp += random.nextLong(18, 22)
        
        // Simulate arm swinging motion during walking
        val walkingPhase = (currentTimestamp - sessionStartTime) / 1000.0 * 2.0 * PI // 2 steps per second
        val armSwingAmplitude = 3.0f * walkingIntensity
        
        return InertialReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = Vector3D(
                x = addNoise(sin(walkingPhase).toFloat() * armSwingAmplitude, ACCELEROMETER_NOISE),
                y = addNoise(cos(walkingPhase * 0.8).toFloat() * armSwingAmplitude * 0.7f, ACCELEROMETER_NOISE),
                z = addNoise(-GRAVITY + sin(walkingPhase * 1.5).toFloat() * armSwingAmplitude * 0.4f, ACCELEROMETER_NOISE)
            ),
            gyroscope = Vector3D(
                x = addNoise(sin(walkingPhase + PI/6).toFloat() * 0.3f * walkingIntensity, GYROSCOPE_NOISE),
                y = addNoise(cos(walkingPhase * 1.2).toFloat() * 0.2f * walkingIntensity, GYROSCOPE_NOISE),
                z = addNoise(sin(walkingPhase * 0.9).toFloat() * 0.25f * walkingIntensity, GYROSCOPE_NOISE)
            ),
            magnetometer = Vector3D(
                x = addNoise(25.0f + sin(walkingPhase * 0.15).toFloat() * 8.0f, MAGNETOMETER_NOISE),
                y = addNoise(-8.0f + cos(walkingPhase * 0.12).toFloat() * 6.0f, MAGNETOMETER_NOISE),
                z = addNoise(-15.0f + sin(walkingPhase * 0.18).toFloat() * 4.0f, MAGNETOMETER_NOISE)
            ),
            batteryLevel = updateBatteryLevel()
        )
    }

    /**
     * Generate a realistic inertial reading for vigorous arm movement during running
     */
    fun generateWristRunningInertialReading(
        sessionId: String,
        deviceId: String = "galaxy_watch_5",
        runningIntensity: Float = 2.0f
    ): InertialReading {
        currentTimestamp += random.nextLong(18, 22)
        
        // Simulate vigorous arm movement during running
        val runningPhase = (currentTimestamp - sessionStartTime) / 1000.0 * 3.5 * PI // 3.5 steps per second
        val armSwingAmplitude = 6.0f * runningIntensity
        
        return InertialReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = Vector3D(
                x = addNoise(sin(runningPhase).toFloat() * armSwingAmplitude, ACCELEROMETER_NOISE * 1.5f),
                y = addNoise(cos(runningPhase * 0.9).toFloat() * armSwingAmplitude * 0.8f, ACCELEROMETER_NOISE * 1.5f),
                z = addNoise(-GRAVITY + sin(runningPhase * 2).toFloat() * armSwingAmplitude * 0.6f, ACCELEROMETER_NOISE * 1.5f)
            ),
            gyroscope = Vector3D(
                x = addNoise(sin(runningPhase + PI/4).toFloat() * 0.8f * runningIntensity, GYROSCOPE_NOISE * 2),
                y = addNoise(cos(runningPhase * 1.3).toFloat() * 0.6f * runningIntensity, GYROSCOPE_NOISE * 2),
                z = addNoise(sin(runningPhase * 1.1).toFloat() * 0.7f * runningIntensity, GYROSCOPE_NOISE * 2)
            ),
            magnetometer = Vector3D(
                x = addNoise(25.0f + sin(runningPhase * 0.25).toFloat() * 12.0f, MAGNETOMETER_NOISE * 2),
                y = addNoise(-8.0f + cos(runningPhase * 0.22).toFloat() * 10.0f, MAGNETOMETER_NOISE * 2),
                z = addNoise(-15.0f + sin(runningPhase * 0.28).toFloat() * 8.0f, MAGNETOMETER_NOISE * 2)
            ),
            batteryLevel = updateBatteryLevel()
        )
    }

    /**
     * Generate a realistic biometric reading for resting state on wearable
     */
    fun generateWearableRestingBiometricReading(
        sessionId: String,
        deviceId: String = "galaxy_watch_5"
    ): BiometricReading {
        currentTimestamp += random.nextLong(800, 1200) // ~1 second intervals
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = random.nextInt(TYPICAL_HEART_RATE_MIN, TYPICAL_HEART_RATE_MAX + 1),
            stepCount = stepCount, // No steps while resting
            calories = totalCalories + random.nextFloat() * 0.08f, // Minimal calorie burn
            skinTemperature = 31.5f + random.nextFloat() * 2.5f, // Wrist temperature range
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate a realistic biometric reading for walking activity on wearable
     */
    fun generateWearableWalkingBiometricReading(
        sessionId: String,
        deviceId: String = "galaxy_watch_5",
        walkingIntensity: Float = 1.0f
    ): BiometricReading {
        currentTimestamp += random.nextLong(800, 1200)
        
        // Simulate step counting during walking
        val elapsedSeconds = (currentTimestamp - sessionStartTime) / 1000.0
        val expectedSteps = (elapsedSeconds * 2.2 * walkingIntensity).toInt() // Slightly higher step rate
        stepCount = maxOf(stepCount, expectedSteps + random.nextInt(-3, 4)) // Add variance
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = random.nextInt(85, 125), // Elevated heart rate for walking
            stepCount = stepCount,
            calories = totalCalories + random.nextFloat() * 0.6f * walkingIntensity, // Moderate calorie burn
            skinTemperature = 32.5f + random.nextFloat() * 2.0f, // Slightly elevated temperature
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate a realistic biometric reading for running activity on wearable
     */
    fun generateWearableRunningBiometricReading(
        sessionId: String,
        deviceId: String = "galaxy_watch_5",
        runningIntensity: Float = 2.0f
    ): BiometricReading {
        currentTimestamp += random.nextLong(800, 1200)
        
        // Simulate step counting during running
        val elapsedSeconds = (currentTimestamp - sessionStartTime) / 1000.0
        val expectedSteps = (elapsedSeconds * 3.2 * runningIntensity).toInt() // Higher step rate for running
        stepCount = maxOf(stepCount, expectedSteps + random.nextInt(-4, 5))
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = random.nextInt(EXERCISE_HEART_RATE_MIN, EXERCISE_HEART_RATE_MAX + 1),
            stepCount = stepCount,
            calories = totalCalories + random.nextFloat() * 2.0f * runningIntensity, // High calorie burn
            skinTemperature = 33.5f + random.nextFloat() * 2.5f, // Elevated temperature
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate readings with sensor failure scenarios
     */
    fun generateInertialReadingWithSensorFailure(
        sessionId: String,
        failedSensor: SensorType,
        deviceId: String = "galaxy_watch_5"
    ): InertialReading {
        currentTimestamp += random.nextLong(18, 22)
        
        return InertialReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            accelerometer = if (failedSensor == SensorType.ACCELEROMETER) Vector3D.ZERO 
                           else Vector3D(addNoise(0.0f, ACCELEROMETER_NOISE), addNoise(0.0f, ACCELEROMETER_NOISE), addNoise(-GRAVITY, ACCELEROMETER_NOISE)),
            gyroscope = if (failedSensor == SensorType.GYROSCOPE) Vector3D.ZERO 
                       else Vector3D(addNoise(0.0f, GYROSCOPE_NOISE), addNoise(0.0f, GYROSCOPE_NOISE), addNoise(0.0f, GYROSCOPE_NOISE)),
            magnetometer = if (failedSensor == SensorType.MAGNETOMETER) Vector3D.ZERO 
                          else Vector3D(addNoise(25.0f, MAGNETOMETER_NOISE), addNoise(-8.0f, MAGNETOMETER_NOISE), addNoise(-15.0f, MAGNETOMETER_NOISE)),
            batteryLevel = updateBatteryLevel()
        )
    }

    /**
     * Generate biometric reading with heart rate sensor failure
     */
    fun generateBiometricReadingWithHeartRateFailure(
        sessionId: String,
        deviceId: String = "galaxy_watch_5"
    ): BiometricReading {
        currentTimestamp += random.nextLong(800, 1200)
        
        return BiometricReading(
            timestamp = currentTimestamp,
            sessionId = sessionId,
            deviceId = deviceId,
            heartRate = null, // Heart rate sensor failed
            stepCount = stepCount,
            calories = totalCalories + random.nextFloat() * 0.1f,
            skinTemperature = 32.0f + random.nextFloat() * 2.0f,
            batteryLevel = currentBatteryLevel
        ).also {
            totalCalories = it.calories
        }
    }

    /**
     * Generate readings simulating rapid battery drain
     */
    fun generateReadingsWithRapidBatteryDrain(
        sessionId: String,
        deviceId: String = "galaxy_watch_5"
    ): InertialReading {
        // Simulate rapid battery drain (e.g., due to high sensor usage)
        currentBatteryLevel = maxOf(0, currentBatteryLevel - random.nextInt(1, 4))
        return generateWristStationaryInertialReading(sessionId, deviceId)
    }

    /**
     * Generate a batch of readings for high-frequency data collection testing
     */
    fun generateHighFrequencyInertialBatch(
        sessionId: String,
        count: Int,
        deviceId: String = "galaxy_watch_5"
    ): List<InertialReading> {
        return (1..count).map {
            generateWristStationaryInertialReading(sessionId, deviceId)
        }
    }

    /**
     * Generate readings with timestamp inconsistencies for error testing
     */
    fun generateReadingWithTimestampError(
        sessionId: String,
        deviceId: String = "galaxy_watch_5"
    ): InertialReading {
        // Generate reading with timestamp in the past (simulating clock issues)
        val errorReading = generateWristStationaryInertialReading(sessionId, deviceId)
        return errorReading.copy(timestamp = currentTimestamp - random.nextLong(10000, 60000))
    }

    /**
     * Generate readings for buffer overflow testing
     */
    fun generateBufferOverflowTestData(
        sessionId: String,
        batchSize: Int,
        deviceId: String = "galaxy_watch_5"
    ): List<InertialReading> {
        return (1..batchSize).map { index ->
            // Simulate varying motion patterns
            when (index % 3) {
                0 -> generateWristStationaryInertialReading(sessionId, deviceId)
                1 -> generateWristWalkingInertialReading(sessionId, deviceId)
                else -> generateWristRunningInertialReading(sessionId, deviceId)
            }
        }
    }

    /**
     * Create a mock session for wearable testing
     */
    fun createMockWearableSession(deviceId: String = "galaxy_watch_5"): SessionData {
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
     * Update battery level with realistic drain for wearable device
     */
    private fun updateBatteryLevel(): Int {
        val elapsedMinutes = (currentTimestamp - sessionStartTime) / 60000.0
        val drainAmount = (elapsedMinutes * BATTERY_DRAIN_RATE).toInt()
        currentBatteryLevel = maxOf(0, 100 - drainAmount)
        return currentBatteryLevel
    }

    enum class SensorType {
        ACCELEROMETER,
        GYROSCOPE,
        MAGNETOMETER
    }

    enum class WearableMotionType {
        STATIONARY,
        WALKING,
        RUNNING,
        MIXED
    }

    enum class WearableActivityType {
        RESTING,
        LIGHT_ACTIVITY,
        MODERATE_ACTIVITY,
        VIGOROUS_ACTIVITY
    }
}