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
}
