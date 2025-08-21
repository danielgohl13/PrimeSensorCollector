package com.example.smartphonecollector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartphonecollector.data.models.SessionData
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Session information display showing current session details
 * Requirements: 5.3 - Session information display
 */
@Composable
fun SessionInformation(
    sessionData: SessionData?,
    dataPointsCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session Information",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (sessionData != null) {
                ActiveSessionInfo(
                    sessionData = sessionData,
                    dataPointsCount = dataPointsCount
                )
            } else {
                NoActiveSessionInfo()
            }
        }
    }
}

/**
 * Display information for an active session
 */
@Composable
private fun ActiveSessionInfo(
    sessionData: SessionData,
    dataPointsCount: Int
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }
    
    // Live duration counter for active sessions
    var currentDuration by remember { mutableLongStateOf(sessionData.getDuration()) }
    
    LaunchedEffect(sessionData.isActive) {
        if (sessionData.isActive) {
            while (true) {
                currentDuration = sessionData.getDuration()
                delay(1000) // Update every second
            }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Session ID
        InfoRow(
            icon = Icons.Default.Person,
            label = "Session ID",
            value = sessionData.sessionId
        )
        
        // Start Time
        InfoRow(
            icon = Icons.Default.Info,
            label = "Started",
            value = dateFormatter.format(Date(sessionData.startTime))
        )
        
        // Duration
        InfoRow(
            icon = Icons.Default.Info,
            label = "Duration",
            value = formatDuration(currentDuration)
        )
        
        // Data Points Collected
        InfoRow(
            icon = Icons.Default.Info,
            label = "Data Points",
            value = dataPointsCount.toString()
        )
        
        // Device ID
        InfoRow(
            icon = Icons.Default.Info,
            label = "Device",
            value = sessionData.deviceId
        )
        
        // Status
        InfoRow(
            icon = if (sessionData.isActive) Icons.Default.PlayArrow else Icons.Default.CheckCircle,
            label = "Status",
            value = if (sessionData.isActive) "Active" else "Completed"
        )
    }
}

/**
 * Display message when no session is active
 */
@Composable
private fun NoActiveSessionInfo() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No Session",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "No active session",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Reusable info row component
 */
@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Format duration in milliseconds to human-readable format
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))
    
    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%02d:%02d", minutes, seconds)
    }
}

@Preview(showBackground = true)
@Composable
fun SessionInformationPreview() {
    PrimeSensorCollectorTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active session
            SessionInformation(
                sessionData = SessionData(
                    sessionId = "session_abc123",
                    startTime = System.currentTimeMillis() - 120000, // 2 minutes ago
                    endTime = null,
                    isActive = true,
                    dataPointsCollected = 150,
                    deviceId = "galaxy_watch_5"
                ),
                dataPointsCount = 150
            )
            
            // No active session
            SessionInformation(
                sessionData = null,
                dataPointsCount = 0
            )
        }
    }
}