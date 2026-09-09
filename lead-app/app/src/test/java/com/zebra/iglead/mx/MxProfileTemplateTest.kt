package com.zebra.iglead.mx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the placeholder substitution the AccessMgr profile relies on. */
class MxProfileTemplateTest {

    @Test
    fun `fills every placeholder`() {
        val template = """
            <parm name="ServiceIdentifier" value="=[ServiceIdentifier]" />
            <parm name="CallerPackageName" value="=[CallerPackageName]" />
        """.trimIndent()

        val filled = MxProfileTemplate.fill(
            template,
            mapOf(
                "ServiceIdentifier" to "content://com.zebra.mdna.els.provider/currentsession",
                "CallerPackageName" to "com.zebra.iglead",
            ),
        )

        assertEquals(
            """
            <parm name="ServiceIdentifier" value="content://com.zebra.mdna.els.provider/currentsession" />
            <parm name="CallerPackageName" value="com.zebra.iglead" />
            """.trimIndent(),
            filled,
        )
    }

    @Test
    fun `rejects a template with a missing value`() {
        val template = """<parm name="CallerSignature" value="=[CallerSignature]" />"""

        val error = assertThrows(MxException::class.java) {
            MxProfileTemplate.fill(template, mapOf("ServiceIdentifier" to "content://x"))
        }

        // The message has to name the placeholder, since a profile that keeps one
        // would allowlist the literal "=[CallerSignature]" string.
        assertTrue(error.message!!.contains("=[CallerSignature]"))
    }

    @Test
    fun `leaves a template without placeholders untouched`() {
        val template = """<parm name="OperationMode" value="1" />"""

        assertEquals(template, MxProfileTemplate.fill(template, emptyMap()))
    }
}
