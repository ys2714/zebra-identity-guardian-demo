package com.zebra.igdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zebra.igdemo.ui.MainScreen
import com.zebra.igdemo.ui.theme.IdentityGuardianDemoTheme

/** Single-activity host for the demo screen. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IdentityGuardianDemoTheme {
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                MainScreen(
                    uiState = uiState,
                    onStartAuthentication = { viewModel.startAuthentication() },
                    onGetCurrentSession = { viewModel.getCurrentUserSession() },
                )
            }
        }
    }
}
