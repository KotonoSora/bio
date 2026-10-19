package com.resume.profile.domain.domains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * D-4/D-6 DNS control port: RFC 8482 TXT answers parse in both observed
 * shapes (dns.google raw RDATA and presentation-format quoted/chunked
 * strings), and every transport failure is a fail-closed empty answer - a
 * verification NEVER succeeds without a TXT match.
 */
class DohTxtResolverTest {

    private fun resolver(body: String) = DohTxtResolver("https://resolver.test/resolve") { body }

    @Test
    fun `parses the dns-google raw TXT data shape`() {
        // Verified live against dns.google (2026-10-18): no presentation quotes in `data`.
        val resolver = resolver(
            """{"Status":0,"Answer":[{"name":"_rb-challenge.blog.example.com.","type":16,"TTL":300,"data":"rb-verify=abc123"}]}""",
        )
        assertEquals(listOf("rb-verify=abc123"), resolver.txt("_rb-challenge.blog.example.com"))
    }

    @Test
    fun `unquotes presentation-format values`() {
        val resolver = resolver("""{"Status":0,"Answer":[{"type":16,"data":"\"v=spf1 -all\""}]}""")
        assertEquals(listOf("v=spf1 -all"), resolver.txt("_rb-challenge.blog.example.com"))
    }

    @Test
    fun `reassembles RFC 1035 chunked strings in both separators`() {
        val spaced = resolver("""{"Status":0,"Answer":[{"type":16,"data":"\"rb-ver\" \"ify=abc\""}]}""")
        assertEquals(listOf("rb-verify=abc"), spaced.txt("_rb-challenge.blog.example.com"))

        val adjacent = resolver("""{"Status":0,"Answer":[{"type":16,"data":"\"rb-ver\"\"ify=abc\""}]}""")
        assertEquals(listOf("rb-verify=abc"), adjacent.txt("_rb-challenge.blog.example.com"))
    }

    @Test
    fun `multiple answer records return every value`() {
        val resolver = resolver(
            """{"Status":0,"Answer":[{"type":16,"data":"v=spf1 -all"},{"type":16,"data":"\"rb-verify=zzz\""}]}""",
        )
        assertEquals(listOf("v=spf1 -all", "rb-verify=zzz"), resolver.txt("_rb-challenge.blog.example.com"))
    }

    @Test
    fun `the queried URL carries the name and TXT type`() {
        var seenUrl: String? = null
        val resolver = DohTxtResolver("https://resolver.test/resolve") { url ->
            seenUrl = url
            """{"Status":0}"""
        }
        resolver.txt("_rb-challenge.blog.example.com")
        assertTrue(seenUrl!!.contains("name=_rb-challenge.blog.example.com"))
        assertTrue(seenUrl!!.contains("type=TXT"))
    }

    @Test
    fun `transport failure fails closed with the degrade reason`() {
        val resolver = DohTxtResolver("https://resolver.test/resolve") { throw IOException("boom") }
        assertEquals(emptyList<String>(), resolver.txt("_rb-challenge.blog.example.com"))
        assertEquals("doh_transport", resolver.lastError)
    }

    @Test
    fun `null body and NXDOMAIN are empty answers`() {
        val nullBody = DohTxtResolver("https://resolver.test/resolve") { null }
        assertEquals(emptyList<String>(), nullBody.txt("_rb-challenge.blog.example.com"))
        assertEquals("doh_unavailable", nullBody.lastError)

        val nxdomain = resolver("""{"Status":3}""")
        assertEquals(emptyList<String>(), nxdomain.txt("_rb-challenge.blog.example.com"))
    }
}
