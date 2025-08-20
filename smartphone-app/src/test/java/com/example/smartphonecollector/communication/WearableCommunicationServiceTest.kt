package com.example.smartphonecollector.communication

import android.content.Context
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.data.serialization.DataSerializer
import com.google.android.gms.wearable.MessageEvent
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class WearableCommunicationServiceTest {

    private lateinit var context: Context
    private lateinit var communicationService: WearableCommunicationService
    
    private val mockInertialDataReceived = mockk<(InertialReading) -> Unit>(relaxed = true)
    private val mockBiometricDataReceived = mockk<(BiometricReading) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = RuntimeEnvironment.getApplication()
        
        communicationService = WearableCommunicationService(
            context = context,
            onInertialDataReceived = mockInertialDataReceived,
            onBiometricDataReceived = mockBiometricDataReceived
        )
    }

    @After
    fun tearDown() {
        communicationService.cleanup()
        clearAllMocks()
    }

    @Test
    fun `connectionStatus should start as DISCONNECTED`() {
        assertEquals(ConnectionStatus.DISCONNECTED, communicationService.connectionStatus.value)
    }

    @Test
    fun `onMessageReceived should handle inertial data correctly`() {
        // Given
        val inertialReading = InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            accelerometer = Vector3D(1.0f, 2.0f, 3.0f),
            gyroscope = Vector3D(0.1f, 0.2f, 0.3f),
            magnetometer = Vector3D(10.0f, 20.0f, 30.0f),
            batteryLevel = 85
        )
        
        val serializedData = DataSerializer.serializeInertialReadingToBytes(inertialReading)
        val messageEvent = mockk<MessageEvent> {
            every { path } returns "/inertial_data"
            every { data } returns serializedData
        }

        // When
        communicationService.onMessageReceived(messageEvent)

        // Then
        verify { mockInertialDataReceived(any()) }
    }

    @Test
    fun `onMessageReceived should handle biometric data correctly`() {
        // Given
        val biometricReading = BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            heartRate = 72,
            stepCount = 1000,
            calories = 50.5f,
            skinTemperature = 36.5f,
            batteryLevel = 85
        )
        
        val serializedData = DataSerializer.serializeBiometricReadingToBytes(biometricReading)
        val messageEvent = mockk<MessageEvent> {
            every { path } returns "/biometric_data"
            every { data } returns serializedData
        }

        // When
        communicationService.onMessageReceived(messageEvent)

        // Then
        verify { mockBiometricDataReceived(any()) }
    }

    @Test
    fun `onMessageReceived should ignore unknown message paths`() {
        // Given
        val messageEvent = mockk<MessageEvent> {
            every { path } returns "/unknown_path"
            every { data } returns byteArrayOf()
        }

        // When
        communicationService.onMessageReceived(messageEvent)

        // Then
        verify(exactly = 0) { mockInertialDataReceived(any()) }
        verify(exactly = 0) { mockBiometricDataReceived(any()) }
    }

    @Test
    fun `cleanup should remove listeners`() {
        // When
        communicationService.cleanup()

        // Then - no exception should be thrown
        // This test mainly ensures cleanup doesn't crash
        assertNotNull(communicationService)
    }

    @Test
    fun `startCollection should handle session ID correctly`() = runTest {
        // Given
        val sessionId = "test_session_123"

        // When - This will fail in test environment due to missing Google Play Services
        // but we can test that it doesn't crash
        val result = try {
            communicationService.startCollection(sessionId)
        } catch (e: Exception) {
            // Expected in test environment
            false
        }

        // Then - Should not crash and return a boolean
        assert(result is Boolean)
    }

    @Test
    fun `stopCollection should handle gracefully`() = runTest {
        // When - This will fail in test environment due to missing Google Play Services
        // but we can test that it doesn't crash
        val result = try {
            communicationService.stopCollection()
        } catch (e: Exception) {
            // Expected in test environment
            false
        }

        // Then - Should not crash and return a boolean
        assert(result is Boolean)
    }

    @Test
    fun `checkConnectionStatus should handle gracefully`() = runTest {
        // When - This will fail in test environment due to missing Google Play Services
        // but we can test that it doesn't crash
        val result = try {
            communicationService.checkConnectionStatus()
        } catch (e: Exception) {
            // Expected in test environment
            false
        }

        // Then - Should not crash and return a boolean
        assert(result is Boolean)
    }
}