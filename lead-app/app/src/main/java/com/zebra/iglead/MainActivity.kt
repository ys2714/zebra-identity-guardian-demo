package com.zebra.iglead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zebra.iglead.ui.MainScreen
import com.zebra.iglead.ui.theme.IdentityGuardianLeadTheme

/** Single-activity host for the login screen. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IdentityGuardianLeadTheme {
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                MainScreen(
                    uiState = uiState,
                    onUserIdChange = viewModel::onUserIdChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onLogin = viewModel::login,
                    onRetry = viewModel::start,
                )
            }
        }
    }
}
