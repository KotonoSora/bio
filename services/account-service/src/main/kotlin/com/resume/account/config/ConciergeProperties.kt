package com.resume.account.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Feature 05 config surface (env-driven; no secrets in code; TL 01 D-6).
 *
 * SLA windows are the open product question Q-01 ("values from the staffing
 * cost model before first sale"): `null` means *not configured* and is reported
 * as such on the board - never an invented number. The dev/e2e wiring sets
 * zero-minute windows to make miss states observable (e2e/scripts/enable-concierge.sh).
 *
 * `ownerUserIds` bootstraps the first agency owners (config-provisioned, SECURITY
 * S-2 "staffing register plus config allowlist"); afterwards ownership lives in
 * `concierge_staff`. Envelopes mirror the normative table in
 * docs/02-architecture-techlead/05-concierge-managed-service/05-security-logging-and-rate-limiting.md.
 */
@ConfigurationProperties(prefix = "concierge")
class ConciergeProperties {
    /** Config-provisioned agency owners (logical user ids; blank entries ignored). */
    var ownerUserIds: List<String> = emptyList()

    /** SLA windows per tier, in minutes; absent tier = no clock configured. */
    var slaTiers: Map<String, SlaTier> = emptyMap()

    /** Credit-path key shown with a missed window (BA 05); a mechanism label, never an amount. */
    var creditRule: String = "service_credit"

    /** Refusal referral path (BA 05 "refusal copy + referral"); points at the SOW view. */
    var referralUrl: String = "/app/concierge"

    /** Signed-email link TTL, exactly 15 min (TL 01). */
    var linkTtlMinutes: Long = 15

    /** HMAC key for signed-email links; blank = dev-only derived key (feature 01 precedent). */
    var linkSigningKey: String = ""

    /** Client task-request envelope: 20/day/contract (NFR-07). */
    var requestsPerDay: Long = 20

    /** Client approve/pause/exit envelope: 60/min/user (NFR-07). */
    var clientActionsPerMinute: Long = 60

    /** Internal ops envelope: 120/min/operator (NFR-07). */
    var opsPerMinute: Long = 120

    class SlaTier {
        var responseMinutes: Long? = null
        var completionMinutes: Long? = null
    }
}
