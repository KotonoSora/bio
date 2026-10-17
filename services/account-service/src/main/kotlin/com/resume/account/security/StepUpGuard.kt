package com.resume.account.security

import com.resume.account.config.AccountProperties
import com.resume.account.domain.ApiException
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Step-up freshness (BR-07, S-4): enforced from the JWT `auth_time` claim only -
 * `now - auth_time <= 600 s`. A stale or absent claim is `403 step_up_required`
 * with the re-auth path; the action never executes, not even partially.
 * The claim originates from the edge session key, refreshed exclusively by an
 * SSO re-dance.
 */
@Component
class StepUpGuard(props: AccountProperties, meters: MeterRegistry) {
    private val windowSeconds = props.session.stepUpSeconds
    private val stale = meters.counter("step_up_required")

    fun requireFresh() {
        val authTime = AuthClaims.authTimeEpochSeconds()
        if (authTime == null || Instant.now().epochSecond - authTime > windowSeconds) {
            stale.increment()
            throw ApiException.stepUpRequired()
        }
    }
}
