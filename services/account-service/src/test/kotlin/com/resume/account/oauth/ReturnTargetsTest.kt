package com.resume.account.oauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * BR-04 same-origin allowlist: `callbackURL`/`returnTo` only ever resolve to
 * relative targets under an allowlisted prefix; everything else falls back.
 */
class ReturnTargetsTest {

    private val allowlist = listOf("/app", "/welcome", "/resumes", "/account")
    private val fallback = "/app"

    @Test
    fun `allows on-list relative targets`() {
        assertEquals("/welcome/username", ReturnTargets.sanitize("/welcome/username", allowlist, fallback))
        assertEquals("/app", ReturnTargets.sanitize("/app", allowlist, fallback))
        assertEquals("/app?x=1", ReturnTargets.sanitize("/app?x=1", allowlist, fallback))
        assertEquals("/account/settings", ReturnTargets.sanitize("/account/settings", allowlist, fallback))
    }

    @Test
    fun `rejects off-list and lookalike prefixes`() {
        assertEquals(fallback, ReturnTargets.sanitize("/apple", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("/etc/passwd", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("/appx/../secret", allowlist, fallback))
    }

    @Test
    fun `rejects absolute, protocol-relative, and backslash targets`() {
        assertEquals(fallback, ReturnTargets.sanitize("https://evil.example/app", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("//evil.example/app", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("/app\\..\\evil", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("HTTP://evil.example", allowlist, fallback))
    }

    @Test
    fun `rejects control characters and blanks`() {
        assertEquals(fallback, ReturnTargets.sanitize("/app\n/x", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("/app\t", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("   ", allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize(null, allowlist, fallback))
        assertEquals(fallback, ReturnTargets.sanitize("", allowlist, fallback))
    }
}
