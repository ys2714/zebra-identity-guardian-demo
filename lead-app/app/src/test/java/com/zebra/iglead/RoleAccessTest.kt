package com.zebra.iglead

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the role gate: which Identity Guardian roles get the login form.
 *
 * The list is a deny list, so the interesting cases are the role that is on it,
 * the roles that are not, and the sloppy spellings that should still be caught.
 */
class RoleAccessTest {

    @Test
    fun `turns a blocked role away`() {
        assertEquals(AccessState.Denied("Parttimer"), roleAccess("Parttimer", BLOCKED))
    }

    @Test
    fun `lets a lead role through`() {
        assertEquals(AccessState.Granted, roleAccess("Manager", BLOCKED))
    }

    // The role string comes from the SSO provider, so its casing and padding are
    // not something this app gets to rely on.
    @Test
    fun `matches regardless of case and padding`() {
        assertEquals(AccessState.Denied("PARTTIMER"), roleAccess("  PARTTIMER  ", BLOCKED))
        assertEquals(AccessState.Denied("parttimer"), roleAccess("parttimer", BLOCKED))
    }

    // Deny list, not an allow list: an unrecognised role keeps working, so the
    // demo survives a device whose roles are configured differently.
    @Test
    fun `lets an unknown role through`() {
        assertEquals(AccessState.Granted, roleAccess("Casual", BLOCKED))
    }

    @Test
    fun `lets a session with no role through`() {
        assertEquals(AccessState.Granted, roleAccess("", BLOCKED))
        assertEquals(AccessState.Granted, roleAccess("   ", BLOCKED))
    }

    @Test
    fun `blocks nobody when the list is empty`() {
        assertEquals(AccessState.Granted, roleAccess("Parttimer", emptyList()))
    }

    private companion object {
        /** Matches the shipped `R.array.blocked_roles`. */
        val BLOCKED = listOf("Parttimer")
    }
}
