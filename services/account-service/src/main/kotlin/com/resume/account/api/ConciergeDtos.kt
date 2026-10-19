package com.resume.account.api

import java.time.Instant

/**
 * Feature 05 DTOs (TL 03 "Kotlin DTOs" - names frozen). Fields marked ADDITIVE
 * are additions required by the BA surfaces (BA 04 setup-plan/handoff/work-log
 * rows, BA 05 SLA + refusal notices); nothing existing was renamed or removed.
 * `taskId` on the approval DTOs is widened to nullable so contract-level
 * setup-plan approvals can be represented (BA 03 step 2). Entity mapping lives
 * in `domain/concierge/ConciergeViews.kt` (api must not depend on `data`).
 */
data class SowItem(val kind: String, val volume: Int? = null)

data class SlaTierView(
    val tier: String,
    val responseWindowMinutes: Long?,
    val completionWindowMinutes: Long?,
    val creditRule: String,
)

data class HandoffItemView(val key: String, val at: Instant?)

data class HandoffView(
    val startedAt: Instant?,
    val scheduledWork: String?,
    val items: List<HandoffItemView>,
    val completedAt: Instant?,
)

data class ContractResponse(
    val id: String,
    val status: String,
    val slaTier: String,
    val sow: List<SowItem>,
    val pendingTransition: String? = null,
    val activatedAt: Instant? = null,
    val exitedAt: Instant? = null,
    // ADDITIVE (BA 04/BA 05): SOW version label, dual-confirm initiator, SLA
    // windows + credit path, exit handoff inventory, recorded setup-plan approval.
    val sowVersion: Int = 1,
    val pendingBy: String? = null,
    val sla: SlaTierView? = null,
    val handoff: HandoffView? = null,
    val setupApproval: ApprovalResponse? = null,
)

data class TaskResponse(
    val id: String,
    val kind: String,
    val status: String,
    val origin: String,
    val requestedAt: Instant,
    val approvedAt: Instant? = null,
    val completedAt: Instant? = null,
    val workRefs: List<String> = emptyList(),
    val refusalReason: String? = null,
    // ADDITIVE (BA 04/BA 05): request notes, referral path, operator name,
    // durable SLA miss, recorded approval history.
    val notes: String? = null,
    val referral: String? = null,
    val operatorName: String? = null,
    val slaMiss: String? = null,
    val approvals: List<ApprovalResponse> = emptyList(),
)

data class TaskRequest(val kind: String = "", val notes: String? = null)

data class ApproveTaskRequest(val scope: String = "")

/** Optional body of the client exit confirmation (BA 04: choice + typed acknowledgment). */
data class ExitDecisionRequest(val scheduledWork: String? = null, val acknowledge: String? = null)

data class ApprovalResponse(
    val id: String,
    val taskId: String? = null,
    val scope: String,
    val channel: String,
    val approvedAt: Instant,
)

data class ApprovalCheckResponse(
    val valid: Boolean,
    val taskId: String? = null,
    val scope: String? = null,
    val contractStatus: String? = null,
)

// --- internal (mesh) request/response bodies --------------------------------

data class CreateContractRequest(
    val userId: String = "",
    val slaTier: String = "",
    val sow: List<SowItem> = emptyList(),
)

data class ContractPatchRequest(
    val action: String = "",
    val slaTier: String? = null,
    val sow: List<SowItem>? = null,
    val scheduledWork: String? = null,
    val acknowledge: String? = null,
)

data class ScheduledTaskRequest(val userId: String = "", val kind: String = "", val notes: String? = null)

data class TaskStatusRequest(val status: String = "", val workRefs: List<String> = emptyList())

data class RefuseRequest(val reason: String = "", val referral: String? = null)

data class StaffRequest(val userId: String = "", val role: String = "operator", val name: String? = null)

data class StaffResponse(val id: String, val userId: String, val role: String, val name: String?, val active: Boolean)

data class TemplateRequest(
    val name: String = "",
    val menu: List<String> = emptyList(),
    val version: Int = 1,
    val active: Boolean? = null,
)

data class TemplateResponse(val id: String, val name: String, val menu: List<String>, val version: Int, val active: Boolean)

data class SignedLinkRequest(val taskId: String = "")

data class SignedLinkResponse(val token: String, val expiresAt: Instant)

data class SignedEmailRequest(val token: String = "")
