package com.zebra.igcrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zebra.igcrew.ui.MainScreen
import com.zebra.igcrew.ui.theme.IdentityGuardianCrewTheme

/** Single-activity host for the crew screen. */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IdentityGuardianCrewTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    /**
     * Window focus, rather than `onResume`, is what drives the app's one action.
     *
     * Holding focus is the only signal that says this activity is genuinely the
     * window in front of the user. `onResume` fires earlier - while the launch
     * transition is still running - and authentication started from there put the
     * Identity Guardian lock screen up over a task that had not finished coming
     * to the front, so dismissing the lock screen dropped the user on the
     * launcher instead of back here.
     *
     * The same signal covers the way back: the lock screen takes focus off this
     * window and hands it back when it is done, which is the moment Get
     * Authentication Status has something to say.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) viewModel.onInForeground()
    }
}
