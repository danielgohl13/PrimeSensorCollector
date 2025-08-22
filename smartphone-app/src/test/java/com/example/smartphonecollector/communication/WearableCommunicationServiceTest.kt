package com.example.smartphonecollector.communication

import android.content.Context
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.data.serialization.DataSerializer
import com.google.android.gms.wearable.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WearableCommunicationServiceTest {

    private lateinit var context: Context
    private lateinit var mockMessageClient: MessageClient
    private lateinit var mockDataClient: DataClient
    private lateinit var mockNodeClient: NodeClient
    private lateinit var mockCapabilityClient: CapabilityClient
    
    private var receivedInertialData: InertialReading? = null
    private var receivedBiometricData: BiometricReading? = null
    
    private lateinit var service: WearableCommunicationService

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        
        // Mock Wearable API clients
        mockMessageClient = mockk(relaxed = true)
        mockDataClient = mockk(relaxed = true)
        mockNodeClient = mockk(relaxed = true)
        mockCapabilityClient = mockk(relaxed = true)
        
        // Mock static Wearable methods
        mockkStatic(Wearable::class)
        every { Wearable.getMessageClient(any()) } returns mockMessageClient
        every { Wearable.getDataClient(any()) } returns mockDataClient
        every { Wearable.getNodeClient(any()) } returns mockNodeClient
        every { Wearable.getCapabilityClient(any()) } returns mockCapabilityClient
        
        // Reset received data
        receivedInertialData = null
        receivedBiometricData = null
        
        // Create service with callbacks
        service = WearableCommunicationService(
            context = context,
            onInertialDataReceived = { receivedInertialData = it },
            onBiometricDataReceived = { receivedBiometricData = it }
        )
    }

    @After
    fun cleanup() {
        service.cleanup()
        unmockkAll()
    }

    private fun createSampleInertialReading(): InertialReading {
        return InertialReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            accelerometer = Vector3D(0.1f, 0.2f, 9.8f),
            gyroscope = Vector3D(0.01f, 0.02f, 0.03f),
            magnetometer = Vector3D(45.0f, -12.0f, 8.0f),
            batteryLevel = 85
        )
    }

    private fun createSampleBiometricReading(): BiometricReading {
        return BiometricReading(
            timestamp = System.currentTimeMillis(),
            sessionId = "test_session",
            deviceId = "test_device",
            heartRate = 72,
            stepCount = 1000,
            calories = 50.5f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )
    }

    @Test
    fun `service initializes with correct connection status`() {
        assertEquals(ConnectionStatus.DISCONNECTED, service.connectionStatus.value)
    }

    @Test
    fun `service registers listeners on initialization`() {
        verify { mockMessageClient.addListener(service) }
        verify { mockDataClient.addListener(service) }
    }

    @Test
    fun `cleanup removes listeners`() {
        service.cleanup()
        
        verify { mockMessageClient.removeListener(service) }
        verify { mockDataClient.removeListener(service) }
    }

    @Test
    fun `onMessageReceived handles inertial data path`() {
        val inertialReading = createSampleInertialReading()
        val serializedData = DataSerializer.serializeInertialReadingToBytes(inertialReading)
        
        val messageEvent = mockk<MessageEvent> {
            every { path } returns "/inertial_data"
            every { data } returns serializedData
        }
        
        service.onMessageReceived(messageEvent)
        
        assertNotNull("Inertial data should be received", receivedInertialData)
        assertEquals("Session ID should match", inertialReading.sessionId, receivedInertialData?.sessionId)
        assertEquals("Device ID should match", inertialReading.deviceId, receivedInertialData?.deviceId)
    }

    @Test
    fun `onMessageReceived handles biometric data path`() {
        val biometricReading = createSampleBiometricReading()
        val serializedData = DataSerializer.serializeBiometricReadingToBytes(biometricReading)
        
        val messageEvent = mockk<MessageEvent> {
            every { path } returns "/biometric_data"
            every { data } returns serializedData
        }
        
        service.onMessageReceived(messageEvent)
        
        assertNotNull("Biometric data should be received", receivedBiometricData)
        assertEquals("Session ID should match", biometricReading.sessionId, receivedBiometricData?.sessionId)
        assertEquals("Heart rate should match", biometricReading.heartRate, receivedBiometricData?.heartRate)
    }

    @Test
    fun `onMessageReceived ignores unknown paths`() {
        val messageEvent = mockk<MessageEvent> {
            every { path } returns "/unknown_path"
            every { data } returns byteArrayOf()
        }
        
        service.onMessageReceived(messageEvent)
        
        assertNull("No inertial data should be received", receivedInertialData)
        assertNull("No biometric data should be received", receivedBiometricData)
    }

    @Test
    fun `onMessageReceived handles deserialization errors gracefully`() {
        val messageEvent = mockk<MessageEvent> {
            every { path } returns "/inertial_data"
            every { data } returns byteArrayOf(1, 2, 3) // Invalid data
        }
        
        // Should not throw exception
        service.onMessageReceived(messageEvent)
        
        assertNull("No inertial data should be received on error", receivedInertialData)
    }

    @Test
    fun `onDataChanged handles data events correctly`() {
        val mockDataItem = mockk<DataItem> {
            every { uri.path } returns "/inertial_data"
        }
        
        val mockDataEvent = mockk<DataEvent> {
            every { type } returns DataEvent.TYPE_CHANGED
            every { dataItem } returns mockDataItem
        }
        
        val mockDataEventBuffer = mockk<DataEventBuffer>(relaxed = true) {
            every { iterator() } returns object : MutableIterator<DataEvent> {
                private val events = listOf(mockDataEvent)
                private var index = 0
                
                override fun hasNext(): Boolean = index < events.size
                override fun next(): DataEvent = events[index++]
                override fun remove() {}
            }
        }
        
        val mockDataMap = mockk<DataMap>(relaxed = true)
        val mockDataMapItem = mockk<DataMapItem> {
            every { dataMap } returns mockDataMap
        }
        
        mockkStatic(DataMapItem::class)
        every { DataMapItem.fromDataItem(mockDataItem) } returns mockDataMapItem
        
        // Mock DataSerializer to avoid actual deserialization
        mockkObject(DataSerializer)
        every { DataSerializer.deserializeInertialReadingFromDataMap(mockDataMap) } returns createSampleInertialReading()
        
        service.onDataChanged(mockDataEventBuffer)
        
        assertNotNull("Inertial data should be received from DataMap", receivedInertialData)
    }

    @Test
    fun `connection status flow is accessible`() {
        val status = service.connectionStatus.value
        
        assertTrue("Connection status should be a valid enum value", 
            status in listOf(ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR))
    }

    // Note: Testing actual network communication methods (startCollection, stopCollection, checkConnectionStatus)
    // requires more complex mocking of Google Play Services Tasks and would be better suited for integration tests
    // with actual devices or emulators. The current tests focus on the core message handling logic.

    @Test
    fun `service handles multiple message events in sequence`() {
        val inertialReading = createSampleInertialReading()
        val biometricReading = createSampleBiometricReading()
        
        val inertialEvent = mockk<MessageEvent> {
            every { path } returns "/inertial_data"
            every { data } returns DataSerializer.serializeInertialReadingToBytes(inertialReading)
        }
        
        val biometricEvent = mockk<MessageEvent> {
            every { path } returns "/biometric_data"
            every { data } returns DataSerializer.serializeBiometricReadingToBytes(biometricReading)
        }
        
        service.onMessageReceived(inertialEvent)
        service.onMessageReceived(biometricEvent)
        
        assertNotNull("Inertial data should be received", receivedInertialData)
        assertNotNull("Biometric data should be received", receivedBiometricData)
        assertEquals("Inertial session ID should match", inertialReading.sessionId, receivedInertialData?.sessionId)
        assertEquals("Biometric session ID should match", biometricReading.sessionId, receivedBiometricData?.sessionId)
    }
}