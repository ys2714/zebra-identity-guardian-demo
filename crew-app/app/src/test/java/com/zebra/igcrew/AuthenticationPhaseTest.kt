package com.zebra.igcrew

import com.zebra.igcrew.ig.AuthenticationResult
import com.zebra.igcrew.ig.AuthenticationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the split between the two APIs: what Start Authentication answers
 * decides whether the lock screen came up, and what Get Authentication Status
 * answers decides how the user got on with it.
 */
class AuthenticationPhaseTest {

    // The bug this guards against: Start Authentication returns the moment
    // Identity Guardian accepts the request, so its IN_PROGRESS was being shown
    // as the result of authenticating and never replaced.
    @Test
    fun `in progress from the launch means the user is on the lock screen`() {
        assertEquals(
            AuthenticationPhase.AwaitingUser,
            launchPhase(resultOf(AuthenticationState.IN_PROGRESS)),
        )
    }

    // SUCCESS here only says the screen was locked, which is still the point at
    // which the user has yet to authenticate.
    @Test
    fun `success from the launch also means the user is on the lock screen`() {
        assertEquals(
            AuthenticationPhase.AwaitingUser,
            launchPhase(resultOf(AuthenticationState.SUCCESS)),
        )
    }

    @Test
    fun `busy and error from the launch are the verdict`() {
        assertEquals(
            AuthenticationPhase.Done(
                AuthenticationOutcome.Reported(AuthenticationState.BUSY, "BUSY")
            ),
            launchPhase(resultOf(AuthenticationState.BUSY)),
        )
        assertEquals(
            AuthenticationPhase.Done(
                AuthenticationOutcome.Reported(AuthenticationState.ERROR, "ERROR")
            ),
            launchPhase(resultOf(AuthenticationState.ERROR)),
        )
    }

    @Test
    fun `success from the status API is the authentication succeeding`() {
        assertEquals(
            AuthenticationPhase.Done(AuthenticationOutcome.Succeeded),
            statusPhase(resultOf(AuthenticationState.SUCCESS)),
        )
    }

    @Test
    fun `in progress from the status API is not a verdict yet`() {
        assertNull(statusPhase(resultOf(AuthenticationState.IN_PROGRESS)))
    }

    // The bug this guards against: once the flow is over Identity Guardian
    // clears the status, so re-opening the app queried it and got nothing back.
    // That was reported as "Authentication failed: Status query did not contain
    // a RESULT value" to a user who had signed in successfully.
    @Test
    fun `no status at all from the status API is not a failure`() {
        assertNull(statusPhase(null))
    }

    @Test
    fun `busy and error from the status API end the attempt`() {
        assertEquals(
            AuthenticationPhase.Done(
                AuthenticationOutcome.Reported(AuthenticationState.BUSY, "BUSY")
            ),
            statusPhase(resultOf(AuthenticationState.BUSY)),
        )
        assertEquals(
            AuthenticationPhase.Done(
                AuthenticationOutcome.Reported(AuthenticationState.ERROR, "ERROR")
            ),
            statusPhase(resultOf(AuthenticationState.ERROR)),
        )
    }

    // An unrecognised status must not leave the screen waiting forever; it is
    // reported verbatim instead.
    @Test
    fun `an unknown status from the status API is reported as it came back`() {
        val unknown = AuthenticationResult(state = null, rawResult = "SOMETHING_NEW")

        assertEquals(
            AuthenticationPhase.Done(AuthenticationOutcome.Reported(null, "SOMETHING_NEW")),
            statusPhase(unknown),
        )
    }

    // Waiting on the lock screen is not this app being busy: the user may come
    // back unfinished and needs the button to start over.
    @Test
    fun `only a call in flight counts as starting`() {
        assertEquals(true, MainUiState(phase = AuthenticationPhase.Starting).isStarting)
        assertEquals(false, MainUiState(phase = AuthenticationPhase.AwaitingUser).isStarting)
        assertEquals(false, MainUiState(phase = AuthenticationPhase.Idle).isStarting)
    }

    private fun resultOf(state: AuthenticationState) =
        AuthenticationResult(state = state, rawResult = state.value)
}
