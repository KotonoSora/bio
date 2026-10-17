package com.resume.account.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** BR-15 prefill safety: markup, bidi, and control characters never reach storage. */
class SanitizerTest {

    private val sanitizer = Sanitizer()

    @Test
    fun `strips markup and trims`() {
        assertEquals("Alice", sanitizer.cleanName("  <b>Alice</b>  "))
        assertEquals("A < B", sanitizer.cleanName("A < B"))
    }

    @Test
    fun `strips control and bidi characters`() {
        assertEquals("Bo", sanitizer.cleanName("B\u0007o\u202E"))
        assertEquals("Nam", sanitizer.cleanName("N\u200Ea\u2066m"))
    }

    @Test
    fun `caps at 100 characters`() {
        val long = "x".repeat(250)
        assertEquals(100, sanitizer.cleanName(long)!!.length)
    }

    @Test
    fun `blank or absent becomes null`() {
        assertNull(sanitizer.cleanName(null))
        assertNull(sanitizer.cleanName("   "))
        assertNull(sanitizer.cleanName("<i> </i>"))
    }
}
