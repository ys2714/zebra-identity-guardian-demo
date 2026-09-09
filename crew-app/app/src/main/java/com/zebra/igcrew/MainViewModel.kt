package com.zebra.igcrew

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zebra.igcrew.ig.AuthenticationScheme
import com.zebra.igcrew.ig.AuthenticationState
import com.zebra.igcrew.ig.IdentityGuardianAuthorizer
import com.zebra.igcrew.ig.IdentityGuardianClient
import com.zebra.igcrew.ig.IdentityGuardianException
import com.zebra.igcrew.ig.LaunchFlag
import com.zebra.igcrew.mx.AccessManager
import com.zebra.igcrew.zdm.ZdmDelegation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Progress of granting this app the Identity Guardian delegation scope. */
sealed interface AuthorizationState {

    /** The MX profile and the ZDM token are being applied. */
    data object InProgress : AuthorizationState

    /** The Start Authentication URI is authorized for this app. */
    data object Authorized : AuthorizationState

    /**
     * Authorization failed. The API may still answer if an administrator staged
     * the AccessMgr profile, so the button stays enabled.
     */
    data class Failed(val message: String) : AuthorizationState
}

/** Outcome of the most recent Start Authentication call. */
sealed interface AuthenticationOutcome {

    /** Identity Guardian reported [AuthenticationState.SUCCESS]. */
    data object Succeeded : AuthenticationOutcome

    /**
     * Identity Guardian answered with something other than SUCCESS.
     *
     * @param state the recognised status, or null when it was not one this app knows.
     * @param rawResult the `RESULT` string as it came back.
     */
    data class Reported(
        val state: AuthenticationState?,
        val rawResult: String,
    ) : AuthenticationOutcome

    /** The call itself failed; [hint] carries the suggested fix when one is known. */
    data class Failed(val message: String, val hint: String? = null) : AuthenticationOutcome
}

/** State rendered by the crew screen. */
data class MainUiState(
    val isAuthenticating: Boolean = false,
    val authorization: AuthorizationState = AuthorizationState.InProgress,
    val outcome: AuthenticationOutcome? = null,
)

/**
 * Drives the one action of this app: Start Authentication with Verification 3.
 *
 * The provider call is suspending, so a call in flight simply flips
 * [MainUiState.isAuthenticating] and the button disables itself until it returns.
 */
class MainViewModel(
    private val client: IdentityGuardianClient,
    private val authorizer: IdentityGuardianAuthorizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** The authorization run in flight, so a retry cannot start a second one. */
    private var authorizationJob: Job? = null

    init {
        // Identity Guardian rejects callers without a delegation scope, so the
        // scope has to be in place before the button can do anything.
        authorize()
    }

    /**
     * Grants this app the delegation scope for Start Authentication.
     * Also exposed for the retry action after a failure.
     */
    fun authorize() {
        if (authorizationJob?.isActive == true) return

        authorizationJob = viewModelScope.launch {
            _uiState.update { it.copy(authorization = AuthorizationState.InProgress) }

            val state = authorizer.authorize().fold(
                onSuccess = { AuthorizationState.Authorized },
                onFailure = { error ->
                    AuthorizationState.Failed(error.message ?: "Authorization failed.")
                },
            )

            _uiState.update { it.copy(authorization = state) }
        }
    }

    /**
     * Calls Start Authentication, which brings up the Identity Guardian lock
     * screen for [scheme] and blocks the device until the user authenticates.
     *
     * `RESULT` describes the lock screen itself, not the credentials the user
     * then enters: SUCCESS means Identity Guardian locked the screen and ran the
     * verification, which is the success this screen reports.
     */
    fun startAuthentication(
        scheme: AuthenticationScheme = AuthenticationScheme.VERIFICATION_3,
        launchFlag: LaunchFlag = LaunchFlag.BLOCKING,
    ) {
        // Ignore taps while a call is already in flight.
        if (_uiState.value.isAuthenticating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, outcome = null) }

            val outcome = client.startAuthentication(scheme, launchFlag).fold(
                onSuccess = { result ->
                    if (result.isSuccess) {
                        AuthenticationOutcome.Succeeded
                    } else {
                        AuthenticationOutcome.Reported(result.state, result.rawResult)
                    }
                },
                onFailure = { error ->
                    AuthenticationOutcome.Failed(
                        message = error.message ?: "Unknown error",
                        hint = (error as? IdentityGuardianException)?.hint,
                    )
                },
            )

            _uiState.update { it.copy(isAuthenticating = false, outcome = outcome) }
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
