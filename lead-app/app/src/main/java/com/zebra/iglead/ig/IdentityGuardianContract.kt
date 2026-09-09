package com.zebra.iglead.ig

import android.net.Uri

/**
 * Constants for the Identity Guardian content provider API.
 *
 * Reference: https://techdocs.zebra.com/identityguardian/3-1/api/
 */
object IdentityGuardianContract {

    /** Package that hosts Identity Guardian (Enterprise Lock Screen). */
    const val PACKAGE_NAME = "com.zebra.mdna.els"

    /** Permission Identity Guardian requires callers to hold. */
    const val PROVIDER_PERMISSION = "com.zebra.mdna.els.permission.PROVIDER"

    /** Authority of the Identity Guardian content provider. */
    const val AUTHORITY = "com.zebra.mdna.els.provider"

    /**
     * Base URI used with [android.content.ContentResolver.call]. The action goes in
     * the `method` argument and the API name in the `arg` argument.
     */
    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY/")

    /**
     * Get Current User Session (v2). Queried with
     * [android.content.ContentResolver.query]; the payload comes back as a
     * stringified JSON object in the cursor's extras under [KEY_RESULT].
     */
    val CURRENT_SESSION_URI: Uri = Uri.parse("content://$AUTHORITY/v2/currentsession")

    /** `method` value for every lock screen action API. */
    const val METHOD_LOCK_SCREEN_ACTION = "lockscreenaction"

    /** `arg` value that launches the lock screen for user verification. */
    const val API_START_AUTHENTICATION = "startauthentication"

    /** Bundle key Identity Guardian uses for every API response payload. */
    const val KEY_RESULT = "RESULT"

    /** Start Authentication input: which authentication scheme to apply. */
    const val KEY_USER_VERIFICATION = "user_verification"

    /** Start Authentication input: whether the lock screen blocks device access. */
    const val KEY_LAUNCH_FLAG = "launchflag"

    /**
     * The delegation scopes this app needs before the APIs above answer, one per
     * API. Each is used both as the MX AccessMgr `ServiceIdentifier` and as the
     * ZDM `delegation_scope`.
     *
     * The docs list Get Current User Session under its unversioned URI even
     * though callers query `v2/currentsession`, so both spellings are granted:
     * an unused scope is harmless, a missing one is not.
     */
    val DELEGATION_SCOPES: List<String> = listOf(
        "content://$AUTHORITY/$METHOD_LOCK_SCREEN_ACTION/$API_START_AUTHENTICATION",
        "content://$AUTHORITY/currentsession",
        CURRENT_SESSION_URI.toString(),
    )

    /**
     * What the APIs report in [KEY_RESULT] when the caller is not on the
     * allowlist. Matched case-insensitively on the word alone, because the exact
     * wording differs between Identity Guardian versions.
     */
    const val RESULT_UNAUTHORIZED = "unauthorized"
}

/**
 * Authentication schemes configured in Identity Guardian. Each scheme is defined
 * on the device (via StageNow or an EMM) and decides which credentials, factors
 * and prompts the lock screen presents.
 */
enum class AuthenticationScheme(val value: String) {
    SCHEME_1("authenticationScheme1"),
    SCHEME_2("authenticationScheme2"),
    SCHEME_3("authenticationScheme3"),
    SCHEME_4("authenticationScheme4"),
}

/**
 * Controls how the lock screen behaves once launched.
 *
 * [BLOCKING] locks the device until the user authenticates successfully;
 * [UNBLOCKING] lets the user dismiss the lock screen and keep using the device.
 */
enum class LaunchFlag(val value: String) {
    BLOCKING("blocking"),
    UNBLOCKING("unblocking"),
}

/**
 * Status strings returned in [IdentityGuardianContract.KEY_RESULT] by the
 * Start Authentication API.
 */
object AuthenticationState {
    /** The screen was successfully locked by Identity Guardian. */
    const val SUCCESS = "SUCCESS"

    /** The screen is in the process of being locked. */
    const val IN_PROGRESS = "IN_PROGRESS"

    /** The screen is already locked by another application. */
    const val BUSY = "BUSY"

    /** Identity Guardian hit an exception. */
    const val ERROR = "ERROR"
}
