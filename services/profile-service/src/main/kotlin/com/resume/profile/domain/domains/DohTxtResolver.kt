package com.resume.profile.domain.domains

/** TXT answer port: `DomainsService` stays DNS-transport agnostic (feature 04 D-4). */
interface TxtLookupPort {
    /** TXT strings for the name, or an empty list on NXDOMAIN / transport failure. */
    fun txt(name: String): List<String>
}

/**
 * DoH TXT checks (feature 04 D-4): fetch-only lookup via the RFC 8482 JSON
 * API. Failures return an empty answer list - a verification NEVER succeeds
 * without a TXT match (fail-closed, D-6); `lastError` feeds the degrade log
 * only, never responses (NFR-06: no probing-data echo).
 */
class DohTxtResolver(
    private val resolver: String,
    private val fetch: (url: String) -> String?,
) : TxtLookupPort {
    @Volatile
    var lastError: String? = null
        private set

    override fun txt(name: String): List<String> {
        lastError = null
        val url = "$resolver?name=${java.net.URLEncoder.encode(name, Charsets.UTF_8)}&type=TXT"
        val body = try {
            fetch(url)
        } catch (ex: Exception) {
            lastError = "doh_transport"
            return emptyList()
        }
        if (body == null) {
            lastError = "doh_unavailable"
            return emptyList()
        }
        return parseAnswer(body)
    }

    /**
     * Minimal RFC 8482 answer walk. Two real-world shapes exist: dns.google
     * returns raw RDATA (verified live: `"data":"v=spf1 -all"`), while
     * presentation-format resolvers wrap character-strings in escaped quotes
     * (`"data":"\"chunk1\" \"chunk2\""`). Quoted values are unquoted and
     * RFC 1035 chunk-joined; unquoted values pass through. Every returned value
     * is compared against `rb-verify=...` by [ChallengeService.matches] - a
     * mis-parse can only yield a non-match, never a false match.
     */
    private fun parseAnswer(body: String): List<String> =
        REGEX_ANSWER.findAll(body).mapNotNull { match ->
            // JSON-escaped quotes decode first; the record content may then be
            // quote-wrapped with chunks joined by `" "` (presentation) or nothing.
            val decoded = match.groupValues[1].replace("\\\"", "\"")
            when {
                decoded.length >= 2 && decoded.startsWith("\"") && decoded.endsWith("\"") ->
                    decoded.substring(1, decoded.length - 1)
                        .replace("\" \"", "")
                        .replace("\"\"", "")
                        .takeIf { it.isNotBlank() }
                else -> decoded.takeIf { it.isNotBlank() }
            }
        }.toList()

    private companion object {
        // Escape-aware capture: `\"` pairs stay inside the value (a `[^"]*` class would stop early).
        private val REGEX_ANSWER = Regex("\"data\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    }
}
