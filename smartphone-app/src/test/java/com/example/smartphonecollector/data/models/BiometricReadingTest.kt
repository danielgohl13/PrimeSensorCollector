package com.example.smartphonecollector.data.models

import org.junit.Test
import org.junit.Assert.*

class BiometricReadingTest {

    private fun createSampleReading(): BiometricReading {
        return BiometricReading(
            timestamp = 1640995200000L,
            sessionId = "session_001",
            deviceId = "watch_001",
            heartRate = 72,
            stepCount = 1250,
            calories = 45.2f,
            skinTemperature = 32.1f,
            batteryLevel = 85
        )
    }

    @Test
    fun `constructor creates reading with correct values`() {
        val reading = createSampleReading()
        
        assertEquals(1640995200000L, reading.timestamp)
        assertEquals("session_001", reading.sessionId)
        assertEquals("watch_001", reading.deviceId)
        assertEquals(72, reading.heartRate)
        assertEquals(1250, reading.stepCount)
        assertEquals(45.2f, reading.calories, 0.001f)
        assertEquals(32.1f, reading.skinTemperature!!, 0.001f)
        assertEquals(85, reading.batteryLevel)
    }

    @Test
    fun `constructor handles null heart rate`() {
        val reading = createSampleReading().copy(heartRate = null)
        
        assertNull(reading.heartRate)
    }

    @Test
    fun `constructor handles null skin temperature`() {
        val reading = createSampleReading().copy(skinTemperature = null)
        
        assertNull(reading.skinTemperature)
    }

    @Test
    fun `csvHeader returns correct header format`() {
        val expectedHeader = "timestamp,session_id,device_id,heart_rate,step_count,calories,skin_temp,battery_level"
        
        assertEquals(expectedHeader, BiometricReading.csvHeader())
    }

    @Test
    fun `toCsvRow returns correct CSV format with all values`() {
        val reading = createSampleReading()
        val expectedCsv = "1640995200000,session_001,watch_001,72,1250,45.2,32.1,85"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `toCsvRow handles null heart rate correctly`() {
        val reading = createSampleReading().copy(heartRate = null)
        val expectedCsv = "1640995200000,session_001,watch_001,,1250,45.2,32.1,85"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `toCsvRow handles null skin temperature correctly`() {
        val reading = createSampleReading().copy(skinTemperature = null)
        val expectedCsv = "1640995200000,session_001,watch_001,72,1250,45.2,,85"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `toCsvRow handles both null values correctly`() {
        val reading = createSampleReading().copy(heartRate = null, skinTemperature = null)
        val expectedCsv = "1640995200000,session_001,watch_001,,1250,45.2,,85"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `toCsvRow handles zero values correctly`() {
        val reading = BiometricReading(
            timestamp = 0L,
            sessionId = "",
            deviceId = "",
            heartRate = 0,
            stepCount = 0,
            calories = 0.0f,
            skinTemperature = 0.0f,
            batteryLevel = 0
        )
        val expectedCsv = "0,,0,0,0.0,0.0,0"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `data class equality works correctly`() {
        val reading1 = createSampleReading()
        val reading2 = createSampleReading()
        val reading3 = reading1.copy(heartRate = 80)
        
        assertEquals(reading1, reading2)
        assertNotEquals(reading1, reading3)
    }

    @Test
    fun `heart rate validation - valid range`() {
        val reading = createSampleReading().copy(heartRate = 75)
        assertTrue("Heart rate should be in valid range", reading.heartRate!! in 30..220)
    }

    @Test
    fun `step count validation - non-negative`() {
        val reading = createSampleReading()
        assertTrue("Step count should be non-negative", reading.stepCount >= 0)
    }

    @Test
    fun `calories validation - non-negative`() {
        val reading = createSampleReading()
        assertTrue("Calories should be non-negative", reading.calories >= 0.0f)
    }

    @Test
    fun `battery level validation - valid range`() {
        val reading = createSampleReading()
        assertTrue("Battery level should be valid", reading.batteryLevel in 0..100)
    }
}