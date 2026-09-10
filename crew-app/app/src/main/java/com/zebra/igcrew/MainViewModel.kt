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
        /** Identity Guardian's own wording, when its answer carried any. */
        val detail: String? = null,
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
                AuthenticationOutcome.Reported(result.state, result.rawResult, result.message)
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
internal fun statusPhase(result: AuthenticationResult?): AuthenticationPhase? = when {
    // Identity Guardian answered with no status at all, which is what it does
    // once a flow is over and it has cleared the one it had. There is nothing
    // to report, so the phase is left exactly as it was - showing this as a
    // failure is what put "Status query did not contain a RESULT value" under a
    // screen whose user had signed in perfectly well.
    result == null -> null

    result.state == AuthenticationState.IN_PROGRESS -> null

    result.isSuccess -> AuthenticationPhase.Done(AuthenticationOutcome.Succeeded)

    else -> AuthenticationPhase.Done(
        AuthenticationOutcome.Reported(result.state, result.rawResult, result.message)
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
 * That takes three APIs rather than one. Logout User clears whoever Identity
 * Guardian still has signed in, without which it declines to show its lock
 * screen at all. Start Authentication then launches that lock screen and answers
 * immediately, so its status cannot be the verdict. Get Authentication Status
 * supplies the verdict, read both when the status URI says it changed and when
 * this app comes back to the foreground - the first because Identity Guardian
 * may never hand the foreground back, the second as the backstop for a device
 * that does not notify.
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

    /** Guards the automatic start, so only the first time in front triggers it. */
    private var hasAutoStarted = false

    /**
     * Set when the app came back to the foreground while the Start Authentication
     * call was still in flight.
     *
     * The docs have that call answering the moment the request is accepted, but
     * if a device instead keeps it open until the lock screen is done with, the
     * foreground signal that should trigger the status read arrives before there
     * is a phase to resolve. Remembering it means the read still happens rather
     * than the screen waiting on a signal that has already been and gone.
     */
    private var foregroundedWhileStarting = false

    init {
        // Watch the status URI for the whole life of the app. This is what lets
        // an attempt reach a verdict while the app is in the background, which
        // it now has to: Identity Guardian dismisses its lock screen to
        // whatever the system puts next, which need not be this app.
        observeAuthenticationStatus()
    }

    /**
     * Called when this app's window is actually in front of the user.
     *
     * The first such moment is what starts authentication, and the timing is the
     * point. Starting it from `init` - during `onCreate` - put the Identity
     * Guardian lock screen up over a task that had not finished coming to the
     * front, so dismissing the lock screen returned the device to the launcher
     * instead of to this app. Waiting for the window to hold focus means this
     * app is unambiguously what the lock screen came up over.
     *
     * Later calls are the user coming back, which is when a verdict may be
     * waiting to be read.
     */
    fun onInForeground() {
        if (!hasAutoStarted) {
            hasAutoStarted = true
            startAuthentication()
            return
        }

        refreshAuthenticationStatus()
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
     * Applies the delegation scopes, but only if this app has not already tried.
     *
     * The MX half of it is slow and leans on a device service that can stop
     * answering altogether, so it must not become something every call pays for.
     * One automatic attempt is enough: it is idempotent, so a second would
     * change nothing, and the retry button is there for a deliberate second go.
     */
    private suspend fun authorizeOnce() {
        if (_uiState.value.authorization != AuthorizationState.NotAttempted) return
        authorizeNow()
    }

    /**
     * Runs [call], and when Identity Guardian refused it for want of a
     * delegation scope, takes the scopes and runs it once more.
     *
     * Being refused is the one failure this app can fix by itself; every other
     * one would not be helped by it, which is why the grant hangs off the
     * refusal rather than running in front of the call.
     */
    private suspend fun <T> withAuthorization(call: suspend () -> Result<T>): Result<T> {
        val first = call()
        if (first.exceptionOrNull()?.needsAuthorization() != true) return first

        authorizeOnce()
        return call()
    }

    /**
     * Ends the session Identity Guardian is still holding from the last run.
     *
     * Identity Guardian does not put its lock screen up for a user it has
     * already authenticated, so without this the second run of the demo left the
     * screen waiting on a lock screen that was never going to appear. Signing
     * the previous user out is what makes authentication repeatable.
     *
     * Deliberately best-effort and its result deliberately ignored: on the first
     * run there is no session to end, and a device in Proxy Mode refuses the
     * call outright. Neither is a reason not to try authenticating.
     */
    private suspend fun signOutPreviousUser() {
        withAuthorization { client.logout() }
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
            foregroundedWhileStarting = false
            _uiState.update { it.copy(phase = AuthenticationPhase.Starting) }

            signOutPreviousUser()

            val launch = withAuthorization { client.startAuthentication(scheme, launchFlag) }

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
            if (phase is AuthenticationPhase.AwaitingUser && foregroundedWhileStarting) {
                foregroundedWhileStarting = false
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
            foregroundedWhileStarting = true
            return
        }

        resolveIfAwaitingUser()
    }

    /**
     * Watches the status URI, which is how Identity Guardian is documented to
     * report this API, and re-reads the status on every notification.
     *
     * Being told rather than having to ask on resume is what makes the outcome
     * survive Identity Guardian handing the device to something other than this
     * app: by the time the user opens it again the verdict is already in, so the
     * screen shows what happened instead of asking a question Identity Guardian
     * no longer has an answer to.
     *
     * A notification that lands mid-launch is dropped rather than replayed, and
     * that is deliberate. Until Start Authentication has answered, the status
     * still describes the *previous* attempt - signing the last user out changes
     * it too - so replaying it would report their SUCCESS as this user's. There
     * is no signal to lose: the lock screen has to change state again to reach a
     * verdict, and that change notifies as well.
     */
    private fun observeAuthenticationStatus() {
        viewModelScope.launch {
            client.authenticationStatusChanges().collect { resolveIfAwaitingUser() }
        }
    }

    /** Reads the status, but only when an attempt is out waiting on the user. */
    private fun resolveIfAwaitingUser() {
        if (_uiState.value.phase !is AuthenticationPhase.AwaitingUser) return
        if (authenticationJob?.isActive == true) return

        authenticationJob = viewModelScope.launch { resolveAuthenticationStatus() }
    }

    /** Reads the status and commits it when it is a verdict. */
    private suspend fun resolveAuthenticationStatus() {
        // Null means "no verdict yet", which leaves the phase untouched.
        val resolved: AuthenticationPhase? =
            withAuthorization { client.getAuthenticationStatus() }.fold(
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
