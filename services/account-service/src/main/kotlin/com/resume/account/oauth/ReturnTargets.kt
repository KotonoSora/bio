package com.resume.account.oauth

/** Same-origin relative target sanitizer (BR-04): anything off-list falls back to the default. */
object ReturnTargets {
    fun sanitize(raw: String?, allowlist: List<String>, default: String): String {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return default
        if (!value.startsWith("/") || value.startsWith("//")) return default
        if (value.contains('\\') || value.contains("://")) return default
        if (value.any { it == '\n' || it == '\r' || it == '\t' }) return default
        val allowed = allowlist.any { prefix ->
            val base = prefix.trimEnd('/')
            value == base || value.startsWith("$base/") || value.startsWith("$base?")
        }
        return if (allowed) value else default
    }
}
