package com.zebra.iglead.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zebra.iglead.AccessState
import com.zebra.iglead.AuthorizationState
import com.zebra.iglead.LoginState
import com.zebra.iglead.MainUiState
import com.zebra.iglead.R
import com.zebra.iglead.SessionStatus
import com.zebra.iglead.ui.theme.IdentityGuardianLeadTheme
import com.zebra.iglead.ui.theme.sessionValueColor

/**
 * The whole demo: a login form whose user and role are filled in from the
 * Identity Guardian session, the only provider API this app calls.
 *
 * Values that came from the session are drawn in red, so it is obvious at a
 * glance which parts of the form Identity Guardian supplied.
 *
 * A role on the deny list never gets this far — [AccessDeniedScreen] takes its
 * place, so there is no form to type into.
 */
@Composable
fun MainScreen(
    uiState: MainUiState,
    onUserIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val access = uiState.access
    if (access is AccessState.Denied) {
        AccessDeniedScreen(
            role = access.role,
            userId = uiState.userId,
            onExit = onExit,
            modifier = modifier,
        )
        return
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            FormRow(label = stringResource(R.string.label_user)) {
                UserField(
                    userId = uiState.userId,
                    onUserIdChange = onUserIdChange,
                )
            }

            Spacer(Modifier.height(16.dp))

            FormRow(label = stringResource(R.string.label_password)) {
                PasswordField(
                    password = uiState.password,
                    onPasswordChange = onPasswordChange,
                    onDone = onLogin,
                )
            }

            Spacer(Modifier.height(16.dp))

            FormRow(label = stringResource(R.string.label_role)) {
                Text(
                    text = uiState.role.ifBlank { stringResource(R.string.value_missing) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = sessionValueColor(),
                )
            }

            Spacer(Modifier.height(24.dp))

            // Sits under the fields, aligned with their right edge, as in the layout.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onExit) {
                    Text(
                        text = stringResource(R.string.action_exit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = onLogin,
                    enabled = uiState.sessionStatus !is SessionStatus.Loading,
                ) {
                    Text(
                        text = stringResource(R.string.action_login),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            StatusArea(uiState = uiState, onRetry = onRetry)
        }
    }
}

/**
 * What a blocked role sees instead of the login form.
 *
 * The role is named, because on a device whose Identity Guardian roles are spelled
 * differently the fix is to put that exact string into `R.array.blocked_roles` -
 * or take it out.
 */
@Composable
private fun AccessDeniedScreen(
    role: String,
    userId: String,
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
                text = stringResource(R.string.access_denied_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.access_denied_detail, role),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            if (userId.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                StatusText(stringResource(R.string.access_denied_user, userId))
            }

            Spacer(Modifier.height(32.dp))

            Button(onClick = onExit) {
                Text(
                    text = stringResource(R.string.action_exit),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/**
 * One `Label :` / control line. Labels share a fixed width and are right-aligned
 * against the controls, which keeps every field starting at the same column.
 */
@Composable
private fun FormRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
            // Per-language, because the Japanese labels need more room than the
            // English ones to stay on one line.
            modifier = Modifier.width(dimensionResource(R.dimen.form_label_width)),
        )
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** The session's user, editable in case the demo needs to log in as someone else. */
@Composable
private fun UserField(
    userId: String,
    onUserIdChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = userId,
        onValueChange = onUserIdChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = sessionValueColor()),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The one credential the session does not supply. */
@Composable
private fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Everything the form itself cannot show: how authorization and the session read
 * went, and the verdict on the last login attempt.
 *
 * An authorization failure is not fatal — an administrator may have staged the
 * same AccessMgr profile already — so it only informs, next to a retry.
 */
@Composable
private fun StatusArea(
    uiState: MainUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (val authorization = uiState.authorization) {
            AuthorizationState.InProgress ->
                ProgressStatus(stringResource(R.string.authorization_in_progress))

            AuthorizationState.Authorized -> Unit

            is AuthorizationState.Failed -> StatusText(
                text = stringResource(R.string.authorization_failed, authorization.message),
                color = MaterialTheme.colorScheme.error,
            )
        }

        when (val session = uiState.sessionStatus) {
            SessionStatus.Loading -> ProgressStatus(stringResource(R.string.session_loading))

            SessionStatus.Loaded -> Unit

            SessionStatus.NoSession -> StatusText(stringResource(R.string.session_none))

            is SessionStatus.Failed -> {
                StatusText(
                    text = stringResource(R.string.session_failed, session.message),
                    color = MaterialTheme.colorScheme.error,
                )
                session.hint?.let { hint ->
                    StatusText(stringResource(R.string.session_hint, hint))
                }
            }
        }

        when (val login = uiState.login) {
            LoginState.Idle -> Unit

            is LoginState.Rejected -> StatusText(
                text = stringResource(login.reason.messageRes),
                color = MaterialTheme.colorScheme.error,
            )

            is LoginState.SignedIn -> StatusText(
                text = stringResource(
                    R.string.login_signed_in,
                    login.userId,
                    login.role.ifBlank { stringResource(R.string.value_missing) },
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val canRetry = uiState.authorization is AuthorizationState.Failed ||
            uiState.sessionStatus is SessionStatus.Failed
        if (canRetry) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

/** A status line with a spinner, for the steps that are still running. */
@Composable
private fun ProgressStatus(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp))
        StatusText(text)
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
        modifier = modifier,
    )
}

/** The reason a login attempt was turned down, as something to show the user. */
private val LoginState.Rejection.messageRes: Int
    @StringRes get() = when (this) {
        LoginState.Rejection.NO_USER -> R.string.login_no_user
        LoginState.Rejection.PASSWORD_REQUIRED -> R.string.login_password_required
    }

@Preview(showBackground = true)
@Composable
private fun MainScreenSessionPreview() {
    IdentityGuardianLeadTheme {
        MainScreen(
            uiState = MainUiState(
                userId = "jdoe",
                role = "Manager",
                sessionStatus = SessionStatus.Loaded,
                authorization = AuthorizationState.Authorized,
                access = AccessState.Granted,
            ),
            onUserIdChange = {},
            onPasswordChange = {},
            onLogin = {},
            onRetry = {},
            onExit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenAccessDeniedPreview() {
    IdentityGuardianLeadTheme {
        MainScreen(
            uiState = MainUiState(
                userId = "ptanaka",
                role = "Parttimer",
                sessionStatus = SessionStatus.Loaded,
                authorization = AuthorizationState.Authorized,
                access = AccessState.Denied("Parttimer"),
            ),
            onUserIdChange = {},
            onPasswordChange = {},
            onLogin = {},
            onRetry = {},
            onExit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenFailurePreview() {
    IdentityGuardianLeadTheme {
        MainScreen(
            uiState = MainUiState(
                sessionStatus = SessionStatus.Failed(
                    message = "Access to the Identity Guardian provider was denied.",
                    hint = "This app needs a ZDM delegation scope for this API URI.",
                ),
                authorization = AuthorizationState.Failed(
                    "EMDK is not available on this device (FAILURE)."
                ),
                login = LoginState.Rejected(LoginState.Rejection.NO_USER),
            ),
            onUserIdChange = {},
            onPasswordChange = {},
            onLogin = {},
            onRetry = {},
            onExit = {},
        )
    }
}
