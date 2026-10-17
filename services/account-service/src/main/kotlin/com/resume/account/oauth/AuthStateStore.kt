package com.resume.account.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.account.config.AccountProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/** Single-use OAuth flow state (D-4): `acct:authstate:{state}`, 10 min TTL, GETDEL. */
data class AuthState(
    val provider: String,
    val nonce: String,
    val codeVerifier: String,
    val callbackURL: String,
    /** login | step_up | link | recovery */
    val mode: String,
    val userId: String? = null,
    val sid: String? = null,
    val recoveryToken: String? = null,
)

@Component
class AuthStateStore(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    private val props: AccountProperties,
) {
    fun put(state: String, value: AuthState) {
        redis.opsForValue().set(
            "acct:authstate:$state",
            mapper.writeValueAsString(value),
            Duration.ofSeconds(props.auth.stateTtlSeconds),
        )
    }

    /** Single-use: the state is gone the moment it is read (replay fails closed). */
    fun consume(state: String): AuthState? {
        val raw = redis.opsForValue().getAndDelete("acct:authstate:$state") ?: return null
        return runCatching { mapper.readValue(raw, AuthState::class.java) }.getOrNull()
    }
}
