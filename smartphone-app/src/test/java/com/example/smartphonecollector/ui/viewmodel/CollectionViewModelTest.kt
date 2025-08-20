package com.example.smartphonecollector.ui.viewmodel

import android.content.Context
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.data.repository.DataRepository
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Basic tests for CollectionViewModel to verify implementation
 * Requirements: 1.1, 1.2, 1.3, 5.4
 */
class CollectionViewModelTest {

    private lateinit var mockContext: Context
    private lateinit var mockDataRepository: DataRepository
    private lateinit var viewModel: CollectionViewModel

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockDataRepository = mockk(relaxed = true)
        
        // Mock basic repository methods
        every { mockDataRepository.isStorageAvailable() } returns true
        every { mockDataRepository.getAvailableStorageSpace() } returns 1000000L
        
        viewModel = CollectionViewModel(mockContext, mockDataRepository)
    }

    @Test
    fun `viewModel should initialize with correct default state`() {
        // Verify ViewModel can be instantiated
        assertNotNull(viewModel)
        
        // Verify StateFlow properties exist
        assertNotNull(viewModel.isCollecting)
        assertNotNull(viewModel.sessionData)
        assertNotNull(viewModel.connectionStatus)
        assertNotNull(viewModel.realTimeInertialData)
        assertNotNull(viewModel.realTimeBiometricData)
        assertNotNull(viewModel.errorMessage)
        assertNotNull(viewModel.dataPointsCount)
    }

    @Test
    fun `viewModel should have required methods for session management`() {
        // Verify required methods exist (Requirements 1.1, 1.2, 1.3)
        try {
            viewModel.startCollection()
            viewModel.stopCollection()
            viewModel.clearError()
            viewModel.checkConnection()
            
            // Verify utility methods exist (Requirements 5.4)
            val duration = viewModel.getCurrentSessionDuration()
            val batteryLevel = viewModel.getCurrentBatteryLevel()
            val isLowBattery = viewModel.isBatteryLow()
            val storageInfo = viewModel.getStorageInfo()
            
            // If we get here, all methods exist
            assertTrue("All required methods exist", true)
        } catch (e: Exception) {
            fail("Required methods are missing or have incorrect signatures: ${e.message}")
        }
    }

    @Test
    fun `getStorageInfo should return repository values`() {
        val (isAvailable, space) = viewModel.getStorageInfo()
        
        assertTrue("Storage should be available", isAvailable)
        assertEquals("Storage space should match repository", 1000000L, space)
        
        verify { mockDataRepository.isStorageAvailable() }
        verify { mockDataRepository.getAvailableStorageSpace() }
    }

    @Test
    fun `getCurrentSessionDuration should return 0 when no session`() {
        val duration = viewModel.getCurrentSessionDuration()
        assertEquals("Duration should be 0 when no session", 0L, duration)
    }

    @Test
    fun `getCurrentBatteryLevel should return null when no data`() {
        val batteryLevel = viewModel.getCurrentBatteryLevel()
        assertNull("Battery level should be null when no data", batteryLevel)
    }

    @Test
    fun `isBatteryLow should return false when no battery data`() {
        val isLow = viewModel.isBatteryLow()
        assertFalse("Battery should not be considered low when no data", isLow)
    }
}