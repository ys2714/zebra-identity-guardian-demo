package com.zebra.igcrew.ig

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Something went wrong talking to Identity Guardian.
 *
 * @param hint an actionable next step for the operator, when one is known.
 * @param needsAuthorization true when the call was refused for want of a
 * delegation scope, which is the one failure this app can do something about.
 * It is what tells the caller the MX/ZDM work is worth attempting; every other
 * failure would not be helped by it.
 */
class IdentityGuardianException(
    message: String,
    val hint: String? = null,
    val needsAuthorization: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

/** True when [this] failed for want of a delegation scope. */
internal fun Throwable.needsAuthorization(): Boolean =
    (this as? IdentityGuardianException)?.needsAuthorization == true

/**
 * What Start Authentication or Get Authentication Status answered.
 *
 * @param state the recognised status, or null when Identity Guardian returned
 * something outside [AuthenticationState].
 * @param rawResult the `RESULT` string exactly as it came back, so an unknown
 * status can still be shown.
 */
data class AuthenticationResult(
    val state: AuthenticationState?,
    val rawResult: String,
    /** Identity Guardian's own explanation, when its answer carried one. */
    val message: String? = null,
) {
    /** True when Identity Guardian reported [AuthenticationState.SUCCESS]. */
    val isSuccess: Boolean get() = state == AuthenticationState.SUCCESS

    companion object {
        /** Reads a `RESULT` payload, in either the JSON or the bare-string shape. */
        fun parse(rawResult: String): AuthenticationResult = AuthenticationResult(
            state = AuthenticationState.fromResult(rawResult),
            rawResult = rawResult,
            message = AuthenticationState.messageOf(rawResult),
        )
    }
}

/**
 * Thin wrapper over the Identity Guardian content provider API.
 *
 * Every call touches a content provider in another process, so all of them are
 * suspending and run on [dispatcher] rather than the main thread.
 */
class IdentityGuardianClient(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Start Authentication: asks Identity Guardian to show its lock screen so the
     * user can authenticate with [scheme].
     *
     * This answers as soon as Identity Guardian has taken the request, so the
     * status it returns describes the launch of the lock screen and *not* what
     * the user then did on it. Use [getAuthenticationStatus] for that.
     */
    suspend fun startAuthentication(
        scheme: AuthenticationScheme = AuthenticationScheme.VERIFICATION_3,
        launchFlag: LaunchFlag = LaunchFlag.BLOCKING,
    ): Result<AuthenticationResult> = runProviderCall {
        val extras = Bundle().apply {
            putString(IdentityGuardianContract.KEY_USER_VERIFICATION, scheme.value)
            putString(IdentityGuardianContract.KEY_LAUNCH_FLAG, launchFlag.value)
        }

        // The action is the `method` argument and the API name is the `arg`.
        val response: Bundle = contentResolver.call(
            IdentityGuardianContract.BASE_URI,
            IdentityGuardianContract.METHOD_LOCK_SCREEN_ACTION,
            IdentityGuardianContract.API_START_AUTHENTICATION,
            extras,
        ) ?: throw IdentityGuardianException(
            message = "Identity Guardian returned no response bundle.",
            hint = "Confirm Identity Guardian is running and its lock screen is enabled.",
        )

        val result = response.getString(IdentityGuardianContract.KEY_RESULT)
            ?: throw IdentityGuardianException(
                message = "Response did not contain a \"${IdentityGuardianContract.KEY_RESULT}\" value.",
            )

        // A missing delegation scope comes back as a plain status string rather
        // than a SecurityException, so surface it as the failure it is.
        if (result.contains(IdentityGuardianContract.RESULT_UNAUTHORIZED, ignoreCase = true)) {
            throw IdentityGuardianException(
                message = "Identity Guardian rejected the call: $result",
                hint = AUTHORIZATION_HINT,
                needsAuthorization = true,
            )
        }

        AuthenticationResult.parse(result)
    }

    /**
     * Get Authentication Status: reads where the lock screen Start Authentication
     * put up has got to.
     *
     * This is the API that says whether the user finished authenticating, which
     * is why the screen calls it once the lock screen is out of the way instead
     * of believing what Start Authentication returned.
     *
     * The status arrives in the cursor's extras rather than as cursor rows, which
     * is why the cursor itself is never iterated.
     *
     * Null means Identity Guardian has no lock screen action to report on. That
     * is what the extras look like once a flow is over and Identity Guardian has
     * cleared the status - "nothing to say", which is emphatically not the same
     * as the authentication having failed. Reporting it as a failure is what put
     * `Status query did not contain a "RESULT" value` under a screen whose user
     * had in fact signed in successfully.
     */
    suspend fun getAuthenticationStatus(): Result<AuthenticationResult?> = runProviderCall {
        val result = contentResolver.query(
            IdentityGuardianContract.AUTHENTICATION_STATUS_URI,
            /* projection = */ null,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ null,
        ).use { cursor ->
            val status = cursor ?: throw IdentityGuardianException(
                message = "Identity Guardian did not return a cursor for the status query.",
                hint = AUTHORIZATION_HINT,
                needsAuthorization = true,
            )
            status.extras?.getString(IdentityGuardianContract.KEY_RESULT)
        } ?: return@runProviderCall null

        // As with Start Authentication, a missing delegation scope comes back as
        // a plain status string rather than a SecurityException.
        if (result.contains(IdentityGuardianContract.RESULT_UNAUTHORIZED, ignoreCase = true)) {
            throw IdentityGuardianException(
                message = "Identity Guardian rejected the status query: $result",
                hint = AUTHORIZATION_HINT,
                needsAuthorization = true,
            )
        }

        AuthenticationResult.parse(result)
    }

    /**
     * Emits every time Identity Guardian changes the authentication status.
     *
     * This is the mechanism the API docs prescribe for this URI, and it is the
     * only one that works while this app is in the background. Waiting to be
     * resumed is not enough: Identity Guardian dismisses its lock screen to
     * whatever the system decides comes next, which need not be this app, so an
     * attempt whose verdict is only read on resume can be missed altogether.
     *
     * The flow only says *that* the status changed; the value still comes from
     * [getAuthenticationStatus].
     */
    fun authenticationStatusChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        try {
            contentResolver.registerContentObserver(
                IdentityGuardianContract.AUTHENTICATION_STATUS_URI,
                /* notifyForDescendants = */ false,
                observer,
            )
        } catch (e: SecurityException) {
            // Nothing to observe, but the resume-driven read still works, so
            // this closes quietly rather than failing the collector.
            close()
            return@callbackFlow
        } catch (e: IllegalArgumentException) {
            // The provider (or the URI) could not be resolved at all.
            close()
            return@callbackFlow
        }

        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }

    /**
     * Logout User: ends the session of whoever Identity Guardian currently has
     * signed in.
     *
     * Called before Start Authentication, because Identity Guardian does not put
     * its lock screen up for a user it has already authenticated - so without
     * this, re-running the demo produces an answer from Start Authentication and
     * no lock screen for the user to authenticate on.
     *
     * The docs note this only applies while Proxy Mode is inactive, and on the
     * first run of the day there is no session to end, so the caller treats a
     * failure here as nothing to act on.
     */
    suspend fun logout(): Result<AuthenticationResult?> = runProviderCall {
        val response: Bundle = contentResolver.call(
            IdentityGuardianContract.BASE_URI,
            IdentityGuardianContract.METHOD_LOCK_SCREEN_ACTION,
            IdentityGuardianContract.API_LOGOUT,
            /* extras = */ null,
        ) ?: return@runProviderCall null

        val result = response.getString(IdentityGuardianContract.KEY_RESULT)
            ?: return@runProviderCall null

        if (result.contains(IdentityGuardianContract.RESULT_UNAUTHORIZED, ignoreCase = true)) {
            throw IdentityGuardianException(
                message = "Identity Guardian rejected the logout: $result",
                hint = AUTHORIZATION_HINT,
                needsAuthorization = true,
            )
        }

        AuthenticationResult.parse(result)
    }

    /**
     * Runs [block] on [dispatcher] and turns the failure modes of a cross-process
     * provider call into an [IdentityGuardianException] with a usable hint.
     */
    private suspend fun <T> runProviderCall(block: () -> T): Result<T> = withContext(dispatcher) {
        try {
            Result.success(block())
        } catch (e: IdentityGuardianException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(
                IdentityGuardianException(
                    message = "Access to the Identity Guardian provider was denied.",
                    hint = AUTHORIZATION_HINT,
                    needsAuthorization = true,
                    cause = e,
                )
            )
        } catch (e: IllegalArgumentException) {
            // Thrown when the provider (or the URI) cannot be resolved at all.
            Result.failure(
                IdentityGuardianException(
                    message = "The Identity Guardian provider is unavailable: ${e.message}",
                    hint = "Install/enable Identity Guardian (${IdentityGuardianContract.PACKAGE_NAME}) " +
                        "and verify the API URI is supported by this version.",
                    cause = e,
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IdentityGuardianException(
                    message = e.message ?: e::class.java.simpleName,
                    cause = e,
                )
            )
        }
    }

    private companion object {
        /** Shared next step for every "we are not authorized" failure. */
        const val AUTHORIZATION_HINT = "This app needs a ZDM delegation scope for this API URI: " +
            "an MX AccessMgr profile naming the URI, plus a ZDM token for it. " +
            "Retry the in-app authorization, or stage the AccessMgr profile with StageNow/your EMM."
    }
}
