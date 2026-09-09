package com.zebra.iglead

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zebra.iglead.ig.IdentityGuardianAuthorizer
import com.zebra.iglead.ig.IdentityGuardianClient
import com.zebra.iglead.ig.IdentityGuardianException
import com.zebra.iglead.mx.AccessManager
import com.zebra.iglead.zdm.ZdmDelegation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Progress of granting this app the Identity Guardian delegation scopes. */
sealed interface AuthorizationState {

    /** The MX profile and the ZDM token are being applied. */
    data object InProgress : AuthorizationState

    /** The Identity Guardian API URI is authorized for this app. */
    data object Authorized : AuthorizationState

    /**
     * Authorization failed. The API may still answer if an administrator staged
     * the AccessMgr profile, so the session read is attempted regardless.
     */
    data class Failed(val message: String) : AuthorizationState
}

/** Progress of the Get Current User Session call that fills the form. */
sealed interface SessionStatus {

    /** The call is in flight. */
    data object Loading : SessionStatus

    /** A session came back; its user and role are in [MainUiState]. */
    data object Loaded : SessionStatus

    /** Identity Guardian reported that nobody is signed in. */
    data object NoSession : SessionStatus

    /** The call failed; [hint] carries the suggested fix when one is known. */
    data class Failed(val message: String, val hint: String? = null) : SessionStatus
}

/** Outcome of the last login attempt. */
sealed interface LoginState {

    /** Nothing attempted yet, or the form was edited since the last attempt. */
    data object Idle : LoginState

    /** The form was not complete enough to sign in. */
    data class Rejected(val reason: Rejection) : LoginState

    /** The login went through for [userId], acting as [role]. */
    data class SignedIn(val userId: String, val role: String) : LoginState

    /** Why a login attempt did not proceed. */
    enum class Rejection {
        /** There is no Identity Guardian user to log in as. */
        NO_USER,

        /** The password field was left empty. */
        PASSWORD_REQUIRED,
    }
}

/** State rendered by the login screen. */
data class MainUiState(
    /** User of the Identity Guardian session; editable in the form. */
    val userId: String = "",
    /** Role of the Identity Guardian session; shown read-only. */
    val role: String = "",
    val password: String = "",
    val sessionStatus: SessionStatus = SessionStatus.Loading,
    val authorization: AuthorizationState = AuthorizationState.InProgress,
    val login: LoginState = LoginState.Idle,
)

/**
 * Fills the login form from the Identity Guardian session and validates the
 * login attempt.
 *
 * Get Current User Session is the only provider API this screen calls, and it
 * runs once on start-up, so the form already carries the signed-in user by the
 * time it is visible.
 */
class MainViewModel(
    private val client: IdentityGuardianClient,
    private val authorizer: IdentityGuardianAuthorizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** The start-up run in flight, so a retry cannot start a second one. */
    private var startupJob: Job? = null

    init {
        start()
    }

    /**
     * Grants the delegation scope, then reads the current session into the form.
     * Also exposed as the retry action after either step fails.
     */
    fun start() {
        if (startupJob?.isActive == true) return

        startupJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    authorization = AuthorizationState.InProgress,
                    sessionStatus = SessionStatus.Loading,
                )
            }

            val authorization = authorizer.authorize().fold(
                onSuccess = { AuthorizationState.Authorized },
                onFailure = { error ->
                    AuthorizationState.Failed(error.message ?: "Authorization failed.")
                },
            )

            _uiState.update { it.copy(authorization = authorization) }

            // A failure here is not fatal: the scope may already be staged, so
            // the session read is worth attempting either way.
            loadSession()
        }
    }

    /** Calls Get Current User Session (v2) and copies the user and role into the form. */
    private suspend fun loadSession() {
        client.getCurrentUserSession().fold(
            onSuccess = { session ->
                _uiState.update {
                    it.copy(
                        userId = session.userId.orEmpty(),
                        role = session.userRole.orEmpty(),
                        sessionStatus = if (session.isEmpty) {
                            SessionStatus.NoSession
                        } else {
                            SessionStatus.Loaded
                        },
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        sessionStatus = SessionStatus.Failed(
                            message = error.message ?: "Unknown error",
                            hint = (error as? IdentityGuardianException)?.hint,
                        ),
                    )
                }
            },
        )
    }

    fun onUserIdChange(userId: String) {
        // Any edit invalidates the previous attempt's verdict.
        _uiState.update { it.copy(userId = userId, login = LoginState.Idle) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, login = LoginState.Idle) }
    }

    /**
     * Signs the user in. There is no back end behind the demo, so this only
     * checks that the form is complete and reports who would be signed in.
     */
    fun login() {
        val state = _uiState.value

        val login = when {
            state.userId.isBlank() -> LoginState.Rejected(LoginState.Rejection.NO_USER)
            state.password.isBlank() -> LoginState.Rejected(LoginState.Rejection.PASSWORD_REQUIRED)
            else -> LoginState.SignedIn(userId = state.userId, role = state.role)
        }

        _uiState.update {
            it.copy(
                login = login,
                // Don't leave the credential in the field once it was accepted.
                password = if (login is LoginState.SignedIn) "" else it.password,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Hand the EMDK session back so the device can tear it down.
        authorizer.release()
    }

    companion object {
        /** Builds the client and the authorizer from the application context. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application: Application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                )
                MainViewModel(
                    client = IdentityGuardianClient(application.contentResolver),
                    authorizer = IdentityGuardianAuthorizer(
                        accessManager = AccessManager(application),
                        delegation = ZdmDelegation(application.contentResolver),
                    ),
                )
            }
        }
    }
}
