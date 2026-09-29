package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.VeoStudioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val studioViewModel: StudioViewModel = viewModel(
                    factory = StudioViewModel.provideFactory(applicationContext)
                )
                VeoStudioScreen(
                    viewModel = studioViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
