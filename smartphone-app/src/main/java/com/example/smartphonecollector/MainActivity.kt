package com.example.smartphonecollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.smartphonecollector.data.repository.DataRepository
import com.example.smartphonecollector.ui.components.CollectionScreen
import com.example.smartphonecollector.ui.theme.PrimeSensorCollectorTheme
import com.example.smartphonecollector.ui.viewmodel.CollectionViewModel

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: CollectionViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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