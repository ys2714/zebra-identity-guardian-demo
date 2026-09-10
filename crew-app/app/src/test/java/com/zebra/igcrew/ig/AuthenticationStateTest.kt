package com.zebra.igcrew.ig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers the mapping of the `RESULT` string returned by Start Authentication. */
class AuthenticationStateTest {

    @Test
    fun `maps every documented status`() {
        assertEquals(AuthenticationState.SUCCESS, AuthenticationState.fromResult("SUCCESS"))
        assertEquals(AuthenticationState.IN_PROGRESS, AuthenticationState.fromResult("IN_PROGRESS"))
        assertEquals(AuthenticationState.BUSY, AuthenticationState.fromResult("BUSY"))
        assertEquals(AuthenticationState.ERROR, AuthenticationState.fromResult("ERROR"))
    }

    @Test
    fun `ignores case and surrounding whitespace`() {
        assertEquals(AuthenticationState.SUCCESS, AuthenticationState.fromResult(" success \n"))
    }

    @Test
    fun `reports an unknown status as null so the raw value can be shown`() {
        assertNull(AuthenticationState.fromResult("SOMETHING_NEW"))
        assertNull(AuthenticationState.fromResult(""))
    }

    @Test
    fun `verification 3 is authenticationScheme3`() {
        assertEquals("authenticationScheme3", AuthenticationScheme.VERIFICATION_3.value)
    }

    // What Identity Guardian 3.1 on an EM45 actually answers. Comparing the whole
    // payload against the status words matched nothing, so every answer used to
    // come out "unrecognised" with this JSON shown to the user.
    @Test
    fun `reads the status out of a JSON result`() {
        val json = """{"code":0,"message":"Authentication under process by Device lock. """ +
            """Try after some time","status":"BUSY"}"""

        assertEquals(AuthenticationState.BUSY, AuthenticationState.fromResult(json))
        assertEquals(
            "Authentication under process by Device lock. Try after some time",
            AuthenticationState.messageOf(json),
        )
    }

    @Test
    fun `reads success out of a JSON result`() {
        assertEquals(
            AuthenticationState.SUCCESS,
            AuthenticationState.fromResult("""{"code":0,"status":"SUCCESS"}"""),
        )
    }

    @Test
    fun `still reads the bare status string`() {
        assertEquals(AuthenticationState.BUSY, AuthenticationState.fromResult("BUSY"))
        assertNull(AuthenticationState.messageOf("BUSY"))
    }

    @Test
    fun `survives a JSON result with no status`() {
        assertNull(AuthenticationState.fromResult("""{"code":0}"""))
    }

    // Malformed JSON must not throw out of the parse - the call already succeeded.
    @Test
    fun `survives something that only looks like JSON`() {
        assertNull(AuthenticationState.fromResult("""{"status":"""))
        assertNull(AuthenticationState.messageOf("""{"status":"""))
    }
}
