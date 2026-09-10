package com.zebra.iglead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
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

                // Somebody may have signed in through IG Crew while this app was
                // in the background, so the session is re-read on the way back in
                // rather than only once at start-up. The first resume lands while
                // the start-up read is still running, which it skips.
                LifecycleResumeEffect(viewModel) {
                    viewModel.refreshSession()
                    onPauseOrDispose { }
                }

                MainScreen(
                    uiState = uiState,
                    onUserIdChange = viewModel::onUserIdChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onLogin = viewModel::login,
                    onRetry = viewModel::start,
                    // Drop the task as well, so the app is properly closed rather
                    // than left in Recents for the next user to walk back into.
                    onExit = { finishAndRemoveTask() },
                )
            }
        }
    }
}
