package com.example.smartphonecollector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme

/**
 * Session control buttons for starting and stopping data collection
 * Requirements: 1.1, 5.3 - Session control functionality
 */
@Composable
fun SessionControls(
    isCollecting: Boolean,
    connectionStatus: ConnectionStatus,
    onStartCollection: () -> Unit,
    onStopCollection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Session Controls",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start Collection Button
                Button(
                    onClick = onStartCollection,
                    enabled = !isCollecting && isConnected,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Collection",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Collection")
                }
                
                // Stop Collection Button
                Button(
                    onClick = onStopCollection,
                    enabled = isCollecting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop Collection",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Collection")
                }
            }
            
            // Status text
            Text(
                text = when {
                    !isConnected -> "Connect your Galaxy Watch 5 to start collection"
                    isCollecting -> "Data collection is active"
                    else -> "Ready to start data collection"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SessionControlsPreview() {
    PrimeSensorCollectorTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Not collecting, connected
            SessionControls(
                isCollecting = false,
                connectionStatus = ConnectionStatus.CONNECTED,
                onStartCollection = {},
                onStopCollection = {}
            )
            
            // Collecting, connected
            SessionControls(
                isCollecting = true,
                connectionStatus = ConnectionStatus.CONNECTED,
                onStartCollection = {},
                onStopCollection = {}
            )
            
            // Not collecting, disconnected
            SessionControls(
                isCollecting = false,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                onStartCollection = {},
                onStopCollection = {}
            )
        }
    }
}