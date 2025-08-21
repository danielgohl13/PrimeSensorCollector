package com.example.primesensorcollector.service

import com.example.primesensorcollector.data.models.Vector3D
import org.junit.Assert.*
import org.junit.Test

/**
 * Simple unit tests for SensorCollectionService
 * Tests basic functionality without complex mocking
 */
class SensorCollectionServiceTest {
    
    @Test
    fun `service constants are defined correctly`() {
        assertEquals("START_COLLECTION", SensorCollectionService.ACTION_START_COLLECTION)
        assertEquals("STOP_COLLECTION", SensorCollectionService.ACTION_STOP_COLLECTION)
        assertEquals("session_id", SensorCollectionService.EXTRA_SESSION_ID)
        assertEquals("device_id", SensorCollectionService.EXTRA_DEVICE_ID)
    }
    
    @Test
    fun `vector3D zero constant is correct`() {
        val zero = Vector3D.ZERO
        assertEquals(0f, zero.x, 0.001f)
        assertEquals(0f, zero.y, 0.001f)
        assertEquals(0f, zero.z, 0.001f)
    }
    
    @Test
    fun `vector3D magnitude calculation works`() {
        val vector = Vector3D(3f, 4f, 0f)
        assertEquals(5f, vector.magnitude(), 0.001f)
    }
    
    @Test
    fun `service action constants are strings`() {
        // Simple test to verify constants are properly defined
        assertTrue(SensorCollectionService.ACTION_START_COLLECTION.isNotEmpty())
        assertTrue(SensorCollectionService.ACTION_STOP_COLLECTION.isNotEmpty())
        assertTrue(SensorCollectionService.EXTRA_SESSION_ID.isNotEmpty())
        assertTrue(SensorCollectionService.EXTRA_DEVICE_ID.isNotEmpty())
    }
}