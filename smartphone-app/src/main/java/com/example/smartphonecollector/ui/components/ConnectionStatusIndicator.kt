package com.example.smartphonecollector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme

/**
 * Connection status indicator showing wearable device connectivity
 * Requirements: 5.3 - Connection status display
 */
@Composable
fun ConnectionStatusIndicator(
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color, text) = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> Triple(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50), // Green
            "Connected to Galaxy Watch 5"
        )
        ConnectionStatus.CONNECTING -> Triple(
            Icons.Default.Refresh,
            Color(0xFFFF9800), // Orange
            "Connecting to Galaxy Watch 5..."
        )
        ConnectionStatus.DISCONNECTED -> Triple(
            Icons.Default.Close,
            Color(0xFFF44336), // Red
            "Disconnected - Check watch pairing"
        )
        ConnectionStatus.ERROR -> Triple(
            Icons.Default.Warning,
            Color(0xFFF44336), // Red
            "Connection Error - Retry needed"
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Connection Status",
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Watch Connection",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Connection strength indicator (for connected state)
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                ConnectionStrengthIndicator()
            }
        }
    }
}

/**
 * Visual indicator for connection strength
 */
@Composable
private fun ConnectionStrengthIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((8 + index * 3).dp)
                    .padding(vertical = 1.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {}
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorPreview() {
    PrimeSensorCollectorTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConnectionStatusIndicator(ConnectionStatus.CONNECTED)
            ConnectionStatusIndicator(ConnectionStatus.CONNECTING)
            ConnectionStatusIndicator(ConnectionStatus.DISCONNECTED)
            ConnectionStatusIndicator(ConnectionStatus.ERROR)
        }
    }
}