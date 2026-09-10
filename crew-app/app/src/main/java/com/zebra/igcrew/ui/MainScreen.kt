package com.zebra.igcrew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zebra.igcrew.AuthenticationOutcome
import com.zebra.igcrew.AuthenticationPhase
import com.zebra.igcrew.AuthorizationState
import com.zebra.igcrew.MainUiState
import com.zebra.igcrew.R
import com.zebra.igcrew.ig.AuthenticationState
import com.zebra.igcrew.ui.theme.IdentityGuardianCrewTheme
import com.zebra.igcrew.ui.theme.successColor

/**
 * The whole app: authentication starts by itself on launch, the button re-runs
 * it, and the message underneath describes how the last attempt went.
 */
@Composable
fun MainScreen(
    uiState: MainUiState,
    onStartAuthentication: () -> Unit,
    onRetryAuthorization: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.screen_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onStartAuthentication,
                // The scope has to land before the API answers, so the button
                // waits for authorization as well as for a call in flight. It
                // stays live while the lock screen is up, so a user who came
                // back without finishing can start over.
                enabled = !uiState.isStarting &&
                    uiState.authorization !is AuthorizationState.InProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_start_authentication),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Shift workers get handed the device with the app already open, so
            // closing it has to be something the screen itself offers.
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_exit),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(24.dp))

            PhaseMessage(phase = uiState.phase)

            Spacer(Modifier.height(24.dp))

            AuthorizationStatus(
                state = uiState.authorization,
                onRetry = onRetryAuthorization,
            )
        }
    }
}

/**
 * Where the attempt has got to: a spinner while this app or the lock screen is
 * working, and the verdict once there is one.
 */
@Composable
private fun PhaseMessage(
    phase: AuthenticationPhase,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (phase) {
            AuthenticationPhase.Idle -> Text(
                text = stringResource(R.string.authentication_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            AuthenticationPhase.Starting ->
                ProgressMessage(stringResource(R.string.authentication_calling))

            // The lock screen is in front of this app, so this is really only
            // visible to a user who came back without finishing on it.
            AuthenticationPhase.AwaitingUser ->
                ProgressMessage(stringResource(R.string.authentication_awaiting_user))

            is AuthenticationPhase.Done -> Outcome(phase.outcome)
        }
    }
}

/** The verdict on a finished attempt. */
@Composable
private fun Outcome(
    outcome: AuthenticationOutcome,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (outcome) {
            AuthenticationOutcome.Succeeded -> Text(
                text = stringResource(R.string.authentication_success),
                style = MaterialTheme.typography.titleMedium,
                color = successColor(),
                textAlign = TextAlign.Center,
            )

            is AuthenticationOutcome.Reported -> Text(
                text = outcome.message(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            is AuthenticationOutcome.Failed -> {
                Text(
                    text = stringResource(R.string.authentication_failed, outcome.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                outcome.hint?.let { hint ->
                    Text(
                        text = stringResource(R.string.authentication_hint, hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** What Identity Guardian answered, when it answered something other than SUCCESS. */
@Composable
private fun AuthenticationOutcome.Reported.message(): String = when (state) {
    // Neither API reports IN_PROGRESS as an outcome any more - it means the user
    // is still on the lock screen, which is AwaitingUser. Kept so that a future
    // caller of Reported cannot land here with nothing to show.
    AuthenticationState.IN_PROGRESS -> stringResource(R.string.authentication_in_progress)
    AuthenticationState.BUSY -> stringResource(R.string.authentication_busy)
    AuthenticationState.ERROR -> stringResource(R.string.authentication_error)
    // SUCCESS never lands here; it is reported as Succeeded.
    else -> stringResource(R.string.authentication_unknown, rawResult)
}

/** A message with a spinner, for the phases that are still going. */
@Composable
private fun ProgressMessage(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One line describing where authorization got to, with a retry when it failed.
 *
 * A failure is not fatal: an administrator may have staged the same AccessMgr
 * profile already, so this only informs rather than blocking the demo.
 */
@Composable
private fun AuthorizationStatus(
    state: AuthorizationState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            // Never needed: the scope was already in place. Nothing to report.
            AuthorizationState.NotAttempted -> Unit

            AuthorizationState.InProgress -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp))
                StatusText(stringResource(R.string.authorization_in_progress))
            }

            AuthorizationState.Authorized ->
                StatusText(stringResource(R.string.authorization_authorized))

            is AuthorizationState.Failed -> {
                StatusText(
                    text = stringResource(R.string.authorization_failed, state.message),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.authorization_retry))
                }
            }
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun MainScreenSuccessPreview() {
    IdentityGuardianCrewTheme {
        MainScreen(
            uiState = MainUiState(
                authorization = AuthorizationState.Authorized,
                phase = AuthenticationPhase.Done(AuthenticationOutcome.Succeeded),
            ),
            onStartAuthentication = {},
            onRetryAuthorization = {},
            onExit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenAwaitingUserPreview() {
    IdentityGuardianCrewTheme {
        MainScreen(
            uiState = MainUiState(
                authorization = AuthorizationState.Authorized,
                phase = AuthenticationPhase.AwaitingUser,
            ),
            onStartAuthentication = {},
            onRetryAuthorization = {},
            onExit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenFailurePreview() {
    IdentityGuardianCrewTheme {
        MainScreen(
            uiState = MainUiState(
                authorization = AuthorizationState.Failed(
                    "EMDK is not available on this device (FAILURE)."
                ),
                phase = AuthenticationPhase.Done(
                    AuthenticationOutcome.Failed(
                        message = "Identity Guardian rejected the call: Caller is unauthorized",
                        hint = "This app needs a ZDM delegation scope for this API URI.",
                    )
                ),
            ),
            onStartAuthentication = {},
            onRetryAuthorization = {},
            onExit = {},
        )
    }
}
