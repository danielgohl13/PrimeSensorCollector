package com.example.smartphonecollector.data.models

import org.junit.Test
import org.junit.Assert.*

class InertialReadingTest {

    private fun createSampleReading(): InertialReading {
        return InertialReading(
            timestamp = 1640995200000L,
            sessionId = "session_001",
            deviceId = "watch_001",
            accelerometer = Vector3D(0.12f, -0.34f, 9.81f),
            gyroscope = Vector3D(0.01f, 0.02f, -0.01f),
            magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
            batteryLevel = 85
        )
    }

    @Test
    fun `constructor creates reading with correct values`() {
        val reading = createSampleReading()
        
        assertEquals(1640995200000L, reading.timestamp)
        assertEquals("session_001", reading.sessionId)
        assertEquals("watch_001", reading.deviceId)
        assertEquals(Vector3D(0.12f, -0.34f, 9.81f), reading.accelerometer)
        assertEquals(Vector3D(0.01f, 0.02f, -0.01f), reading.gyroscope)
        assertEquals(Vector3D(45.2f, -12.3f, 8.7f), reading.magnetometer)
        assertEquals(85, reading.batteryLevel)
    }

    @Test
    fun `csvHeader returns correct header format`() {
        val expectedHeader = "timestamp,session_id,device_id,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,battery_level"
        
        assertEquals(expectedHeader, InertialReading.csvHeader())
    }

    @Test
    fun `toCsvRow returns correct CSV format`() {
        val reading = createSampleReading()
        val expectedCsv = "1640995200000,session_001,watch_001,0.12,-0.34,9.81,0.01,0.02,-0.01,45.2,-12.3,8.7,85"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `toCsvRow handles zero values correctly`() {
        val reading = InertialReading(
            timestamp = 0L,
            sessionId = "",
            deviceId = "",
            accelerometer = Vector3D.ZERO,
            gyroscope = Vector3D.ZERO,
            magnetometer = Vector3D.ZERO,
            batteryLevel = 0
        )
        val expectedCsv = "0,,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `toCsvRow handles negative values correctly`() {
        val reading = InertialReading(
            timestamp = 1640995200000L,
            sessionId = "test",
            deviceId = "device",
            accelerometer = Vector3D(-1.0f, -2.0f, -3.0f),
            gyroscope = Vector3D(-0.1f, -0.2f, -0.3f),
            magnetometer = Vector3D(-10.0f, -20.0f, -30.0f),
            batteryLevel = 50
        )
        val expectedCsv = "1640995200000,test,device,-1.0,-2.0,-3.0,-0.1,-0.2,-0.3,-10.0,-20.0,-30.0,50"
        
        assertEquals(expectedCsv, reading.toCsvRow())
    }

    @Test
    fun `data class equality works correctly`() {
        val reading1 = createSampleReading()
        val reading2 = createSampleReading()
        val reading3 = reading1.copy(batteryLevel = 90)
        
        assertEquals(reading1, reading2)
        assertNotEquals(reading1, reading3)
    }

    @Test
    fun `battery level validation - valid range`() {
        val reading = createSampleReading().copy(batteryLevel = 50)
        assertTrue("Battery level should be valid", reading.batteryLevel in 0..100)
    }

    @Test
    fun `timestamp validation - positive value`() {
        val reading = createSampleReading()
        assertTrue("Timestamp should be positive", reading.timestamp > 0)
    }
}