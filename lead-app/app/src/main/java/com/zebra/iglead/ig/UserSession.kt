package com.zebra.iglead.ig

import org.json.JSONArray
import org.json.JSONObject

/** A single key/value pair from a session payload, ready to render. */
data class SessionField(
    val key: String,
    val label: String,
    val value: String,
)

/**
 * Parsed result of the Get Current User Session API.
 *
 * Identity Guardian returns the session as a stringified JSON object whose keys
 * depend on how the device is configured, so the payload is kept as an ordered
 * list of fields plus the pretty-printed original for reference.
 */
data class UserSession(
    val fields: List<SessionField>,
    val rawJson: String,
) {
    /** True when Identity Guardian reported no signed-in user. */
    val isEmpty: Boolean get() = fields.isEmpty()

    /** The signed-in user, or null when the payload did not carry one. */
    val userId: String? get() = valueOf("user_id")

    /** The role that user signed in with, or null when the payload had none. */
    val userRole: String? get() = valueOf("user_role")

    /** The value Identity Guardian returned for [key], or null when absent. */
    fun valueOf(key: String): String? = fields.firstOrNull { it.key == key }?.value

    companion object {

        /**
         * Keys shown first, in this order, because they are the ones a caller
         * usually cares about. Any other key follows in the order IG returned it.
         */
        private val PREFERRED_KEY_ORDER = listOf(
            "user_id",
            "user_role",
            "signed_in_state",
            "signin_time",
            "signout_time",
            "security_types",
            "storage_type",
            "valid_through",
            "barcode_id",
            "sso_provider",
        )

        /** Labels that simple prettifying would get wrong. */
        private val LABEL_OVERRIDES = mapOf(
            "user_id" to "User ID",
            "barcode_id" to "Barcode ID",
            "sso_provider" to "SSO Provider",
        )

        /** Parses the JSON string found under `RESULT`. */
        fun fromJson(rawJson: String): UserSession {
            val json = JSONObject(rawJson)
            val keys = json.keys().asSequence().toList()

            // Preferred keys first (only those actually present), then the rest.
            val orderedKeys = PREFERRED_KEY_ORDER.filter { json.has(it) } +
                keys.filterNot { it in PREFERRED_KEY_ORDER }

            val fields = orderedKeys.mapNotNull { key ->
                val value = json.opt(key)?.asDisplayValue() ?: return@mapNotNull null
                SessionField(key = key, label = labelFor(key), value = value)
            }

            return UserSession(fields = fields, rawJson = json.toString(2))
        }

        /** Turns `signin_time` into `Signin Time`, honouring [LABEL_OVERRIDES]. */
        private fun labelFor(key: String): String = LABEL_OVERRIDES[key]
            ?: key.split('_', '-')
                .filter { it.isNotEmpty() }
                .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }

        /** Renders a JSON value as a single display string, or null when absent. */
        private fun Any.asDisplayValue(): String? = when (this) {
            JSONObject.NULL -> null
            is JSONObject -> takeIf { it.length() > 0 }?.toString()
            is JSONArray -> takeIf { it.length() > 0 }?.toString()
            else -> toString().takeIf { it.isNotBlank() }
        }
    }
}
