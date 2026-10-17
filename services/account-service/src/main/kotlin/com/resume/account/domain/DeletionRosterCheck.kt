package com.resume.account.domain

import com.resume.account.config.AccountProperties
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Roster discipline (TL 01): every configured participant must answer its
 * `/internal/v1/account/purge-probe` route at boot. Strict posture
 * (`account.deletion.strict-participants=true`) fails startup so a participant
 * that was registered but never migrated is caught in dev; the default posture
 * warns + counts - a downed participant must never take down the API, and the
 * degrade matrix already keeps purge retryable.
 */
@Component
class DeletionRosterCheck(
    private val props: AccountProperties,
    private val participants: PurgeParticipantClient,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val unreachable = meters.counter("deletion.roster.unreachable")

    @EventListener(ApplicationReadyEvent::class)
    fun check() {
        val roster = props.deletion.participants.filter { it.name.isNotBlank() && it.baseUrl.isNotBlank() }
        if (roster.isEmpty()) return
        val missing = roster.filterNot { participants.reachable(it) }
        if (missing.isEmpty()) {
            log.info("purge roster verified: {}", roster.joinToString { it.name })
            return
        }
        unreachable.increment()
        val names = missing.joinToString { it.name }
        if (props.deletion.strictParticipants) {
            throw IllegalStateException("purge roster unreachable: $names")
        }
        log.warn("purge roster unreachable (purge stays retryable): {}", names)
    }
}
