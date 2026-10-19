package com.resume.profile.domain.domains

import com.ibm.icu.text.IDNA

/**
 * Hostname value object (feature 04 BR-02, D-2): normalization happens before
 * any comparison is made. Rules:
 *  - lowercase, trimmed, trailing dot stripped;
 *  - IDN accepted, stored as punycode (IDNA2008 / UTS #46 via ICU; the JDK's
 *    IDN is IDNA2003 only), the original unicode form is kept for display;
 *  - total length <= 253, labels 1-63, at least two labels (an apex record is
 *    `example.com`, never a bare token a public TXT could prove);
 *  - wildcards never - each hostname is verified individually.
 * Stored/displayed separately: [ascii] is the normalized punycode key
 * (the `hostname` column), [display] is the original form for the UI.
 */
data class Hostname(val ascii: String, val display: String) {

    val txtName: String get() = "$CHALLENGE_PREFIX$ascii"

    override fun toString(): String = ascii

    companion object {
        const val CHALLENGE_PREFIX = "_rb-challenge."
        const val CHALLENGE_ATTR = "rb-verify"
        const val MAX_TOTAL = 253
        const val MAX_LABEL = 63

        private val ASCII_HOST = Regex("^[a-z0-9](?:[a-z0-9-]*(?<!-))?$")

        /** Normalizes or throws [HostnameInvalidException] - the single normalizer (S-4). */
        fun of(raw: String): Hostname {
            val trimmed = raw.trim().trimEnd('.')
            if (trimmed.isEmpty()) throw HostnameInvalidException("Hostname is required.")
            if (trimmed.any { it == '*' }) throw HostnameInvalidException("Wildcards are never valid.")
            if (trimmed.length > MAX_TOTAL) throw HostnameInvalidException("Hostname exceeds 253 characters.")

            val display = trimmed.lowercase()
            val ascii = toPunycode(display)
            if (ascii.any { it == '*' }) throw HostnameInvalidException("Wildcards are never valid.")
            val labels = ascii.split('.')
            if (labels.size < 2) throw HostnameInvalidException("Enter a hostname like example.com or blog.example.com (no wildcards).")
            if (labels.any { it.isEmpty() }) {
                throw HostnameInvalidException("Enter a hostname like example.com or blog.example.com (no wildcards).")
            }
            if (labels.any { it.length > MAX_LABEL }) {
                throw HostnameInvalidException("Each label must be 1-63 characters.")
            }
            if (ascii.length > MAX_TOTAL) throw HostnameInvalidException("Hostname exceeds 253 characters.")
            return Hostname(ascii, display)
        }

        /** Single source of the IDNA2008 rules; failures fold into the stable code. */
        private fun toPunycode(display: String): String {
            val info = IDNA.Info()
            val converted = UTS46.nameToASCII(display, StringBuilder(), info).toString()
            if (info.hasErrors() || converted.isBlank()) {
                throw HostnameInvalidException("Enter a hostname like example.com or blog.example.com (no wildcards).")
            }
            val labels = converted.split('.')
            if (labels.any { !ASCII_HOST.matches(it) }) {
                throw HostnameInvalidException("Hostname labels support only letters, digits, and inner hyphens.")
            }
            return converted.lowercase()
        }

        /** Nontransitional UTS #46 instance (IDNA2008 semantics), created once. */
        private val UTS46: IDNA = IDNA.getUTS46Instance(
            IDNA.NONTRANSITIONAL_TO_ASCII or
                IDNA.NONTRANSITIONAL_TO_UNICODE or
                IDNA.USE_STD3_RULES or
                IDNA.CHECK_BIDI or
                IDNA.CHECK_CONTEXTO,
        )
    }
}
