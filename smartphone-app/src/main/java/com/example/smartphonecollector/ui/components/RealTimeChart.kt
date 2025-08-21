package com.example.smartphonecollector.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartphonecollector.data.models.BiometricReading
import com.example.smartphonecollector.data.models.InertialReading
import com.example.smartphonecollector.data.models.Vector3D
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time chart component for live sensor data visualization
 * Requirements: 5.1, 5.2 - Real-time data visualization
 */
@Composable
fun RealTimeChart(
    inertialData: InertialReading?,
    biometricData: BiometricReading?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Real-Time Sensor Data",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Accelerometer Chart
            inertialData?.let { data ->
                AccelerometerChart(
                    accelerometer = data.accelerometer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Gyroscope Chart
            inertialData?.let { data ->
                GyroscopeChart(
                    gyroscope = data.gyroscope,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Magnetometer Chart
            inertialData?.let { data ->
                MagnetometerChart(
                    magnetometer = data.magnetometer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Biometric Data Display
            biometricData?.let { data ->
                BiometricDataDisplay(
                    biometricData = data,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Accelerometer data visualization
 */
@Composable
private fun AccelerometerChart(
    accelerometer: Vector3D,
    modifier: Modifier = Modifier
) {
    SensorChart(
        title = "Accelerometer (m/s²)",
        xValue = accelerometer.x,
        yValue = accelerometer.y,
        zValue = accelerometer.z,
        xColor = Color(0xFFE53E3E), // Red
        yColor = Color(0xFF38A169), // Green
        zColor = Color(0xFF3182CE), // Blue
        range = 20f, // ±20 m/s²
        modifier = modifier
    )
}

/**
 * Gyroscope data visualization
 */
@Composable
private fun GyroscopeChart(
    gyroscope: Vector3D,
    modifier: Modifier = Modifier
) {
    SensorChart(
        title = "Gyroscope (rad/s)",
        xValue = gyroscope.x,
        yValue = gyroscope.y,
        zValue = gyroscope.z,
        xColor = Color(0xFFE53E3E), // Red
        yColor = Color(0xFF38A169), // Green
        zColor = Color(0xFF3182CE), // Blue
        range = 10f, // ±10 rad/s
        modifier = modifier
    )
}

/**
 * Magnetometer data visualization
 */
@Composable
private fun MagnetometerChart(
    magnetometer: Vector3D,
    modifier: Modifier = Modifier
) {
    SensorChart(
        title = "Magnetometer (μT)",
        xValue = magnetometer.x,
        yValue = magnetometer.y,
        zValue = magnetometer.z,
        xColor = Color(0xFFE53E3E), // Red
        yColor = Color(0xFF38A169), // Green
        zColor = Color(0xFF3182CE), // Blue
        range = 100f, // ±100 μT
        modifier = modifier
    )
}

/**
 * Generic sensor chart for 3-axis data
 */
@Composable
private fun SensorChart(
    title: String,
    xValue: Float,
    yValue: Float,
    zValue: Float,
    xColor: Color,
    yColor: Color,
    zColor: Color,
    range: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        // Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawSensorChart(
                    xValue = xValue,
                    yValue = yValue,
                    zValue = zValue,
                    xColor = xColor,
                    yColor = yColor,
                    zColor = zColor,
                    range = range
                )
            }
        }
        
        // Legend and Values
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem("X", xValue, xColor)
            LegendItem("Y", yValue, yColor)
            LegendItem("Z", zValue, zColor)
        }
    }
}

/**
 * Draw sensor chart on canvas
 */
private fun DrawScope.drawSensorChart(
    xValue: Float,
    yValue: Float,
    zValue: Float,
    xColor: Color,
    yColor: Color,
    zColor: Color,
    range: Float
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2
    
    // Draw background grid
    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val gridLines = 5
    
    for (i in 0..gridLines) {
        val y = (height / gridLines) * i
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1.dp.toPx()
        )
    }
    
    // Draw center line
    drawLine(
        color = Color.Gray,
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 2.dp.toPx()
    )
    
    // Calculate bar heights (normalized to chart height)
    val maxBarHeight = height * 0.4f
    val xBarHeight = (xValue / range) * maxBarHeight
    val yBarHeight = (yValue / range) * maxBarHeight
    val zBarHeight = (zValue / range) * maxBarHeight
    
    // Draw bars
    val barWidth = width / 6f
    val spacing = width / 4f
    
    drawBar(centerY, spacing * 1, xBarHeight, barWidth, xColor)
    drawBar(centerY, spacing * 2, yBarHeight, barWidth, yColor)
    drawBar(centerY, spacing * 3, zBarHeight, barWidth, zColor)
}

/**
 * Draw individual bar on canvas
 */
private fun DrawScope.drawBar(
    centerY: Float,
    x: Float,
    height: Float,
    width: Float,
    color: Color
) {
    val startY = if (height >= 0) centerY - height else centerY
    val endY = if (height >= 0) centerY else centerY - height
    
    drawRect(
        color = color,
        topLeft = Offset(x - width/2, startY),
        size = androidx.compose.ui.geometry.Size(width, abs(height))
    )
}

/**
 * Legend item showing axis label, value, and color
 */
@Composable
private fun LegendItem(
    axis: String,
    value: Float,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = color)
            }
        }
        Text(
            text = "$axis: ${String.format("%.2f", value)}",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp
        )
    }
}

/**
 * Biometric data display
 */
@Composable
private fun BiometricDataDisplay(
    biometricData: BiometricReading,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Biometric Data",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BiometricItem(
                label = "Heart Rate",
                value = biometricData.heartRate?.toString() ?: "N/A",
                unit = "bpm"
            )
            BiometricItem(
                label = "Steps",
                value = biometricData.stepCount.toString(),
                unit = ""
            )
            BiometricItem(
                label = "Calories",
                value = String.format("%.1f", biometricData.calories),
                unit = "cal"
            )
        }
    }
}

/**
 * Individual biometric data item
 */
@Composable
private fun BiometricItem(
    label: String,
    value: String,
    unit: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$value $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RealTimeChartPreview() {
    PrimeSensorCollectorTheme {
        RealTimeChart(
            inertialData = InertialReading(
                timestamp = System.currentTimeMillis(),
                sessionId = "session_123",
                deviceId = "galaxy_watch_5",
                accelerometer = Vector3D(2.5f, -1.2f, 9.8f),
                gyroscope = Vector3D(0.1f, -0.05f, 0.02f),
                magnetometer = Vector3D(45.2f, -12.3f, 8.7f),
                batteryLevel = 85
            ),
            biometricData = BiometricReading(
                timestamp = System.currentTimeMillis(),
                sessionId = "session_123",
                deviceId = "galaxy_watch_5",
                heartRate = 72,
                stepCount = 1250,
                calories = 45.2f,
                skinTemperature = 32.1f,
                batteryLevel = 85
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}