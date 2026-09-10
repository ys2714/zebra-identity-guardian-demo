package com.zebra.igcrew.ig

import android.net.Uri

/**
 * Constants for the Identity Guardian content provider API.
 *
 * This app calls two APIs: Start Authentication puts the lock screen up, and Get
 * Authentication Status reports how the user got on with it. Only their URIs,
 * inputs and response values are declared here.
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

    /** `method` value for every lock screen action API. */
    const val METHOD_LOCK_SCREEN_ACTION = "lockscreenaction"

    /** `arg` value that launches the lock screen for user verification. */
    const val API_START_AUTHENTICATION = "startauthentication"

    /** `arg` value that reports the state of the lock screen action in flight. */
    const val API_AUTHENTICATION_STATUS = "authenticationstatus"

    /**
     * Get Authentication Status. Unlike Start Authentication this one is a
     * [android.content.ContentResolver.query], and the status comes back in the
     * cursor's extras under [KEY_RESULT].
     *
     * Start Authentication answers the moment it is invoked, so its `RESULT`
     * only says whether the lock screen was *launched*. This URI is what reports
     * where that lock screen actually got to afterwards.
     */
    val AUTHENTICATION_STATUS_URI: Uri =
        Uri.parse("content://$AUTHORITY/$METHOD_LOCK_SCREEN_ACTION/$API_AUTHENTICATION_STATUS")

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
     */
    val DELEGATION_SCOPES: List<String> = listOf(
        "content://$AUTHORITY/$METHOD_LOCK_SCREEN_ACTION/$API_START_AUTHENTICATION",
        AUTHENTICATION_STATUS_URI.toString(),
    )

    /**
     * What the API reports in [KEY_RESULT] when the caller is not on the
     * allowlist. Matched case-insensitively on the word alone, because the exact
     * wording differs between Identity Guardian versions.
     */
    const val RESULT_UNAUTHORIZED = "unauthorized"
}

/**
 * Authentication schemes configured in Identity Guardian. Each scheme is defined
 * on the device (via StageNow or an EMM) and decides which credentials, factors
 * and prompts the lock screen presents.
 *
 * The Identity Guardian configuration screens label these "Verification 1" to
 * "Verification 4"; the API takes the `authenticationSchemeN` spelling, so
 * Verification 3 is [VERIFICATION_3].
 */
enum class AuthenticationScheme(val value: String) {
    VERIFICATION_1("authenticationScheme1"),
    VERIFICATION_2("authenticationScheme2"),
    VERIFICATION_3("authenticationScheme3"),
    VERIFICATION_4("authenticationScheme4"),
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
 * Status strings Identity Guardian returns in [IdentityGuardianContract.KEY_RESULT].
 *
 * Both Start Authentication and Get Authentication Status answer with these four
 * values, but they mean different things: from Start Authentication the value
 * describes the *launch* of the lock screen, while from Get Authentication Status
 * it describes where that lock screen has since got to.
 */
enum class AuthenticationState(val value: String) {

    /** The screen was successfully locked by Identity Guardian. */
    SUCCESS("SUCCESS"),

    /** The screen is in the process of being locked. */
    IN_PROGRESS("IN_PROGRESS"),

    /** The screen is already locked by another application. */
    BUSY("BUSY"),

    /** Identity Guardian hit an exception. */
    ERROR("ERROR");

    companion object {
        /**
         * Matches [result] to a known state, or null when Identity Guardian
         * answered with something this app does not know about.
         */
        fun fromResult(result: String): AuthenticationState? {
            val trimmed = result.trim()
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        }
    }
}
