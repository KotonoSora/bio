package com.resume.account.security

import com.resume.account.config.AccountProperties
import com.resume.account.domain.ApiException
import io.jsonwebtoken.Jwts
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant

/**
 * S-4 freshness: `now - auth_time <= 600 s` from the JWT claim only. Stale or
 * absent claims refuse the action (`403 step_up_required`) and are counted.
 */
class StepUpGuardTest {

    private val props = AccountProperties()
    private val meters = SimpleMeterRegistry()
    private val guard = StepUpGuard(props, meters)

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `fresh auth_time passes`() {
        authenticate(mapOf("auth_time" to Instant.now().epochSecond, "sid" to "sid_1"))
        guard.requireFresh()
        assertEquals(0.0, meters.counter("step_up_required").count())
    }

    @Test
    fun `stale auth_time is refused and counted`() {
        authenticate(mapOf("auth_time" to Instant.now().epochSecond - props.session.stepUpSeconds - 1))
        val ex = assertThrows(ApiException::class.java) { guard.requireFresh() }
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
        assertEquals("step_up_required", ex.code)
        assertEquals("/api/auth/step-up", ex.extras["reauthPath"])
        assertEquals(1.0, meters.counter("step_up_required").count())
    }

    @Test
    fun `absent auth_time fails closed`() {
        authenticate(mapOf("sid" to "sid_1"))
        val ex = assertThrows(ApiException::class.java) { guard.requireFresh() }
        assertEquals("step_up_required", ex.code)
    }

    private fun authenticate(claims: Map<String, Any>) {
        val auth = UsernamePasswordAuthenticationToken("usr_test", null, emptyList())
        auth.details = Jwts.claims(claims)
        SecurityContextHolder.getContext().authentication = auth
    }
}
