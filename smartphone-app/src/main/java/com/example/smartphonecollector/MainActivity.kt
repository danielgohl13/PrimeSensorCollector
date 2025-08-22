package com.example.smartphonecollector

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.smartphonecollector.data.repository.DataRepository
import com.example.smartphonecollector.service.DataCollectionService
import com.example.smartphonecollector.ui.components.CollectionScreen
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme
import com.example.smartphonecollector.ui.viewmodel.CollectionViewModel
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), CoroutineScope by MainScope(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val MESSAGE_ITEM_RECEIVED_PATH = "/message_item_received"
        private const val WEARABLE_CAPABILITY = "wearable_data_collector"
    }
    
    private lateinit var viewModel: CollectionViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start background service
        DataCollectionService.startService(this)
        
        // Initialize dependencies
        val dataRepository = DataRepository(this)
        viewModel = CollectionViewModel(this, dataRepository)
        
        setContent {
            PrimeSensorCollectorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CollectionScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // ViewModel cleanup is handled automatically by the framework
    }
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    PrimeSensorCollectorTheme {
        // Preview placeholder - actual preview would need ViewModel instance
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            androidx.compose.material3.Text(
                text = "Wearable Data Collector",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}