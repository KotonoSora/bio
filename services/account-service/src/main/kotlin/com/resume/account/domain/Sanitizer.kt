package com.resume.account.domain

import org.springframework.stereotype.Component

/**
 * Prefill safety (BR-15): IdP-supplied text is stripped of markup and control
 * characters before storage; never rendered as HTML anywhere (AC-15).
 * IdP avatar URLs are never hotlinked or stored by this feature (image_key is
 * reserved for feature 09's server-side prefill).
 */
@Component
class Sanitizer {
    private val markup = Regex("<[^>]*>")
    private val control = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
    private val bidi = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    fun cleanName(raw: String?): String? {
        val value = raw ?: return null
        val cleaned = value
            .replace(markup, "")
            .replace(bidi, "")
            .replace(control, "")
            .trim()
            .take(100)
        return cleaned.ifBlank { null }
    }
}
