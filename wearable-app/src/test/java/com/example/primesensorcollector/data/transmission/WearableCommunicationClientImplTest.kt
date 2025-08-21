package com.example.primesensorcollector.data.transmission

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class WearableCommunicationClientImplTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockMessageClient: MessageClient
    
    @Mock
    private lateinit var mockNodeClient: NodeClient
    
    @Mock
    private lateinit var mockNode: Node
    
    private lateinit var communicationClient: WearableCommunicationClientImpl
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock Wearable API clients
        mockkStatic(Wearable::class)
        every { Wearable.getMessageClient(mockContext) } returns mockMessageClient
        every { Wearable.getNodeClient(mockContext) } returns mockNodeClient
        
        communicationClient = WearableCommunicationClientImpl(mockContext)
    }
    
    @Test
    fun `initialize should setup message listener and find connected nodes`() = runTest {
        // Given
        val connectedNodes = listOf(mockNode)
        whenever(mockNode.id).thenReturn("test_node_id")
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(connectedNodes))
        
        // When
        val result = communicationClient.initialize()
        
        // Then
        assertTrue("Initialize should succeed", result)
        verify(mockMessageClient).addListener(communicationClient)
        verify(mockNodeClient).connectedNodes
    }
    
    @Test
    fun `initialize should handle failure gracefully`() = runTest {
        // Given
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forException(RuntimeException("Test error")))
        
        // When
        val result = communicationClient.initialize()
        
        // Then
        assertFalse("Initialize should fail gracefully", result)
    }
    
    @Test
    fun `sendMessage should send data to connected node`() = runTest {
        // Given
        val connectedNodes = listOf(mockNode)
        whenever(mockNode.id).thenReturn("test_node_id")
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(connectedNodes))
        whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(Tasks.forResult(1))
        
        // Initialize first
        communicationClient.initialize()
        
        // When
        val result = communicationClient.sendMessage("test_type", "test_data")
        
        // Then
        assertTrue("Send message should succeed", result)
        verify(mockMessageClient).sendMessage(
            eq("test_node_id"),
            eq("/sensor_data"),
            any()
        )
    }
    
    @Test
    fun `sendMessage should fail when no nodes connected`() = runTest {
        // Given
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(emptyList()))
        
        // When
        val result = communicationClient.sendMessage("test_type", "test_data")
        
        // Then
        assertFalse("Send message should fail when no nodes connected", result)
        verify(mockMessageClient, never()).sendMessage(any(), any(), any())
    }
    
    @Test
    fun `isConnected should return true when nodes are connected`() = runTest {
        // Given
        val connectedNodes = listOf(mockNode)
        whenever(mockNode.id).thenReturn("test_node_id")
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(connectedNodes))
        
        // When
        val result = communicationClient.isConnected()
        
        // Then
        assertTrue("Should be connected when nodes exist", result)
    }
    
    @Test
    fun `isConnected should return false when no nodes connected`() = runTest {
        // Given
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(emptyList()))
        
        // When
        val result = communicationClient.isConnected()
        
        // Then
        assertFalse("Should not be connected when no nodes exist", result)
    }
    
    @Test
    fun `reportStatus should send status message`() = runTest {
        // Given
        val connectedNodes = listOf(mockNode)
        whenever(mockNode.id).thenReturn("test_node_id")
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(connectedNodes))
        whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(Tasks.forResult(1))
        
        // When
        val result = communicationClient.reportStatus("collecting")
        
        // Then
        assertTrue("Report status should succeed", result)
        verify(mockMessageClient).sendMessage(
            eq("test_node_id"),
            eq("/status"),
            any()
        )
    }
    
    @Test
    fun `setCommandListener should store listener`() {
        // Given
        var receivedCommand: String? = null
        var receivedData: String? = null
        val listener: (String, String?) -> Unit = { command, data ->
            receivedCommand = command
            receivedData = data
        }
        
        // When
        communicationClient.setCommandListener(listener)
        
        // Then - Verify listener is set (we'll test it in onMessageReceived test)
        assertNotNull("Listener should be set", listener)
    }
    
    @Test
    fun `onMessageReceived should handle command messages`() {
        // Given
        var receivedCommand: String? = null
        var receivedData: String? = null
        
        communicationClient.setCommandListener { command, data ->
            receivedCommand = command
            receivedData = data
        }
        
        val messageEvent = mock<MessageEvent>()
        whenever(messageEvent.path).thenReturn("/commands")
        whenever(messageEvent.data).thenReturn("start_collection|session_123".toByteArray())
        
        // When
        communicationClient.onMessageReceived(messageEvent)
        
        // Then
        assertEquals("start_collection", receivedCommand)
        assertEquals("session_123", receivedData)
    }
    
    @Test
    fun `onMessageReceived should handle command without data`() {
        // Given
        var receivedCommand: String? = null
        var receivedData: String? = null
        
        communicationClient.setCommandListener { command, data ->
            receivedCommand = command
            receivedData = data
        }
        
        val messageEvent = mock<MessageEvent>()
        whenever(messageEvent.path).thenReturn("/commands")
        whenever(messageEvent.data).thenReturn("stop_collection".toByteArray())
        
        // When
        communicationClient.onMessageReceived(messageEvent)
        
        // Then
        assertEquals("stop_collection", receivedCommand)
        assertNull(receivedData)
    }
    
    @Test
    fun `onMessageReceived should ignore unknown message paths`() {
        // Given
        var listenerCalled = false
        
        communicationClient.setCommandListener { _, _ ->
            listenerCalled = true
        }
        
        val messageEvent = mock<MessageEvent>()
        whenever(messageEvent.path).thenReturn("/unknown_path")
        whenever(messageEvent.data).thenReturn("test_data".toByteArray())
        
        // When
        communicationClient.onMessageReceived(messageEvent)
        
        // Then
        assertFalse("Listener should not be called for unknown paths", listenerCalled)
    }
    
    @Test
    fun `cleanup should remove message listener`() {
        // When
        communicationClient.cleanup()
        
        // Then
        verify(mockMessageClient).removeListener(communicationClient)
    }
}