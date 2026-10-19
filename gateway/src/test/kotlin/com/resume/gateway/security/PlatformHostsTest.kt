package com.resume.gateway.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Feature 04 BR-02/D-4: one normalizer and one membership rule shared by the
 * host routing gate and the CSRF read exemptions - the two decision points
 * must never drift apart.
 */
class PlatformHostsTest {

    private val hosts = PlatformHosts(" localhost,127.0.0.1,::1,edge.example.com ")

    @Test
    fun `matches configured hosts irrespective of port, case and root dot`() {
        assertTrue(hosts.isPlatform(PlatformHosts.normalize("LOCALHOST")))
        assertTrue(hosts.isPlatform(PlatformHosts.normalize("localhost:8080")))
        assertTrue(hosts.isPlatform(PlatformHosts.normalize("edge.example.com.")))
        assertTrue(hosts.isPlatform(PlatformHosts.normalize("[::1]:443")))
        assertFalse(hosts.isPlatform(PlatformHosts.normalize("blog.example.com")))
        assertFalse(hosts.isPlatform(PlatformHosts.normalize("unknown-creator.e2e.test")))
    }

    @Test
    fun `a missing Host counts as platform - legacy routing and cookie issuance apply`() {
        assertTrue(hosts.isPlatform(PlatformHosts.normalize(null)))
        assertTrue(hosts.isPlatform(PlatformHosts.normalize("")))
    }

    @Test
    fun `normalization strips ports, brackets and trailing dots`() {
        assertEquals("example.com", PlatformHosts.normalize(" Example.COM.:443 "))
        assertEquals("::1", PlatformHosts.normalize("[::1]:8080"))
        assertEquals("unknown-creator.e2e.test", PlatformHosts.normalize("Unknown-Creator.E2E.test"))
        assertEquals("", PlatformHosts.normalize(null))
    }
}
