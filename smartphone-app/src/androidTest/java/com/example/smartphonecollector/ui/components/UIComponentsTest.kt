package com.example.smartphonecollector.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for UI components
 * Verifies that UI components can be composed without errors
 */
@RunWith(AndroidJUnit4::class)
class UIComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectionStatusIndicator_displaysCorrectly() {
        composeTestRule.setContent {
            PrimeSensorCollectorTheme {
                ConnectionStatusIndicator(
                    connectionStatus = ConnectionStatus.CONNECTED
                )
            }
        }
        
        // Verify the component renders without crashing
        composeTestRule.waitForIdle()
    }

    @Test
    fun sessionControls_displaysCorrectly() {
        composeTestRule.setContent {
            PrimeSensorCollectorTheme {
                SessionControls(
                    isCollecting = false,
                    connectionStatus = ConnectionStatus.CONNECTED,
                    onStartCollection = {},
                    onStopCollection = {}
                )
            }
        }
        
        // Verify the component renders without crashing
        composeTestRule.waitForIdle()
    }

    @Test
    fun sessionInformation_displaysCorrectly() {
        val sessionData = SessionData(
            sessionId = "test_session",
            startTime = System.currentTimeMillis(),
            endTime = null,
            isActive = true,
            dataPointsCollected = 100,
            deviceId = "test_device"
        )
        
        composeTestRule.setContent {
            PrimeSensorCollectorTheme {
                SessionInformation(
                    sessionData = sessionData,
                    dataPointsCount = 100
                )
            }
        }
        
        // Verify the component renders without crashing
        composeTestRule.waitForIdle()
    }

    @Test
    fun sessionInformation_displaysCorrectlyWithNoSession() {
        composeTestRule.setContent {
            PrimeSensorCollectorTheme {
                SessionInformation(
                    sessionData = null,
                    dataPointsCount = 0
                )
            }
        }
        
        // Verify the component renders without crashing
        composeTestRule.waitForIdle()
    }
}