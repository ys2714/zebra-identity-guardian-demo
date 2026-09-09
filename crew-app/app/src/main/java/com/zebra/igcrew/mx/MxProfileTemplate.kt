package com.zebra.igcrew.mx

import android.content.Context

/**
 * Fills the `=[Name]` placeholders of an MX profile XML template kept in assets.
 *
 * MX profiles are plain XML, so the demo ships the profile as an asset and only
 * substitutes the values that change per call. This mirrors how the Zebra Kotlin
 * wrapper feeds profiles to EMDK, minus its line-by-line asset reader.
 */
object MxProfileTemplate {

    /** Reads [assetName] from assets and fills it with [params]. */
    fun read(context: Context, assetName: String, params: Map<String, String>): String {
        val template = context.assets.open(assetName).use { input ->
            input.reader().readText()
        }
        return fill(template, params)
    }

    /**
     * Replaces every `=[key]` placeholder in [template] with its value.
     *
     * @throws MxException when a value is missing, so a half-filled profile is
     * never handed to MX (it would silently allowlist the literal placeholder).
     */
    fun fill(template: String, params: Map<String, String>): String {
        var filled = template
        params.forEach { (key, value) ->
            filled = filled.replace("=[$key]", value)
        }

        val leftover = PLACEHOLDER_REGEX.find(filled)?.value
        if (leftover != null) {
            throw MxException("MX profile placeholder $leftover was not filled in.")
        }
        return filled
    }

    /** Matches a `=[Name]` placeholder. */
    private val PLACEHOLDER_REGEX = Regex("""=\[[^\]]*\]""")
}
