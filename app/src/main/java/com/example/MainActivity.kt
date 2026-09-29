package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.VeoStudioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val studioViewModel: StudioViewModel = viewModel(
                factory = StudioViewModel.provideFactory(applicationContext)
            )
            val uiState by studioViewModel.uiState.collectAsStateWithLifecycle()

            MyApplicationTheme(
                language = uiState.language
            ) {
                VeoStudioScreen(
                    viewModel = studioViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
