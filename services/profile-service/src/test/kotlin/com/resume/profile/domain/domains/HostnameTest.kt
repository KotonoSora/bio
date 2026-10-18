package com.resume.profile.domain.domains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * BR-02 normalization truth table (S-4): every downstream comparison sees the
 * normalized punycode form; every invalid input fails with the one stable code.
 * IDN handling is IDNA2008 (UTS #46 nontransitional via ICU), never the JDK's
 * IDNA2003.
 */
class HostnameTest {

    @Test
    fun `normalizes case, whitespace and the trailing root dot`() {
        val host = Hostname.of("  Blog.Example.COM.  ")
        assertEquals("blog.example.com", host.ascii)
        assertEquals("blog.example.com", host.display)
    }

    @Test
    fun `keeps the unicode display form and stores punycode`() {
        val host = Hostname.of("Bücher.example")
        assertEquals("xn--bcher-kva.example", host.ascii)
        assertEquals("bücher.example", host.display)
    }

    @Test
    fun `wildcards are never valid - each host verifies individually`() {
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("*.example.com") }
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("blog.*.example.com") }
    }

    @Test
    fun `requires at least two labels`() {
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("example") }
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("   ") }
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("example.") }
    }

    @Test
    fun `hyphen placement follows std3 rules`() {
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("-bad.example") }
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("bad-.example") }
        assertEquals("a-b.example.com", Hostname.of("a-b.example.com").ascii)
    }

    @Test
    fun `enforces the 63 label and 253 total bounds`() {
        val label63 = "a".repeat(63)
        assertEquals("$label63.example.com", Hostname.of("$label63.example.com").ascii)

        val label64 = "a".repeat(64)
        assertThrows(HostnameInvalidException::class.java) { Hostname.of("$label64.example.com") }

        val overTotal = (0 until 4).joinToString(".") { "a".repeat(63) } // 255 chars
        assertThrows(HostnameInvalidException::class.java) { Hostname.of(overTotal) }
    }

    @Test
    fun `challenge record name is the prefixed normalized host`() {
        assertEquals("_rb-challenge.blog.example.com", Hostname.of("Blog.Example.com.").txtName)
    }
}
