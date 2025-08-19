package com.example.smartphonecollector.data.models

import kotlinx.serialization.Serializable

/**
 * Represents a 3D vector with x, y, z components
 * Used for accelerometer, gyroscope, and magnetometer readings
 */
@Serializable
data class Vector3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = Vector3D(0f, 0f, 0f)
    }
    
    /**
     * Calculate the magnitude of the vector
     */
    fun magnitude(): Float {
        return kotlin.math.sqrt(x * x + y * y + z * z)
    }
}