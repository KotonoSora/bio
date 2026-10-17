package com.resume.publicsite

import com.resume.publicsite.api.HealthController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthTest {
    @Test
    fun `liveness returns UP`() {
        val body = HealthController().liveness().body!!
        assertEquals("UP", body.status)
        assertEquals("public-site-service", body.service)
    }
}
