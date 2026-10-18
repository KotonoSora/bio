package com.resume.account.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import java.time.Instant

/** DTOs are the source of truth for the SPA type mirror (TL 03); names are frozen. */
data class SocialSignInRequest(val provider: String = "", val callbackURL: String = "")

data class StepUpRequest(val returnTo: String = "/app")

data class MeResponse(
    val id: String,
    val name: String?,
    val email: String?,
    val emailVerified: Boolean,
    val emailConflict: Boolean,
    val contactEmail: String?,
    val contactVerified: Boolean,
    val imageKey: String?,
    val createdAt: Instant,
)

data class SessionSummary(
    val sid: String,
    val current: Boolean,
    val deviceLabel: String?,
    val ipClass: String?,
    val createdAt: Instant,
    val lastActiveAt: Instant,
)

data class SessionListResponse(val sessions: List<SessionSummary>)

data class ContactEmailRequest(@field:Email val email: String)

data class ContactEmailVerifyRequest(@field:Pattern(regexp = "\\d{6}") val code: String)

data class SessionIntrospectResponse(
    val userId: String,
    val authTime: Instant,
    val absoluteExpiresAt: Instant,
)

data class EntitlementSnapshotResponse(
    val userId: String,
    val plan: String, // "free" | "pro"
    val capabilities: Map<String, String>,
    val resolvedAt: Instant,
)

/**
 * Feature 03 account plan read (TL 03): tier from the feature 01 snapshot plus
 * optional subscription rows composed from feature 36's projection. Absent
 * fields stay absent - nothing is invented (BR-03, BR-05).
 */
data class PlanResponse(
    val plan: String, // free | pro (feature 01 snapshot)
    val subscription: SubscriptionProjection? = null, // null when effective free or projection absent pre-36
    val trial: TrialState? = null, // derived from 36's read when status = trial
)

data class SubscriptionProjection(
    val status: String, // active | grace | cancelled | trial (feature 36)
    val term: String, // monthly | yearly
    val periodEnd: Instant,
    val periodStart: Instant, // additive (G4)
    val nextAmountVnd: Long, // the buyer's own next amount, never a headline (BR-05)
    val graceEndsAt: Instant? = null, // present only in grace
)

data class TrialState(val days: Int, val endsAt: Instant)

data class RecoveryLinkRequest(val userId: String = "", val evidenceRef: String = "")

data class RecoveryLinkResponse(val token: String, val expiresAt: Instant)

data class ProviderView(val id: String, val label: String)

data class ProviderListResponse(val providers: List<ProviderView>)

/** Owner view of the provider set with link state (settings providers list). */
data class MyProviderView(val id: String, val label: String, val linked: Boolean)

data class MyProviderListResponse(val providers: List<MyProviderView>)

data class EmailView(val email: String?, val emailVerified: Boolean)

// --- Feature 02: account deletion (TL 03 "Kotlin DTOs" - names are frozen) ---

/** Typed confirmation: the profile username exactly, else the literal `DELETE`. */
data class DeleteRequest(val confirmation: String = "")

data class DeleteStatusResponse(
    val status: String, // none | grace | cancelled
    val requestedAt: Instant?,
    val purgeDueAt: Instant?,
    val certificateRef: String?,
)

data class AccountStatusResponse(val status: String, // active | deactivated | purged
    val purgeDueAt: Instant?)

data class PurgeRequest(val userId: String = "", val runId: String = "")

data class PurgeResponse(val runId: String, val tables: Map<String, Long>, val files: Long)

data class PurgeProbeResponse(val userId: String, val residualRows: Map<String, Long>, val residualFiles: Long)

data class DeletionStatusResponse(
    val userId: String,
    val status: String,
    val requestedAt: Instant?,
    val purgeDueAt: Instant?,
    val purgedAt: Instant?,
    val certificateRef: String?,
)
