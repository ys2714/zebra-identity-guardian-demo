package com.zebra.igcrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zebra.igcrew.ui.MainScreen
import com.zebra.igcrew.ui.theme.IdentityGuardianCrewTheme

/** Single-activity host for the crew screen. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IdentityGuardianCrewTheme {
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                MainScreen(
                    uiState = uiState,
                    onStartAuthentication = { viewModel.startAuthentication() },
                    onRetryAuthorization = viewModel::authorize,
                )
            }
        }
    }
}
