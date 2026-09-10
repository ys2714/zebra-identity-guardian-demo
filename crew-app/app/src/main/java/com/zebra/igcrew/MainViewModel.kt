package com.zebra.igcrew

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zebra.igcrew.ig.AuthenticationResult
import com.zebra.igcrew.ig.AuthenticationScheme
import com.zebra.igcrew.ig.AuthenticationState
import com.zebra.igcrew.ig.IdentityGuardianAuthorizer
import com.zebra.igcrew.ig.IdentityGuardianClient
import com.zebra.igcrew.ig.IdentityGuardianException
import com.zebra.igcrew.ig.LaunchFlag
import com.zebra.igcrew.ig.needsAuthorization
import com.zebra.igcrew.mx.AccessManager
import com.zebra.igcrew.zdm.ZdmDelegation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Progress of granting this app the Identity Guardian delegation scopes. */
sealed interface AuthorizationState {

    /**
     * Never needed. The APIs answered without it, because an administrator
     * staged the AccessMgr profile or an earlier run already granted the scopes.
     *
     * This is the normal state on a configured device, which is why the app no
     * longer does the MX/ZDM work up front: it is slow, it depends on a device
     * service that can be wedged, and most of the time it changes nothing.
     */
    data object NotAttempted : AuthorizationState

    /** The MX profile and the ZDM token are being applied. */
    data object InProgress : AuthorizationState

    /** The Identity Guardian API URIs are authorized for this app. */
    data object Authorized : AuthorizationState

    /**
     * Authorization failed. Only worth showing when the call it was meant to
     * unblock also failed - if the call then worked, the scope was already there
     * and this is noise.
     */
    data class Failed(val message: String) : AuthorizationState
}

/** How an authentication attempt ended. */
sealed interface AuthenticationOutcome {

    /** The user authenticated: Get Authentication Status reported SUCCESS. */
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

    /** A call itself failed; [hint] carries the suggested fix when one is known. */
    data class Failed(val message: String, val hint: String? = null) : AuthenticationOutcome
}

/**
 * How far the current authentication attempt has got.
 *
 * Start Authentication returns the instant Identity Guardian accepts the request,
 * so "the call came back" is not "the user authenticated" — [AwaitingUser] is the
 * gap between the two, and only [Done] carries a verdict.
 */
sealed interface AuthenticationPhase {

    /** Nothing attempted yet. */
    data object Idle : AuthenticationPhase

    /** The Start Authentication call is in flight. */
    data object Starting : AuthenticationPhase

    /** The lock screen is up and the user has not come back from it yet. */
    data object AwaitingUser : AuthenticationPhase

    /** The attempt finished, one way or another. */
    data class Done(val outcome: AuthenticationOutcome) : AuthenticationPhase
}

/**
 * Where an attempt stands after Start Authentication answered.
 *
 * BUSY and ERROR mean the lock screen never came up, so they are already the
 * verdict. Anything else — including IN_PROGRESS, which is the usual answer —
 * means Identity Guardian took the request and the user is now on the lock
 * screen, which is not a result yet. Reporting IN_PROGRESS as the outcome is the
 * bug this split exists to prevent.
 */
internal fun launchPhase(result: AuthenticationResult): AuthenticationPhase =
    when (result.state) {
        AuthenticationState.BUSY, AuthenticationState.ERROR ->
            AuthenticationPhase.Done(
                AuthenticationOutcome.Reported(result.state, result.rawResult)
            )

        else -> AuthenticationPhase.AwaitingUser
    }

/**
 * Where an attempt stands after Get Authentication Status answered, or null when
 * there is still no verdict and the wait should continue.
 *
 * IN_PROGRESS is the one status that is not a verdict: the lock screen is still
 * up. Every other value, known or not, ends the attempt.
 */
internal fun statusPhase(result: AuthenticationResult): AuthenticationPhase? = when {
    result.state == AuthenticationState.IN_PROGRESS -> null

    result.isSuccess -> AuthenticationPhase.Done(AuthenticationOutcome.Succeeded)

    else -> AuthenticationPhase.Done(
        AuthenticationOutcome.Reported(result.state, result.rawResult)
    )
}

/** State rendered by the crew screen. */
data class MainUiState(
    val authorization: AuthorizationState = AuthorizationState.NotAttempted,
    val phase: AuthenticationPhase = AuthenticationPhase.Idle,
) {
    /**
     * True only while this app is mid-call. Waiting on the lock screen does not
     * count: the user may well come back without having finished, and they need
     * the button to try again.
     */
    val isStarting: Boolean get() = phase is AuthenticationPhase.Starting
}

/**
 * Drives the one action of this app: authenticating the crew member with
 * Verification 3.
 *
 * That takes two APIs rather than one. Start Authentication only launches the
 * Identity Guardian lock screen and answers immediately, so its status cannot be
 * the verdict; Get Authentication Status supplies the verdict once the user is
 * done with the lock screen, which [refreshAuthenticationStatus] reads when this
 * app comes back to the foreground.
 */
class MainViewModel(
    private val client: IdentityGuardianClient,
    private val authorizer: IdentityGuardianAuthorizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** The authorization run in flight, so a retry cannot start a second one. */
    private var authorizationJob: Job? = null

    /** The authentication attempt in flight, so a tap cannot start a second one. */
    private var authenticationJob: Job? = null

    /** Guards the automatic start, which belongs to app launch and not to a retry. */
    private var hasAutoStarted = false

    /**
     * Set when the app was resumed while the Start Authentication call was still
     * in flight.
     *
     * The docs have that call answering the moment the request is accepted, but
     * if a device instead keeps it open until the lock screen is done with, the
     * resume that should trigger the status read arrives before there is a phase
     * to resolve. Remembering it means the read still happens rather than the
     * screen waiting for a resume that has already been and gone.
     */
    private var resumedWhileStarting = false

    init {
        // Authentication is supposed to begin as soon as the app opens, and the
        // delegation scope is usually already in place, so go straight at it.
        // authorize() is the fallback for when Identity Guardian says no.
        hasAutoStarted = true
        startAuthentication()
    }

    /**
     * Grants this app the delegation scopes for the two APIs it calls.
     *
     * Not called up front any more: it is slow, it depends on EMDK and the
     * device's MX framework service, and on a configured device it changes
     * nothing. When that service is not answering the profile submission never
     * completes, which used to hold the whole app behind it for the full timeout
     * and then report a failure for a step it did not need. Now it runs only
     * when Identity Guardian actually refuses a call, and as the retry action.
     */
    fun authorize() {
        if (authorizationJob?.isActive == true) return

        authorizationJob = viewModelScope.launch {
            authorizeNow()
            // Retrying by hand means the user wants another go at the real thing.
            startAuthentication()
        }
    }

    /** Applies the MX profile and takes the ZDM tokens, reporting how it went. */
    private suspend fun authorizeNow() {
        _uiState.update { it.copy(authorization = AuthorizationState.InProgress) }

        val state = authorizer.authorize().fold(
            onSuccess = { AuthorizationState.Authorized },
            onFailure = { error ->
                AuthorizationState.Failed(error.message ?: "Authorization failed.")
            },
        )

        _uiState.update { it.copy(authorization = state) }
    }

    /**
     * Brings up the Identity Guardian lock screen for [scheme] and waits for the
     * user on it.
     *
     * BUSY and ERROR mean the lock screen never came up, so they are the verdict.
     * Anything else means Identity Guardian took the request and the user is now
     * on the lock screen — which is *not* a result yet, so the screen says as
     * much and [refreshAuthenticationStatus] finishes the job on the way back.
     */
    fun startAuthentication(
        scheme: AuthenticationScheme = AuthenticationScheme.VERIFICATION_3,
        launchFlag: LaunchFlag = LaunchFlag.BLOCKING,
    ) {
        // Ignore taps while a call is already in flight.
        if (authenticationJob?.isActive == true) return

        authenticationJob = viewModelScope.launch {
            resumedWhileStarting = false
            _uiState.update { it.copy(phase = AuthenticationPhase.Starting) }

            var launch = client.startAuthentication(scheme, launchFlag)

            // Refused for want of a delegation scope is the one failure this app
            // can fix, so take the scope and try once more. Every other failure
            // would not be helped by it.
            if (launch.exceptionOrNull()?.needsAuthorization() == true) {
                authorizeNow()
                launch = client.startAuthentication(scheme, launchFlag)
            }

            val phase = launch.fold(
                onSuccess = ::launchPhase,
                onFailure = { error ->
                    AuthenticationPhase.Done(
                        AuthenticationOutcome.Failed(
                            message = error.message ?: "Unknown error",
                            hint = (error as? IdentityGuardianException)?.hint,
                        )
                    )
                },
            )

            _uiState.update { it.copy(phase = phase) }

            // Act on a resume that arrived too early to be acted on then.
            if (phase is AuthenticationPhase.AwaitingUser && resumedWhileStarting) {
                resumedWhileStarting = false
                resolveAuthenticationStatus()
            }
        }
    }

    /**
     * Reads Get Authentication Status to find out how the lock screen went.
     *
     * Called when this app returns to the foreground, which — with
     * `launchflag=blocking` — is the point at which the lock screen is out of the
     * way and the status has settled. A status that still reads IN_PROGRESS means
     * the user came back without finishing, so the wait simply continues.
     */
    fun refreshAuthenticationStatus() {
        // The attempt has not finished announcing itself yet; replay this once it
        // has, rather than dropping the one signal there is.
        if (_uiState.value.phase is AuthenticationPhase.Starting) {
            resumedWhileStarting = true
            return
        }

        // Nothing to resolve unless an attempt is actually waiting on the user.
        if (_uiState.value.phase !is AuthenticationPhase.AwaitingUser) return
        if (authenticationJob?.isActive == true) return

        authenticationJob = viewModelScope.launch { resolveAuthenticationStatus() }
    }

    /** Reads the status and commits it when it is a verdict. */
    private suspend fun resolveAuthenticationStatus() {
        // Null means "no verdict yet", which leaves the phase untouched.
        val resolved: AuthenticationPhase? = client.getAuthenticationStatus().fold(
            onSuccess = ::statusPhase,
            onFailure = { error ->
                AuthenticationPhase.Done(
                    AuthenticationOutcome.Failed(
                        message = error.message ?: "Unknown error",
                        hint = (error as? IdentityGuardianException)?.hint,
                    )
                )
            },
        )

        resolved?.let { phase -> _uiState.update { it.copy(phase = phase) } }
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
