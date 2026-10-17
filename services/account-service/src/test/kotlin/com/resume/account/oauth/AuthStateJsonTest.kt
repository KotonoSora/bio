package com.resume.account.oauth

import com.resume.account.config.JacksonConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Auth-state JSON goes through the injected mapper (Redis round trip in
 * AuthStateStore). Without the Kotlin module the mapper cannot bind the data
 * class back and `consume` silently returns null — every SSO callback fails.
 */
class AuthStateJsonTest {

    private val mapper = JacksonConfig().objectMapper()

    @Test
    fun `auth state round-trips through the api mapper`() {
        val state = AuthState(
            provider = "generic-oidc",
            nonce = "n-1",
            codeVerifier = "v-1",
            callbackURL = "/app",
            mode = "login",
        )
        assertEquals(state, mapper.readValue(mapper.writeValueAsString(state), AuthState::class.java))
    }

    @Test
    fun `nullable link fields survive the round trip`() {
        val state = AuthState(
            provider = "generic-oidc",
            nonce = "n-2",
            codeVerifier = "v-2",
            callbackURL = "/app/account/settings",
            mode = "link",
            userId = "usr_1",
            sid = "sid_1",
            recoveryToken = "rec_1",
        )
        assertEquals(state, mapper.readValue(mapper.writeValueAsString(state), AuthState::class.java))
    }
}
