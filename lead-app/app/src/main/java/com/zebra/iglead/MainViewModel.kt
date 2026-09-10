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
import com.zebra.iglead.ig.UserSession
import com.zebra.iglead.ig.needsAuthorization
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

    /**
     * Never needed. The API answered without it, because an administrator staged
     * the AccessMgr profile or an earlier run already granted the scope.
     *
     * This is the normal state on a configured device, which is why the app no
     * longer does the MX/ZDM work up front: it is slow, it depends on a device
     * service that can be wedged, and most of the time it changes nothing.
     */
    data object NotAttempted : AuthorizationState

    /** The MX profile and the ZDM token are being applied. */
    data object InProgress : AuthorizationState

    /** The Identity Guardian API URI is authorized for this app. */
    data object Authorized : AuthorizationState

    /**
     * Authorization failed. Only worth showing when the API call it was meant to
     * unblock also failed - if the call then worked, the scope was already there
     * and this is noise.
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

/**
 * Whether the Identity Guardian role is one this app is for.
 *
 * IG Lead is a lead's tool, so a session belonging to a role that is not
 * supposed to have it never reaches the login form. The check is a deny list:
 * the roles named in `R.array.blocked_roles` are turned away and every other
 * role is let through, which keeps the demo working on a device whose roles are
 * configured differently.
 */
sealed interface AccessState {

    /** No verdict yet, because the session has not been read. */
    data object Undecided : AccessState

    /** The role is not one this app turns away. */
    data object Granted : AccessState

    /** [role] is on the deny list, so the login form is withheld. */
    data class Denied(val role: String) : AccessState
}

/**
 * Applies the deny list to [role]: anything not named in [blockedRoles] is let
 * through, matched case-insensitively and ignoring surrounding whitespace.
 *
 * A session carrying no role names nobody to turn away, so it is granted.
 */
internal fun roleAccess(role: String, blockedRoles: List<String>): AccessState {
    val trimmed = role.trim()
    if (trimmed.isBlank()) return AccessState.Granted

    return if (blockedRoles.any { it.trim().equals(trimmed, ignoreCase = true) }) {
        AccessState.Denied(trimmed)
    } else {
        AccessState.Granted
    }
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
    val authorization: AuthorizationState = AuthorizationState.NotAttempted,
    val access: AccessState = AccessState.Undecided,
    val login: LoginState = LoginState.Idle,
)

/**
 * Fills the login form from the Identity Guardian session, decides whether the
 * role behind that session may use this app, and validates the login attempt.
 *
 * Get Current User Session is the only provider API this screen calls. It runs
 * on start-up and again whenever the app comes back to the foreground, so the
 * form follows whoever signed in most recently — including someone who
 * authenticated in IG Crew while this app sat in the background.
 *
 * @param blockedRoles Identity Guardian roles that are refused this app,
 * matched case-insensitively.
 */
class MainViewModel(
    private val client: IdentityGuardianClient,
    private val authorizer: IdentityGuardianAuthorizer,
    private val blockedRoles: List<String> = emptyList(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** The run filling the form, so a retry cannot start a second one. */
    private var sessionJob: Job? = null

    init {
        start()
    }

    /**
     * Reads the current session into the form, granting the delegation scope
     * first only if Identity Guardian actually refuses the call.
     *
     * The scope is usually already in place - staged by an administrator, or
     * granted by an earlier run - so doing the MX/ZDM work up front made every
     * launch wait on it for nothing. Worse, it is EMDK and MX that the wait
     * depends on: when the device's MX framework service is not answering, the
     * profile submission simply never completes, and the app used to sit behind
     * that for the full timeout and then show a failure for a step it did not
     * need. Asking Identity Guardian first is both faster and honest about
     * whether authorization was required at all.
     */
    fun start() {
        if (sessionJob?.isActive == true) return

        sessionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    authorization = AuthorizationState.NotAttempted,
                    sessionStatus = SessionStatus.Loading,
                )
            }

            val session = client.getCurrentUserSession()

            // Anything other than "you have no delegation scope" is a failure
            // the MX/ZDM work would not fix, so don't spend time on it.
            val refused = session.exceptionOrNull()?.needsAuthorization() == true
            if (!refused) {
                apply(session)
                return@launch
            }

            _uiState.update { it.copy(authorization = AuthorizationState.InProgress) }

            val authorization = authorizer.authorize().fold(
                onSuccess = { AuthorizationState.Authorized },
                onFailure = { error ->
                    AuthorizationState.Failed(error.message ?: "Authorization failed.")
                },
            )

            _uiState.update { it.copy(authorization = authorization) }

            // Worth retrying even if authorize() reported a failure: it applies
            // several profiles and only one of them has to have landed.
            apply(client.getCurrentUserSession())
        }
    }

    /**
     * Re-reads the session without redoing the authorization.
     *
     * Called when the app returns to the foreground: the session may have
     * changed while it was away, and a form still showing the previous user
     * would also mean the role gate was judging the wrong person.
     */
    fun refreshSession() {
        if (sessionJob?.isActive == true) return

        sessionJob = viewModelScope.launch {
            _uiState.update { it.copy(sessionStatus = SessionStatus.Loading) }
            apply(client.getCurrentUserSession())
        }
    }

    /** Copies a Get Current User Session result into the form and the role gate. */
    private fun apply(result: Result<UserSession>) {
        result.fold(
            onSuccess = { session ->
                val role = session.userRole.orEmpty()
                val signedIn = session.isSignedIn

                _uiState.update {
                    it.copy(
                        userId = session.userId.orEmpty(),
                        role = role,
                        sessionStatus = if (signedIn) {
                            SessionStatus.Loaded
                        } else {
                            SessionStatus.NoSession
                        },
                        // With nobody signed in there is no role to judge, so the
                        // gate stays open and the form reports the missing session.
                        access = if (signedIn) {
                            roleAccess(role, blockedRoles)
                        } else {
                            AccessState.Undecided
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

        // The form is not on screen for a blocked role, but the gate is what
        // decides who signs in, so it is enforced here rather than in the UI.
        if (state.access is AccessState.Denied) return

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
        /**
         * Builds the client and the authorizer from the application context, and
         * reads the deny list out of resources so the roles this app refuses can
         * be changed without touching the code.
         */
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
                    blockedRoles = application.resources
                        .getStringArray(R.array.blocked_roles)
                        .toList(),
                )
            }
        }
    }
}
