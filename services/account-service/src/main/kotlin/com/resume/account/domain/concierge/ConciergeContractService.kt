package com.resume.account.domain.concierge

import com.resume.account.api.ContractResponse
import com.resume.account.api.SowItem
import com.resume.account.config.ConciergeProperties
import com.resume.account.data.ConciergeApprovalRepository
import com.resume.account.data.ConciergeContractEntity
import com.resume.account.data.ConciergeContractRepository
import com.resume.account.data.ConciergeTaskEntity
import com.resume.account.data.ConciergeTaskRepository
import com.resume.account.data.HandoffItem
import com.resume.account.data.HandoffState
import com.resume.account.data.SowEntry
import com.resume.account.domain.ApiException
import com.resume.account.domain.AuditService
import com.resume.account.domain.Tokens
import com.resume.account.events.DomainEvent
import com.resume.account.events.EventPublisher
import com.resume.account.security.RlsContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Contract lifecycle (TL 01 D-1/D-2): draft -> active -> paused -> active ->
 * exited, with dual-confirmed pause/exit (BR-10) and the staffing + setup-plan
 * activation gates (BR-03, BR-08). Client rows are owner-scoped by RLS plus an
 * explicit `user_id` predicate; agency actions run under the `svc` scope with
 * an actor gate (BR-02, S-2).
 */
@Service
class ConciergeContractService(
    private val contracts: ConciergeContractRepository,
    private val tasks: ConciergeTaskRepository,
    private val approvals: ConciergeApprovalRepository,
    private val staffing: ConciergeStaffing,
    private val props: ConciergeProperties,
    private val audit: AuditService,
    private val events: EventPublisher,
) {

    data class SlaView(
        val tier: String,
        val responseWindowMinutes: Long?,
        val completionWindowMinutes: Long?,
        val creditRule: String,
    )

    @Transactional
    fun create(actor: String?, userId: String, slaTier: String, sow: List<SowItem>): ContractResponse =
        RlsContext.asService {
            staffing.requireOwner(actor)
            if (userId.isBlank()) throw ApiException.validation("userId is required.")
            if (slaTier.isBlank()) throw ApiException.validation("slaTier is required.")
            val items = normalizeSow(sow)
            if (contracts.findLive(userId) != null) {
                throw ConciergeErrors.transitionConflict("This client already has a live contract.")
            }
            val contract = contracts.save(
                ConciergeContractEntity(
                    id = Tokens.newId("cnc"),
                    userId = userId,
                    status = "draft",
                    slaTier = slaTier.take(32),
                    sow = items.toMutableList(),
                    sowVersion = 1,
                ),
            )
            audit.record(actor ?: "svc", "concierge.contract_created", target = contract.id, subjectUserId = userId)
            contract.toResponse(sla(contract))
        }

    @Transactional
    fun patch(actor: String?, id: String, req: ContractPatch): ContractResponse = RlsContext.asService {
        staffing.requireOwner(actor)
        val contract = contracts.findById(id).orElse(null)
            ?: throw ConciergeErrors.contractNotFound()
        when (req.action) {
            "update_sow" -> {
                // DEVIATION FLAGGED (owner decision 2026-10-17): BR-12 requires a
                // recorded SOW update before execution, so an *active* contract's
                // SOW can be updated (versioned); the TL restricted PATCH's SOW
                // wording to drafts. Tier updates stay draft-only.
                if (contract.status == "exited") {
                    throw ConciergeErrors.transitionConflict("An exited contract can't be edited.")
                }
                contract.sow = normalizeSow(req.sow.orEmpty()).toMutableList()
                contract.sowVersion += 1
                if (contract.status == "draft") {
                    req.slaTier?.takeIf { it.isNotBlank() }?.let { contract.slaTier = it.take(32) }
                }
            }

            "activate" -> activate(actor, contract)
            "request_pause" -> requestAgency(actor, contract, "pause")
            "request_exit" -> requestAgency(actor, contract, "exit")
            "confirm_pause" -> confirmPause(actor, contract)
            "resume" -> resume(actor, contract)
            "confirm_exit" -> confirmExit(actor, contract, req)
            "complete_exit" -> completeExit(actor, contract)
            "cancel_transition" -> cancel(actor, contract)

            else -> throw ApiException.validation("Unsupported contract action.")
        }
        contract.updatedAt = Instant.now()
        contracts.save(contract)
        contract.toResponse(sla(contract))
    }

    private fun activate(actor: String?, contract: ConciergeContractEntity) {
        if (contract.status != "draft") {
            throw ConciergeErrors.transitionConflict("Only a draft contract can be activated.")
        }
        staffing.requireComplete()
        approvals.listSetup(contract.id).firstOrNull()
            ?: throw ConciergeErrors.transitionConflict(
                "The setup plan needs the client's approval before build.",
            )
        contract.status = "active"
        contract.activatedAt = Instant.now()
        contract.pendingTransition = null
        contract.pendingBy = null
        if (contract.sow.any { it.kind == "setup" } && tasks.listByContract(contract.id).none { it.kind == "setup" }) {
            // BA 03 step 4: setup work is seeded on the board at activation.
            tasks.save(
                ConciergeTaskEntity(
                    id = Tokens.newId("cnc_task"),
                    contractId = contract.id,
                    userId = contract.userId,
                    kind = "setup",
                    status = "requested",
                    origin = "scheduled",
                ),
            )
        }
        audit.record(actor ?: "svc", "concierge.contract_activated", target = contract.id, subjectUserId = contract.userId)
        publishState(contract, "active")
    }

    private fun requestAgency(actor: String?, contract: ConciergeContractEntity, kind: String) {
        if (contract.pendingTransition != null) {
            throw ConciergeErrors.transitionConflict("A change is already waiting for confirmation.")
        }
        if (contract.status != "active" && !(kind == "exit" && contract.status == "paused")) {
            throw ConciergeErrors.transitionConflict("This contract is already ${contract.status}.")
        }
        contract.pendingTransition = kind
        contract.pendingBy = "agency"
        audit.record(actor ?: "svc", "concierge.${kind}_requested", target = contract.id, subjectUserId = contract.userId)
    }

    private fun confirmPause(actor: String?, contract: ConciergeContractEntity) {
        if (contract.pendingTransition != "pause") {
            throw ConciergeErrors.transitionConflict("There's no pause waiting for confirmation.")
        }
        if (contract.status != "active") {
            throw ConciergeErrors.transitionConflict("This contract is already ${contract.status}.")
        }
        contract.status = "paused"
        contract.pausedAt = Instant.now()
        contract.pendingTransition = null
        contract.pendingBy = null
        audit.record(actor ?: "svc", "concierge.pause_confirmed", target = contract.id, subjectUserId = contract.userId)
        publishState(contract, "paused")
    }

    private fun resume(actor: String?, contract: ConciergeContractEntity) {
        if (contract.status != "paused") {
            throw ConciergeErrors.transitionConflict("This contract is already ${contract.status}.")
        }
        contract.status = "active"
        contract.pendingTransition = null
        contract.pendingBy = null
        audit.record(actor ?: "svc", "concierge.resumed", target = contract.id, subjectUserId = contract.userId)
        publishState(contract, "active")
    }

    private fun confirmExit(actor: String?, contract: ConciergeContractEntity, req: ContractPatch) {
        if (contract.pendingTransition != "exit") {
            throw ConciergeErrors.transitionConflict("There's no exit waiting for confirmation.")
        }
        val scheduledWork = req.scheduledWork
        if (scheduledWork != "transfer" && scheduledWork != "cancel") {
            // BA 05 verbatim: the decision is required before closing out.
            throw ApiException.validation("Choose what happens to your scheduled work before we close out.")
        }
        if (req.acknowledge != "HANDOFF") {
            throw ApiException.validation("Type HANDOFF to confirm the handoff inventory.")
        }
        val started = Instant.now()
        contract.handoff = HandoffState(
            startedAt = started,
            scheduledWork = scheduledWork,
            items = listOf(
                HandoffItem("credentials_inventory", started),
                HandoffItem("ownership_confirmation", started),
                HandoffItem("scheduled_work_decision", started),
                HandoffItem("final_report", null),
            ),
        )
        audit.record(actor ?: "svc", "concierge.exit_confirmed", target = contract.id, subjectUserId = contract.userId)
        // Work halts here: the approval check and operator routes refuse while
        // `pending_transition = exit` (BR-10 "agency links sever on exit").
    }

    private fun completeExit(actor: String?, contract: ConciergeContractEntity) {
        if (contract.pendingTransition != "exit" || contract.handoff == null) {
            throw ConciergeErrors.transitionConflict("The handoff inventory hasn't started.")
        }
        val done = Instant.now()
        val handoff = contract.handoff!!
        contract.status = "exited"
        contract.exitedAt = done
        contract.pendingTransition = null
        contract.pendingBy = null
        contract.handoff = handoff.copy(
            items = handoff.items.map { if (it.key == "final_report") it.copy(at = done) else it },
            completedAt = done,
        )
        if (handoff.scheduledWork == "cancel") {
            // Queued concierge-origin work is canceled at the client's choice; the
            // client's own artifacts and history stay intact (BR-10, BR-11).
            tasks.listQueued(contract.id).forEach { queued ->
                queued.status = "cancelled"
                queued.refusalReason = "client_cancel"
                queued.updatedAt = done
                tasks.save(queued)
            }
        }
        audit.record(actor ?: "svc", "concierge.exited", target = contract.id, subjectUserId = contract.userId)
        publishState(contract, "exited")
    }

    private fun cancel(actor: String?, contract: ConciergeContractEntity) {
        if (contract.pendingTransition == null) {
            throw ApiException.notFound("not_found", "There's nothing to cancel.")
        }
        audit.record(actor ?: "svc", "concierge.${contract.pendingTransition}_cancelled", target = contract.id)
        contract.pendingTransition = null
        contract.pendingBy = null
    }

    /** Client-requested transition: creates the pending state, or confirms an agency request. */
    @Transactional
    fun clientTransition(
        userId: String,
        kind: String,
        scheduledWork: String?,
        acknowledge: String?,
    ): TransitionOutcome {
        val contract = contracts.findLive(userId) ?: throw ConciergeErrors.contractNotFound()
        if (contract.pendingTransition == null) {
            if (contract.status != "active") {
                throw ConciergeErrors.transitionConflict("This contract is already ${contract.status}.")
            }
            contract.pendingTransition = kind
            contract.pendingBy = "client"
            contract.updatedAt = Instant.now()
            contracts.save(contract)
            audit.record(userId, "concierge.${kind}_requested", target = contract.id, subjectUserId = contract.userId)
            return TransitionOutcome(view(contract), committed = false)
        }
        if (contract.pendingTransition != kind) {
            throw ConciergeErrors.transitionConflict("A different change is already waiting for confirmation.")
        }
        if (contract.pendingBy != "agency") {
            // Already pending on the client's own request: idempotent pending read.
            return TransitionOutcome(view(contract), committed = false)
        }
        // The client is the counterpart of an agency request (BR-10).
        if (kind == "pause") {
            confirmPause(userId, contract)
        } else {
            confirmExit(userId, contract, ContractPatch(scheduledWork = scheduledWork, acknowledge = acknowledge))
        }
        contract.updatedAt = Instant.now()
        contracts.save(contract)
        return TransitionOutcome(view(contract), committed = true)
    }

    /** Initiator cancels a pending transition before the counterpart confirms (BR-10). */
    @Transactional
    fun clientCancel(userId: String): Boolean {
        val contract = contracts.findLive(userId) ?: throw ConciergeErrors.contractNotFound()
        if (contract.pendingTransition == null) {
            throw ApiException.notFound("not_found", "There's nothing to cancel.")
        }
        if (contract.pendingBy != "client") {
            throw ConciergeErrors.transitionConflict("The agency asked for this change - confirm it or contact them.")
        }
        cancel(userId, contract)
        contract.updatedAt = Instant.now()
        contracts.save(contract)
        return true
    }

    /** Client read surface: the current engagement in any state (BR-10/BR-11 keep history post-exit). */
    @Transactional(readOnly = true)
    fun clientView(userId: String): ConciergeContractEntity? = contracts.findFirstByUserIdOrderByCreatedAtDesc(userId)

    @Transactional(readOnly = true)
    fun opsList(): List<ContractResponse> = RlsContext.asService { contracts.listAll().map { it.toResponse(sla(it)) } }

    /** Full client-facing view: SLA windows + the recorded setup-plan approval. */
    private fun view(contract: ConciergeContractEntity): ContractResponse =
        contract.toResponse(sla(contract), approvals.listSetup(contract.id).firstOrNull())

    @Transactional(readOnly = true)
    fun byId(contractId: String): ConciergeContractEntity =
        contracts.findById(contractId).orElse(null) ?: throw ConciergeErrors.contractNotFound()

    fun sla(contract: ConciergeContractEntity): SlaView {
        val tier = props.slaTiers[contract.slaTier]
        return SlaView(
            tier = contract.slaTier,
            responseWindowMinutes = tier?.responseMinutes,
            completionWindowMinutes = tier?.completionMinutes,
            creditRule = props.creditRule,
        )
    }

    private fun publishState(contract: ConciergeContractEntity, state: String) {
        events.publishAfterCommit(
            DomainEvent(
                type = ContractStateChangedV1,
                aggregateId = contract.id,
                userId = contract.userId,
                payload = mapOf("state" to state, "slaTier" to contract.slaTier),
            ),
        )
    }

    private fun normalizeSow(sow: List<SowItem>): List<SowEntry> {
        if (sow.isEmpty()) throw ApiException.validation("Pick at least one menu item for the SOW.")
        sow.forEach {
            if (it.kind.isBlank()) throw ApiException.validation("SOW items need a kind.")
            if (it.kind.length > 40) throw ApiException.validation("SOW kinds are at most 40 characters.")
            if (it.volume != null && it.volume < 0) throw ApiException.validation("Volumes can't be negative.")
        }
        return sow.mapIndexed { index, entry -> entry to index }
            .sortedBy { it.second }
            .map { SowEntry(it.first.kind, it.first.volume) }
            .distinctBy { it.kind }
    }

    companion object {
        /** TL 03 events: invoicing (retainer start/stop) + schedulers (halt on pause/exit). */
        const val ContractStateChangedV1 = "concierge.contract_state_changed.v1"
    }
}
