package com.zebra.igcrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
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

                // The Identity Guardian lock screen covers this activity, so
                // getting resumed is the signal that the user is done with it and
                // Get Authentication Status has a verdict to report.
                LifecycleResumeEffect(viewModel) {
                    viewModel.refreshAuthenticationStatus()
                    onPauseOrDispose { }
                }

                MainScreen(
                    uiState = uiState,
                    onStartAuthentication = { viewModel.startAuthentication() },
                    onRetryAuthorization = { viewModel.authorize() },
                    // Drop the task as well, so the app is properly closed rather
                    // than left in Recents for the next user to walk back into.
                    onExit = { finishAndRemoveTask() },
                )
            }
        }
    }
}
