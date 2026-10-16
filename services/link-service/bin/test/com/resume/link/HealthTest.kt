package com.resume.link

import com.resume.link.api.HealthController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthTest {
    @Test
    fun `liveness returns UP`() {
        val body = HealthController().liveness().body!!
        assertEquals("UP", body.status)
        assertEquals("link-service", body.service)
    }
}
