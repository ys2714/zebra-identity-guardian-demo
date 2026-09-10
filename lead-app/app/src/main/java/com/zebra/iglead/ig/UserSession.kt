package com.zebra.iglead.ig

import org.json.JSONArray
import org.json.JSONObject

/** A single value from a session payload, ready to render. */
data class SessionField(
    /** Dotted path the value sits at, e.g. `userInformation.userId`. */
    val key: String,
    val label: String,
    val value: String,
) {
    /** The last path segment: what the payload itself calls this value. */
    val name: String get() = key.substringAfterLast('.')
}

/**
 * Parsed result of the Get Current User Session API.
 *
 * Identity Guardian returns the session as a stringified JSON object whose keys
 * depend on how the device is configured, so the payload is kept as an ordered
 * list of fields plus the pretty-printed original for reference.
 *
 * The v2 payload nests: the user and the role a caller usually wants sit inside
 * `userInformation`, not at the top level. Parsing therefore flattens the object
 * into leaf values keyed by their path, and lookups match on the trailing name
 * as well, so a caller does not have to know how deep a value was buried.
 */
data class UserSession(
    val fields: List<SessionField>,
    val rawJson: String,
) {
    /** True when Identity Guardian returned no payload at all. */
    val isEmpty: Boolean get() = fields.isEmpty()

    /** The signed-in user, or null when the payload did not carry one. */
    val userId: String? get() = firstValueOf(USER_ID_KEYS)

    /** The role that user signed in with, or null when the payload had none. */
    val userRole: String? get() = firstValueOf(USER_ROLE_KEYS)

    /**
     * True when the payload describes a user who is signed in *now*.
     *
     * v2 answers the query even with nobody signed in — it just says so in
     * `userLoggedInState` — so a non-empty payload is not by itself a session.
     * Older payloads carry no such flag, where having a user is the only signal.
     */
    val isSignedIn: Boolean
        get() = when (valueOf(KEY_LOGGED_IN_STATE)?.trim()?.lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> userId != null
        }

    /**
     * The value Identity Guardian returned for [key], or null when absent.
     *
     * [key] may be a full dotted path or just the name of a value at any depth;
     * both are matched case-insensitively, since the spelling of these keys has
     * changed between Identity Guardian versions.
     */
    fun valueOf(key: String): String? =
        fields.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            ?: fields.firstOrNull { it.name.equals(key, ignoreCase = true) }?.value

    /** The first of [keys] the payload actually carries a non-blank value for. */
    private fun firstValueOf(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key -> valueOf(key)?.takeIf { it.isNotBlank() } }

    companion object {

        /**
         * Where the signed-in user turns up, best spelling first. v2 uses
         * `userId`; the legacy payload used `user_id`; a Proxy Mode session
         * identifies the user by `userName` instead.
         */
        private val USER_ID_KEYS = listOf("userId", "user_id", "userName", "user_name")

        /** Where that user's role turns up. v2 uses `userRole`. */
        private val USER_ROLE_KEYS = listOf("userRole", "user_role", "role")

        /** v2's flag for whether anybody is signed in. */
        private const val KEY_LOGGED_IN_STATE = "userLoggedInState"

        /**
         * Names shown first, in this order, because they are the ones a caller
         * usually cares about. Both the v2 and the legacy spelling are listed so
         * either payload comes out in a sensible order; any other name follows in
         * the order Identity Guardian returned it.
         */
        private val PREFERRED_KEY_ORDER = listOf(
            "userId",
            "user_id",
            "userRole",
            "user_role",
            "userName",
            "displayName",
            "domain",
            "userLoggedInState",
            "signed_in_state",
            "eventType",
            "status",
            "userLoginTime",
            "signin_time",
            "signout_time",
            "securityTypes",
            "security_types",
            "storageType",
            "storage_type",
            "enrollmentId",
            "barcode_id",
            "ssoProvider",
            "sso_provider",
            "valid_through",
            "errorCode",
        )

        /** Labels that simple prettifying would get wrong, keyed in lower case. */
        private val LABEL_OVERRIDES = mapOf(
            "user_id" to "User ID",
            "userid" to "User ID",
            "barcode_id" to "Barcode ID",
            "enrollmentid" to "Enrollment ID",
            "sso_provider" to "SSO Provider",
            "ssoprovider" to "SSO Provider",
            "ssoidtoken" to "SSO ID Token",
            "ssoaccesstoken" to "SSO Access Token",
        )

        /** Word break inside a camelCase name, as in `userLoginTime`. */
        private val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")

        /** Parses the JSON string found under `RESULT`. */
        fun fromJson(rawJson: String): UserSession {
            val json = JSONObject(rawJson)

            // sortedBy is stable, so unrecognised names keep the payload's own
            // order behind the preferred ones.
            val fields = json.flatten(prefix = "").sortedBy { field ->
                PREFERRED_KEY_ORDER
                    .indexOfFirst { it.equals(field.name, ignoreCase = true) }
                    .takeIf { it >= 0 }
                    ?: PREFERRED_KEY_ORDER.size
            }

            return UserSession(fields = fields, rawJson = json.toString(2))
        }

        /**
         * Walks this object into a flat list of leaf values, each keyed by the
         * dotted path it was found at.
         *
         * Nested objects are walked, because that is where v2 keeps the user and
         * the role. Arrays are left as JSON: their entries are lists of
         * authentication factors rather than individually addressable values.
         */
        private fun JSONObject.flatten(prefix: String): List<SessionField> = buildList {
            for (key in keys()) {
                val path = if (prefix.isEmpty()) key else "$prefix.$key"

                when (val value = opt(key)) {
                    null, JSONObject.NULL -> Unit

                    is JSONObject -> addAll(value.flatten(path))

                    else -> value.asDisplayValue()?.let { display ->
                        add(SessionField(key = path, label = labelFor(key), value = display))
                    }
                }
            }
        }

        /** Turns `signin_time` and `userLoginTime` alike into `Signin Time`. */
        private fun labelFor(name: String): String = LABEL_OVERRIDES[name.lowercase()]
            ?: name.splitWords().joinToString(" ") { word ->
                word.replaceFirstChar(Char::uppercaseChar)
            }

        /** Splits a name on separators and on camelCase humps. */
        private fun String.splitWords(): List<String> = split('_', '-')
            .filter { it.isNotEmpty() }
            .flatMap { part -> CAMEL_CASE_BOUNDARY.split(part) }
            .filter { it.isNotEmpty() }

        /** Renders a JSON value as a single display string, or null when absent. */
        private fun Any.asDisplayValue(): String? = when (this) {
            JSONObject.NULL -> null
            is JSONArray -> takeIf { it.length() > 0 }?.toString()
            else -> toString().takeIf { it.isNotBlank() }
        }
    }
}
