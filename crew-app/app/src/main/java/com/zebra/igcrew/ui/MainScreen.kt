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
import com.zebra.igcrew.AuthorizationState
import com.zebra.igcrew.MainUiState
import com.zebra.igcrew.R
import com.zebra.igcrew.ig.AuthenticationState
import com.zebra.igcrew.ui.theme.IdentityGuardianCrewTheme
import com.zebra.igcrew.ui.theme.successColor

/**
 * The whole app: one button that calls Start Authentication with Verification 3,
 * and the message describing how it went.
 */
@Composable
fun MainScreen(
    uiState: MainUiState,
    onStartAuthentication: () -> Unit,
    onRetryAuthorization: () -> Unit,
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
                // waits for authorization as well as for a call in flight.
                enabled = !uiState.isAuthenticating &&
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

            Spacer(Modifier.height(24.dp))

            OutcomeMessage(uiState = uiState)

            Spacer(Modifier.height(24.dp))

            AuthorizationStatus(
                state = uiState.authorization,
                onRetry = onRetryAuthorization,
            )
        }
    }
}

/** The result of the last call: the success message, or why there isn't one. */
@Composable
private fun OutcomeMessage(
    uiState: MainUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.isAuthenticating) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Text(stringResource(R.string.authentication_calling))
            }
            return@Column
        }

        when (val outcome = uiState.outcome) {
            null -> Text(
                text = stringResource(R.string.authentication_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

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
    AuthenticationState.IN_PROGRESS -> stringResource(R.string.authentication_in_progress)
    AuthenticationState.BUSY -> stringResource(R.string.authentication_busy)
    AuthenticationState.ERROR -> stringResource(R.string.authentication_error)
    // SUCCESS never lands here; it is reported as Succeeded.
    else -> stringResource(R.string.authentication_unknown, rawResult)
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
                outcome = AuthenticationOutcome.Succeeded,
            ),
            onStartAuthentication = {},
            onRetryAuthorization = {},
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
                outcome = AuthenticationOutcome.Failed(
                    message = "Identity Guardian rejected the call: Caller is unauthorized",
                    hint = "This app needs a ZDM delegation scope for this API URI.",
                ),
            ),
            onStartAuthentication = {},
            onRetryAuthorization = {},
        )
    }
}
