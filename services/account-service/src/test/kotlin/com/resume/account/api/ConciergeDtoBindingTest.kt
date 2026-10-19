package com.resume.account.api

import com.resume.account.config.JacksonConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/**
 * Mesh request bodies are partial by contract (TL 03): the template drain sends
 * `{ "active": false }`, scheduled work may omit `userId`, pause/exit may send
 * `{}`. Spring MVC binds through the auto-configured Jackson 3 `JsonMapper`, so
 * the 3.x Kotlin module must be registered on it - a strict all-fields binding
 * would 422 the callers' valid calls.
 */
class ConciergeDtoBindingTest {
    private val mapper: JsonMapper = JsonMapper.builder()
        .apply { JacksonConfig().jsonMapperCustomizer().customize(this) }
        .build()

    @Test
    fun `partial template patch binds through the Kotlin defaults`() {
        val request = mapper.readValue("{\"active\":false}", TemplateRequest::class.java)
        assertEquals(false, request.active)
        assertEquals("", request.name)
        assertEquals(emptyList<String>(), request.menu)
        assertEquals(1, request.version)
    }

    @Test
    fun `scheduled-task body may omit the client`() {
        val request = mapper.readValue("{\"kind\":\"report\",\"notes\":\"Monthly review\"}", ScheduledTaskRequest::class.java)
        assertEquals("", request.userId)
        assertEquals("report", request.kind)
        assertEquals("Monthly review", request.notes)
    }

    @Test
    fun `refusal body may omit the referral`() {
        val request = mapper.readValue("{\"reason\":\"off_menu\"}", RefuseRequest::class.java)
        assertEquals("off_menu", request.reason)
        assertEquals(null, request.referral)
    }

    @Test
    fun `client task request may omit the notes`() {
        val request = mapper.readValue("{\"kind\":\"campaign\"}", TaskRequest::class.java)
        assertEquals("campaign", request.kind)
        assertEquals(null, request.notes)
    }
}
