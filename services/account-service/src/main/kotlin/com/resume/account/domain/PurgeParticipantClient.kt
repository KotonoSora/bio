package com.resume.account.domain

import com.resume.account.api.PurgeProbeResponse
import com.resume.account.api.PurgeRequest
import com.resume.account.api.PurgeResponse
import com.resume.account.config.AccountProperties
import com.resume.account.security.OutboundServiceJwt
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/** One participant's purge result: verbatim table counts + file count. */
data class ParticipantOutcome(val name: String, val tables: Map<String, Long>, val files: Long)

/** Bounded-retry exhaustion (or `409 purge_in_progress`): the account stays `grace`. */
class ParticipantFailure(val participant: String, cause: Throwable) :
    RuntimeException("purge participant $participant failed: ${cause.message}", cause)

/**
 * Purge fan-out client (TL 01 D-1): `POST /internal/v1/account/purge` on every
 * registered participant, each call carrying the run `runId` (participants are
 * idempotent per run). Retries are bounded per D-4; a participant that keeps
 * failing (including `409 purge_in_progress`) fails this run for the account -
 * the row stays `grace` and the next daily pass retries, never a partial
 * "purged". Mesh credential: service JWT audience = participant service name.
 */
@Service
class PurgeParticipantClient(
    private val props: AccountProperties,
    private val outbound: OutboundServiceJwt,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val failures = meters.counter("deletion.participant.failure")
    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(props.services.timeoutMs.coerceAtLeast(500))) },
        )
        .build()

    fun purge(participant: AccountProperties.Participant, userId: String, runId: String): ParticipantOutcome {
        val attempts = props.deletion.participantRetries.coerceAtLeast(1)
        var last: Exception? = null
        for (attempt in 1..attempts) {
            try {
                val spec = client.post().uri("${participant.baseUrl}/internal/v1/account/purge")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                outbound.authorization(participant.name)?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
                val body = spec.body(PurgeRequest(userId = userId, runId = runId))
                    .retrieve()
                    .body(PurgeResponse::class.java)
                    ?: throw IllegalStateException("empty purge response")
                return ParticipantOutcome(participant.name, body.tables, body.files)
            } catch (ex: Exception) {
                last = ex
                log.warn("purge participant {} attempt {}/{} failed: {}", participant.name, attempt, attempts, ex.message)
                if (attempt < attempts) {
                    try {
                        Thread.sleep(200L * attempt)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
        failures.increment()
        throw ParticipantFailure(participant.name, last ?: IllegalStateException("participant unreachable"))
    }

    /** Weekly residual probe read; callers count failures as degraded, never as residual. */
    fun probe(participant: AccountProperties.Participant, userId: String): PurgeProbeResponse {
        val spec = client.get().uri("${participant.baseUrl}/internal/v1/account/purge-probe/{userId}", userId)
        outbound.authorization(participant.name)?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
        return spec.retrieve().body(PurgeProbeResponse::class.java)
            ?: throw IllegalStateException("empty probe response")
    }

    /** Roster discipline (TL 01): a registered participant must answer its probe route. */
    fun reachable(participant: AccountProperties.Participant): Boolean = try {
        probe(participant, ROSTER_USER_ID)
        true
    } catch (ex: Exception) {
        log.warn("purge participant {} unreachable at boot: {}", participant.name, ex.message)
        false
    }

    companion object {
        /** Never exists; the probe route answers with zero residuals when the handler is live. */
        const val ROSTER_USER_ID = "_roster_probe"
    }
}
