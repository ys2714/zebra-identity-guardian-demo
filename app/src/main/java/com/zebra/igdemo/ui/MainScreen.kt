package com.zebra.igdemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zebra.igdemo.ApiResult
import com.zebra.igdemo.AuthorizationState
import com.zebra.igdemo.IdentityGuardianApi
import com.zebra.igdemo.MainUiState
import com.zebra.igdemo.R
import com.zebra.igdemo.ig.SessionField
import com.zebra.igdemo.ui.theme.IdentityGuardianDemoTheme

/**
 * The whole demo: two buttons that each call one Identity Guardian API, and a
 * card showing what came back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onStartAuthentication: () -> Unit,
    onGetCurrentSession: () -> Unit,
    onRetryAuthorization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.screen_title))
                        Text(
                            text = stringResource(R.string.screen_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The delegation scopes have to land before the APIs answer, so the
            // progress is shown above the actions it unblocks.
            AuthorizationStatus(
                state = uiState.authorization,
                onRetry = onRetryAuthorization,
            )

            val isAuthorizing = uiState.authorization is AuthorizationState.InProgress

            ActionButton(
                text = stringResource(R.string.action_start_authentication),
                enabled = !uiState.isBusy && !isAuthorizing,
                onClick = onStartAuthentication,
            )

            ActionButton(
                text = stringResource(R.string.action_get_current_session),
                enabled = !uiState.isBusy && !isAuthorizing,
                onClick = onGetCurrentSession,
            )

            // Takes the remaining space at most, and scrolls internally when the
            // payload is longer than that. fill = false keeps short results compact.
            ResultCard(
                isBusy = uiState.isBusy,
                result = uiState.result,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            )
        }
    }
}

/**
 * One line describing where authorization got to, with a retry when it failed.
 *
 * A failure is not fatal: an administrator may have staged the same AccessMgr
 * profiles already, so this only informs rather than blocking the demo.
 */
@Composable
private fun AuthorizationStatus(
    state: AuthorizationState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            AuthorizationState.InProgress -> {
                CircularProgressIndicator(Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.authorization_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AuthorizationState.Authorized -> Text(
                text = stringResource(R.string.authorization_authorized),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is AuthorizationState.Failed -> {
                Text(
                    text = stringResource(R.string.authorization_failed, state.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.authorization_retry))
                }
            }
        }
    }
}

/** A full-width primary action button. */
@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Shows a spinner while a call is in flight, then the success or failure detail. */
@Composable
private fun ResultCard(
    isBusy: Boolean,
    result: ApiResult?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.elevatedCardColors()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                isBusy -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text("Calling Identity Guardian…")
                }

                result == null -> Text(
                    text = stringResource(R.string.result_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> ResultBody(result)
            }
        }
    }
}

@Composable
private fun ResultBody(result: ApiResult) {
    Text(
        text = result.api.displayName,
        style = MaterialTheme.typography.titleMedium,
    )
    HorizontalDivider()

    when (result) {
        is ApiResult.Success -> {
            DetailRow(stringResource(R.string.result_status), result.status)

            result.fields.forEach { field ->
                DetailRow(field.label, field.value)
            }

            if (result.api == IdentityGuardianApi.GET_CURRENT_SESSION && result.fields.isEmpty()) {
                Text(
                    text = stringResource(R.string.result_no_session),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            result.rawResponse?.let { raw ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_raw_response),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = raw,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        is ApiResult.Failure -> {
            Text(
                text = result.message,
                color = MaterialTheme.colorScheme.error,
            )
            result.hint?.let { hint ->
                DetailRow(stringResource(R.string.result_hint), hint)
            }
        }
    }
}

/** One label/value line, with the label kept narrow so values stay aligned. */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenSuccessPreview() {
    IdentityGuardianDemoTheme {
        MainScreen(
            uiState = MainUiState(
                result = ApiResult.Success(
                    api = IdentityGuardianApi.GET_CURRENT_SESSION,
                    status = "SESSION FOUND",
                    fields = listOf(
                        SessionField("user_id", "User ID", "jdoe"),
                        SessionField("user_role", "User Role", "operator"),
                        SessionField("signin_time", "Signin Time", "2026-09-04T08:15:00Z"),
                    ),
                    rawResponse = "{\n  \"user_id\": \"jdoe\"\n}",
                ),
                authorization = AuthorizationState.Authorized,
            ),
            onStartAuthentication = {},
            onGetCurrentSession = {},
            onRetryAuthorization = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenFailurePreview() {
    IdentityGuardianDemoTheme {
        MainScreen(
            uiState = MainUiState(
                result = ApiResult.Failure(
                    api = IdentityGuardianApi.START_AUTHENTICATION,
                    message = "Identity Guardian rejected the call: Caller is unauthorized",
                    hint = "This app needs a ZDM delegation scope for this API URI.",
                ),
                authorization = AuthorizationState.Failed(
                    "EMDK is not available on this device (FAILURE)."
                ),
            ),
            onStartAuthentication = {},
            onGetCurrentSession = {},
            onRetryAuthorization = {},
        )
    }
}
