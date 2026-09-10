package com.zebra.iglead.ig

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `exposes the user and role the login form needs`() {
        val json = """{"user_id":"jdoe","user_role":"Manager"}"""

        val session = UserSession.fromJson(json)

        assertEquals("jdoe", session.userId)
        assertEquals("Manager", session.userRole)
    }

    @Test
    fun `reports a missing user or role as null`() {
        val session = UserSession.fromJson("""{"user_id":"jdoe"}""")

        assertEquals("jdoe", session.userId)
        assertNull(session.userRole)
    }

    @Test
    fun `an empty payload yields an empty session`() {
        val session = UserSession.fromJson("{}")

        assertTrue(session.isEmpty)
    }

    // The v2 API is the one this app queries, and it neither spells these keys in
    // snake_case nor keeps them at the top level. Reading only `user_id` and
    // `user_role` off the root is why the form used to come up blank.
    @Test
    fun `reads the user and role out of a v2 payload`() {
        val session = UserSession.fromJson(V2_PAYLOAD)

        assertEquals("user1@zebra.com", session.userId)
        assertEquals("Manager", session.userRole)
        assertTrue(session.isSignedIn)
    }

    @Test
    fun `flattens nested objects to their paths`() {
        val session = UserSession.fromJson(V2_PAYLOAD)

        val keys = session.fields.map { it.key }
        assertTrue("userInformation.userId" in keys)
        assertTrue("loginInformation.userLoginTime" in keys)
    }

    @Test
    fun `looks a value up by path or by name`() {
        val session = UserSession.fromJson(V2_PAYLOAD)

        assertEquals("user1@zebra.com", session.valueOf("userInformation.userId"))
        assertEquals("user1@zebra.com", session.valueOf("userId"))
        assertEquals("Login", session.valueOf("eventType"))
        assertNull(session.valueOf("nothing_like_this"))
    }

    @Test
    fun `prefers a top level userId over a nested one`() {
        val json = """{"userId":"top","userInformation":{"userId":"nested"}}"""

        assertEquals("top", UserSession.fromJson(json).userId)
    }

    @Test
    fun `falls back to the proxy mode user name`() {
        val json = """{"userInformation":{"userName":"JohnD","displayName":"John Doe"}}"""

        assertEquals("JohnD", UserSession.fromJson(json).userId)
    }

    @Test
    fun `prettifies camelCase labels`() {
        val json = """{"userLoginTime":"1737983880029","userId":"jdoe","ssoProvider":"OKTA"}"""

        val labels = UserSession.fromJson(json).associateLabelsByKey()

        assertEquals("User Login Time", labels["userLoginTime"])
        assertEquals("User ID", labels["userId"])
        assertEquals("SSO Provider", labels["ssoProvider"])
    }

    @Test
    fun `orders the v2 user and role ahead of the rest`() {
        val session = UserSession.fromJson(V2_PAYLOAD)

        assertEquals(
            listOf("userInformation.userId", "userInformation.userRole"),
            session.fields.take(2).map { it.key },
        )
    }

    // v2 answers the query with nobody signed in too, so a payload that parses is
    // not by itself a session - the form would otherwise show a stale user.
    @Test
    fun `a logged out v2 payload is not a session`() {
        val json = """{"userLoggedInState":"0","errorCode":0,"status":"SUCCESS"}"""

        val session = UserSession.fromJson(json)

        assertFalse(session.isSignedIn)
        assertNull(session.userId)
    }

    @Test
    fun `a legacy payload with no logged-in flag counts the user as the signal`() {
        assertTrue(UserSession.fromJson("""{"user_id":"jdoe"}""").isSignedIn)
        assertFalse(UserSession.fromJson("""{"storage_type":"BARCODE"}""").isSignedIn)
    }

    private fun UserSession.associateLabelsByKey(): Map<String, String> =
        fields.associate { it.key to it.label }

    private companion object {
        /**
         * Shaped after the sample response in the Identity Guardian 3.1 docs for
         * Get Current User Session (v2), trimmed to what this app reads.
         */
        val V2_PAYLOAD = """
            {
                "authenticationFactors": {
                    "adminByPassFactors": [],
                    "factors": [
                        {"factor":"PASSCODE","factorType":"PRIMARYPRIMARYFACTOR","status":"EXECUTED"}
                    ],
                    "schemaVersion": "1.0"
                },
                "enrollmentInformation": {
                    "enrollmentId": "1737983906027D4BA34E",
                    "enrollmentType": "BARCODE",
                    "storageType": "BARCODE"
                },
                "loginInformation": {"userLoginTime": "1737983880029"},
                "userInformation": {
                    "userId": "user1@zebra.com",
                    "userRole": "Manager"
                },
                "errorCode": 0,
                "eventType": "Login",
                "status": "SUCCESS",
                "userLoggedInState": "1"
            }
        """.trimIndent()
    }
}
