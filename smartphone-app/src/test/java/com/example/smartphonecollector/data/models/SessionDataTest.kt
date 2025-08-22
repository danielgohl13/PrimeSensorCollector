package com.example.smartphonecollector.data.models

import org.junit.Test
import org.junit.Assert.*

class SessionDataTest {

    private fun createSampleSession(): SessionData {
        return SessionData(
            sessionId = "session_12345678",
            startTime = 1640995200000L,
            endTime = null,
            isActive = true,
            dataPointsCollected = 100,
            deviceId = "watch_001"
        )
    }

    @Test
    fun `constructor creates session with correct values`() {
        val session = createSampleSession()
        
        assertEquals("session_12345678", session.sessionId)
        assertEquals(1640995200000L, session.startTime)
        assertNull(session.endTime)
        assertTrue(session.isActive)
        assertEquals(100, session.dataPointsCollected)
        assertEquals("watch_001", session.deviceId)
    }

    @Test
    fun `createNew generates session with unique ID`() {
        val session1 = SessionData.createNew("device1")
        val session2 = SessionData.createNew("device2")
        
        assertNotEquals(session1.sessionId, session2.sessionId)
        assertTrue("Session ID should start with 'session_'", session1.sessionId.startsWith("session_"))
        assertTrue("Session ID should start with 'session_'", session2.sessionId.startsWith("session_"))
    }

    @Test
    fun `createNew creates active session`() {
        val session = SessionData.createNew("device1")
        
        assertTrue("New session should be active", session.isActive)
        assertNull("New session should not have end time", session.endTime)
        assertEquals("New session should have zero data points", 0, session.dataPointsCollected)
        assertTrue("Start time should be recent", System.currentTimeMillis() - session.startTime < 1000)
    }

    @Test
    fun `complete marks session as finished`() {
        val activeSession = createSampleSession()
        val completedSession = activeSession.complete()
        
        assertFalse("Completed session should not be active", completedSession.isActive)
        assertNotNull("Completed session should have end time", completedSession.endTime)
        assertTrue("End time should be recent", System.currentTimeMillis() - completedSession.endTime!! < 1000)
        
        // Other properties should remain unchanged
        assertEquals(activeSession.sessionId, completedSession.sessionId)
        assertEquals(activeSession.startTime, completedSession.startTime)
        assertEquals(activeSession.dataPointsCollected, completedSession.dataPointsCollected)
        assertEquals(activeSession.deviceId, completedSession.deviceId)
    }

    @Test
    fun `updateDataPoints changes count correctly`() {
        val session = createSampleSession()
        val updatedSession = session.updateDataPoints(250)
        
        assertEquals(250, updatedSession.dataPointsCollected)
        
        // Other properties should remain unchanged
        assertEquals(session.sessionId, updatedSession.sessionId)
        assertEquals(session.startTime, updatedSession.startTime)
        assertEquals(session.endTime, updatedSession.endTime)
        assertEquals(session.isActive, updatedSession.isActive)
        assertEquals(session.deviceId, updatedSession.deviceId)
    }

    @Test
    fun `getDuration calculates correctly for active session`() {
        val currentTime = System.currentTimeMillis()
        val session = createSampleSession().copy(startTime = currentTime - 5000) // 5 seconds ago
        
        val duration = session.getDuration()
        assertTrue("Duration should be approximately 5 seconds", duration >= 4900 && duration <= 5100)
    }

    @Test
    fun `getDuration calculates correctly for completed session`() {
        val startTime = 1640995200000L
        val endTime = 1640995205000L // 5 seconds later
        val session = createSampleSession().copy(
            startTime = startTime,
            endTime = endTime,
            isActive = false
        )
        
        val duration = session.getDuration()
        assertEquals(5000L, duration)
    }

    @Test
    fun `getDuration handles zero duration`() {
        val time = System.currentTimeMillis()
        val session = createSampleSession().copy(startTime = time, endTime = time)
        
        val duration = session.getDuration()
        assertEquals(0L, duration)
    }

    @Test
    fun `data class equality works correctly`() {
        val session1 = createSampleSession()
        val session2 = createSampleSession()
        val session3 = session1.copy(dataPointsCollected = 200)
        
        assertEquals(session1, session2)
        assertNotEquals(session1, session3)
    }

    @Test
    fun `session ID format validation`() {
        val session = SessionData.createNew("device1")
        
        assertTrue("Session ID should contain underscore", session.sessionId.contains("_"))
        assertTrue("Session ID should be reasonable length", session.sessionId.length > 10)
    }

    @Test
    fun `data points count validation`() {
        val session = createSampleSession()
        
        assertTrue("Data points should be non-negative", session.dataPointsCollected >= 0)
    }

    @Test
    fun `start time validation`() {
        val session = createSampleSession()
        
        assertTrue("Start time should be positive", session.startTime > 0)
    }
}