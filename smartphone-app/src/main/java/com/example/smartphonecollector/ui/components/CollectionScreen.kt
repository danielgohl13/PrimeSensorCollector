package com.example.smartphonecollector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartphonecollector.communication.ConnectionStatus
import com.example.smartphonecollector.data.models.*
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme
import com.example.smartphonecollector.ui.viewmodel.CollectionViewModel

/**
 * Main collection screen with session controls and real-time data visualization
 * Requirements: 1.1, 5.1, 5.2, 5.3, 5.5
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    modifier: Modifier = Modifier
) {
    // Collect state from ViewModel
    val isCollecting by viewModel.isCollecting.collectAsStateWithLifecycle()
    val sessionData by viewModel.sessionData.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val realTimeInertialData by viewModel.realTimeInertialData.collectAsStateWithLifecycle()
    val realTimeBiometricData by viewModel.realTimeBiometricData.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val dataPointsCount by viewModel.dataPointsCount.collectAsStateWithLifecycle()

    // Check for low battery warning
    val isBatteryLow = viewModel.isBatteryLow()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title
        Text(
            text = "Wearable Data Collector",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Connection Status
        ConnectionStatusIndicator(
            connectionStatus = connectionStatus,
            onRetryClick = { viewModel.retryConnection() },
            modifier = Modifier.fillMaxWidth()
        )

        // Session Controls
        SessionControls(
            isCollecting = isCollecting,
            connectionStatus = connectionStatus,
            onStartCollection = { viewModel.startCollection() },
            onStopCollection = { viewModel.stopCollection() },
            modifier = Modifier.fillMaxWidth()
        )

        // Session Information
        SessionInformation(
            sessionData = sessionData,
            dataPointsCount = dataPointsCount,
            modifier = Modifier.fillMaxWidth()
        )

        // Battery Warning
        if (isBatteryLow) {
            BatteryWarning(
                batteryLevel = viewModel.getCurrentBatteryLevel() ?: 0,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Real-time Data Visualization
        if (isCollecting && realTimeInertialData != null) {
            RealTimeChart(
                inertialData = realTimeInertialData,
                biometricData = realTimeBiometricData,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Error Message Display
        errorMessage?.let { error ->
            ErrorMessage(
                message = error,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Error message card with dismiss functionality
 */
@Composable
private fun ErrorMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

/**
 * Battery warning card
 */
@Composable
private fun BatteryWarning(
    batteryLevel: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                contentDescription = "Battery Warning",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Low Battery Warning: $batteryLevel%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CollectionScreenPreview() {
    PrimeSensorCollectorTheme {
        // Preview with mock data would require ViewModel instance
        // This is a placeholder for the preview
        Surface {
            Text("Collection Screen Preview")
        }
    }
}