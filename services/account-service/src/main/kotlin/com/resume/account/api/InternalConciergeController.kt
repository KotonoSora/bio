package com.resume.account.api

import com.resume.account.domain.ApiException
import com.resume.account.domain.concierge.ConciergeApprovalService
import com.resume.account.domain.concierge.ConciergeContractService
import com.resume.account.domain.concierge.ConciergeStaffing
import com.resume.account.domain.concierge.ConciergeTaskBoard
import com.resume.account.domain.concierge.ContractPatch
import com.resume.account.security.AuthClaims
import com.resume.account.security.RlsContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Mesh-only concierge API (TL 03 "Internal endpoints"; S-1/S-5: the public edge
 * never routes here). Every handler is actor-gated (agency owner / assigned
 * operator) and runs inside an explicit `svc` RLS scope: the scope must be set
 * *before* the transactional service call so the borrowed connection carries
 * `app.context` (RlsAwareDataSource) - the service JWT is the only identity
 * source (BR-08).
 *
 * ADDITIVE routes (documented, agency tooling + the drills need reads the TL
 * table implied but did not list): `GET /staff`, `GET /templates`,
 * `POST /approvals/signed-email-link`.
 */
@RestController
@RequestMapping("/internal/v1/concierge")
class InternalConciergeController(
    private val contracts: ConciergeContractService,
    private val board: ConciergeTaskBoard,
    private val approvals: ConciergeApprovalService,
    private val staffing: ConciergeStaffing,
) {

    @PostMapping("/contracts")
    fun createContract(@RequestBody body: CreateContractRequest): ResponseEntity<ContractResponse> =
        RlsContext.asService {
            val contract = contracts.create(
                actor = actor(),
                userId = body.userId.trim(),
                slaTier = body.slaTier.trim(),
                sow = body.sow,
            )
            ResponseEntity.status(HttpStatus.CREATED).body(contract)
        }

    @PatchMapping("/contracts/{id}")
    fun patchContract(@PathVariable id: String, @RequestBody body: ContractPatchRequest): ContractResponse =
        RlsContext.asService {
            contracts.patch(
                actor = actor(),
                id = id,
                req = ContractPatch(
                    action = body.action.trim(),
                    slaTier = body.slaTier,
                    sow = body.sow,
                    scheduledWork = body.scheduledWork,
                    acknowledge = body.acknowledge,
                ),
            )
        }

    @GetMapping("/contracts")
    fun listContracts(): List<ContractResponse> = RlsContext.asService {
        staffing.requireStaff(actor())
        contracts.opsList()
    }

    /** Scheduled-origin work (BR-05): the owner/operator puts an item on the board. */
    @PostMapping("/tasks")
    fun scheduleTask(@RequestBody body: ScheduledTaskRequest): ResponseEntity<TaskResponse> =
        RlsContext.asService {
            val task = board.scheduled(actor(), body.userId.trim().ifBlank { null }, body.kind.trim(), body.notes)
            ResponseEntity.status(HttpStatus.CREATED).body(task)
        }

    @PostMapping("/tasks/{id}/status")
    fun taskStatus(@PathVariable id: String, @RequestBody body: TaskStatusRequest): TaskResponse =
        RlsContext.asService {
            board.operatorStatus(actor(), id, body.status.trim(), body.workRefs)
        }

    @PostMapping("/tasks/{id}/refuse")
    fun refuseTask(@PathVariable id: String, @RequestBody body: RefuseRequest): TaskResponse =
        RlsContext.asService {
            board.refuse(actor(), id, body.reason.trim(), body.referral)
        }

    @PostMapping("/staff")
    fun registerStaff(@RequestBody body: StaffRequest): ResponseEntity<StaffResponse> = RlsContext.asService {
        val row = staffing.register(actor(), body.userId.trim(), body.role.trim(), body.name)
        ResponseEntity.status(HttpStatus.CREATED).body(row)
    }

    @DeleteMapping("/staff/{id}")
    fun removeStaff(@PathVariable id: String): ResponseEntity<Void> = RlsContext.asService {
        staffing.remove(actor(), id)
        ResponseEntity.noContent().build()
    }

    @GetMapping("/staff")
    fun listStaff(): List<StaffResponse> = RlsContext.asService {
        staffing.staffRows(actor())
    }

    @PostMapping("/templates")
    fun createTemplate(@RequestBody body: TemplateRequest): ResponseEntity<TemplateResponse> =
        RlsContext.asService {
            val row = staffing.createTemplate(actor(), body.name.trim(), body.menu, body.version)
            ResponseEntity.status(HttpStatus.CREATED).body(row)
        }

    @PatchMapping("/templates/{id}")
    fun patchTemplate(@PathVariable id: String, @RequestBody body: TemplateRequest): TemplateResponse =
        RlsContext.asService {
            staffing.patchTemplate(actor(), id, body.name, body.menu, body.version, body.active)
        }

    @GetMapping("/templates")
    fun listTemplates(): List<TemplateResponse> = RlsContext.asService {
        staffing.templateRows(actor())
    }

    /**
     * ADDITIVE (S-9): issuance for the signed-email approval link. The mesh
     * caller is gated + audited; the token is delivered by mail and returned
     * here so agency tooling can re-deliver it - never logged (Mailer).
     */
    @PostMapping("/approvals/signed-email-link")
    fun issueSignedLink(@RequestBody body: SignedLinkRequest): ResponseEntity<SignedLinkResponse> =
        RlsContext.asService {
            val (token, expiresAt) = approvals.issueSignedLink(actor(), body.taskId.trim())
            ResponseEntity.status(HttpStatus.CREATED).body(SignedLinkResponse(token, expiresAt))
        }

    /** The account-service callback half of the signed-email flow (TL 03). */
    @PostMapping("/approvals/signed-email")
    fun confirmSignedEmail(@RequestBody body: SignedEmailRequest): ApprovalResponse = RlsContext.asService {
        if (body.token.isBlank()) throw ApiException.validation("token is required.")
        approvals.confirmSignedLink(body.token.trim())
    }

    /** Dispatch gate (TL 03 normative): latest record + active contract, fail-closed consumers. */
    @GetMapping("/approvals/{id}")
    fun approvalCheck(@PathVariable id: String): ApprovalCheckResponse = RlsContext.asService {
        val check = board.approvalCheck(id)
        ApprovalCheckResponse(
            valid = check.valid,
            taskId = check.taskId,
            scope = check.scope,
            contractStatus = check.contractStatus,
        )
    }

    private fun actor(): String? = AuthClaims.subject()
}
