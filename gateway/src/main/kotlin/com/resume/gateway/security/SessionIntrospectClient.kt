package com.resume.gateway.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant

/**
 * Edge rebuild path (feature 01 D-2): registry truth from account-service when
 * the `edge:session:{sid}` key is missing or db 2 is unreachable. Gateway-signed
 * service JWT (`sub=edge:gateway`, aud=account-service). `NotFound` (dead,
 * revoked, expired - or forged) and `Unavailable` (degraded mesh) are distinct
 * so the filter never confuses "no session" with "cannot tell".
 */
@Service
class SessionIntrospectClient(
    @Value("\${edge.account-service-url:http://account-service:8080}") baseUrl: String,
    private val jwt: InternalJwtService,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val base = baseUrl.trimEnd('/')

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofSeconds(2)) },
        )
        .build()

    sealed interface Result {
        data class Found(
            val userId: String,
            val authTimeEpochSeconds: Long,
            val absoluteExpiresEpochSeconds: Long,
        ) : Result

        data object NotFound : Result
        data object Unavailable : Result
    }

    fun introspect(sid: String): Result = try {
        client.get()
            .uri("$base/internal/v1/sessions/{sid}/introspect", sid)
            .headers { it.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issue(userId = "edge:gateway", audience = "account-service")) }
            .exchange { _, response ->
                when (response.statusCode.value()) {
                    200 -> {
                        val body = mapper.readValue(response.body.readBytes(), IntrospectJson::class.java)
                        Result.Found(body.userId, body.authTime.epochSecond, body.absoluteExpiresAt.epochSecond)
                    }
                    404 -> Result.NotFound
                    else -> Result.Unavailable
                }
            }
    } catch (ex: Exception) {
        log.warn("edge introspect degraded: {}", ex.message)
        Result.Unavailable
    }

    private data class IntrospectJson(val userId: String, val authTime: Instant, val absoluteExpiresAt: Instant)
}
