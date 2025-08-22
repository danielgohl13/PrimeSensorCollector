package com.example.smartphonecollector.data.models

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sqrt

class Vector3DTest {

    @Test
    fun `constructor creates vector with correct values`() {
        val vector = Vector3D(1.0f, 2.0f, 3.0f)
        
        assertEquals(1.0f, vector.x, 0.001f)
        assertEquals(2.0f, vector.y, 0.001f)
        assertEquals(3.0f, vector.z, 0.001f)
    }

    @Test
    fun `ZERO constant has all zero values`() {
        val zero = Vector3D.ZERO
        
        assertEquals(0.0f, zero.x, 0.001f)
        assertEquals(0.0f, zero.y, 0.001f)
        assertEquals(0.0f, zero.z, 0.001f)
    }

    @Test
    fun `magnitude calculates correct value for positive components`() {
        val vector = Vector3D(3.0f, 4.0f, 0.0f)
        val expectedMagnitude = 5.0f // 3-4-5 triangle
        
        assertEquals(expectedMagnitude, vector.magnitude(), 0.001f)
    }

    @Test
    fun `magnitude calculates correct value for negative components`() {
        val vector = Vector3D(-3.0f, -4.0f, 0.0f)
        val expectedMagnitude = 5.0f
        
        assertEquals(expectedMagnitude, vector.magnitude(), 0.001f)
    }

    @Test
    fun `magnitude calculates correct value for 3D vector`() {
        val vector = Vector3D(1.0f, 2.0f, 2.0f)
        val expectedMagnitude = sqrt(1.0f + 4.0f + 4.0f) // sqrt(9) = 3
        
        assertEquals(expectedMagnitude, vector.magnitude(), 0.001f)
    }

    @Test
    fun `magnitude returns zero for zero vector`() {
        val vector = Vector3D.ZERO
        
        assertEquals(0.0f, vector.magnitude(), 0.001f)
    }

    @Test
    fun `data class equality works correctly`() {
        val vector1 = Vector3D(1.0f, 2.0f, 3.0f)
        val vector2 = Vector3D(1.0f, 2.0f, 3.0f)
        val vector3 = Vector3D(1.0f, 2.0f, 4.0f)
        
        assertEquals(vector1, vector2)
        assertNotEquals(vector1, vector3)
    }

    @Test
    fun `data class copy works correctly`() {
        val original = Vector3D(1.0f, 2.0f, 3.0f)
        val copy = original.copy(z = 4.0f)
        
        assertEquals(1.0f, copy.x, 0.001f)
        assertEquals(2.0f, copy.y, 0.001f)
        assertEquals(4.0f, copy.z, 0.001f)
        assertNotEquals(original, copy)
    }
}