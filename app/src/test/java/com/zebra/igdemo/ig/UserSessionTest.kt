package com.zebra.igdemo.ig

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the parsing of the stringified JSON payload returned by the session API. */
class UserSessionTest {

    @Test
    fun `orders preferred keys first and keeps the rest`() {
        val json = JSONObject(
            mapOf(
                "storage_type" to "internal",
                "user_role" to "operator",
                "custom_field" to "abc",
                "user_id" to "jdoe",
            )
        ).toString()

        val session = UserSession.fromJson(json)

        // user_id / user_role / storage_type come from the preferred order; the
        // unrecognised key follows.
        assertEquals(
            listOf("user_id", "user_role", "storage_type", "custom_field"),
            session.fields.map { it.key },
        )
    }

    @Test
    fun `prettifies labels and honours overrides`() {
        val json = JSONObject(
            mapOf(
                "user_id" to "jdoe",
                "signin_time" to "2026-09-04T08:15:00Z",
                "sso_provider" to "okta",
            )
        ).toString()

        val labels = UserSession.fromJson(json).associateLabelsByKey()

        assertEquals("User ID", labels["user_id"])
        assertEquals("Signin Time", labels["signin_time"])
        assertEquals("SSO Provider", labels["sso_provider"])
    }

    @Test
    fun `drops null and blank values`() {
        val json = """{"user_id":"jdoe","user_role":null,"barcode_id":"  "}"""

        val session = UserSession.fromJson(json)

        assertEquals(listOf("user_id"), session.fields.map { it.key })
    }

    @Test
    fun `renders nested values as JSON`() {
        val json = """{"security_types":["pin","barcode"]}"""

        val session = UserSession.fromJson(json)

        assertEquals("""["pin","barcode"]""", session.fields.single().value)
    }

    @Test
    fun `an empty payload yields an empty session`() {
        val session = UserSession.fromJson("{}")

        assertTrue(session.isEmpty)
    }

    private fun UserSession.associateLabelsByKey(): Map<String, String> =
        fields.associate { it.key to it.label }
}
