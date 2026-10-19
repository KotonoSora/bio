package com.resume.account.domain.concierge

import com.resume.account.api.ApprovalResponse
import com.resume.account.api.ContractResponse
import com.resume.account.api.HandoffItemView
import com.resume.account.api.HandoffView
import com.resume.account.api.SlaTierView
import com.resume.account.api.SowItem
import com.resume.account.api.StaffResponse
import com.resume.account.api.TaskResponse
import com.resume.account.api.TemplateResponse
import com.resume.account.data.ConciergeApprovalEntity
import com.resume.account.data.ConciergeContractEntity
import com.resume.account.data.ConciergeStaffEntity
import com.resume.account.data.ConciergeTaskEntity
import com.resume.account.data.ConciergeTemplateEntity
import com.resume.account.data.HandoffState
import com.resume.account.data.SowEntry
import org.springframework.stereotype.Component

/**
 * Entity -> response mapping. It lives in `domain` because the api layer must
 * not depend on `data` (ArchitectureTest): services return these views and the
 * controllers only serialize them.
 */
fun SowEntry.toView(): SowItem = SowItem(kind, volume)

fun ConciergeContractEntity.toResponse(
    sla: ConciergeContractService.SlaView?,
    setupApproval: ConciergeApprovalEntity? = null,
): ContractResponse = ContractResponse(
    id = id,
    status = status,
    slaTier = slaTier,
    sow = sow.map(SowEntry::toView),
    pendingTransition = pendingTransition,
    activatedAt = activatedAt,
    exitedAt = exitedAt,
    sowVersion = sowVersion,
    pendingBy = pendingBy,
    sla = sla?.let {
        SlaTierView(it.tier, it.responseWindowMinutes, it.completionWindowMinutes, it.creditRule)
    },
    handoff = handoff?.toView(),
    setupApproval = setupApproval?.toResponse(),
)

fun HandoffState.toView(): HandoffView = HandoffView(
    startedAt = startedAt,
    scheduledWork = scheduledWork,
    items = items.map { HandoffItemView(it.key, it.at) },
    completedAt = completedAt,
)

fun ConciergeTaskEntity.toResponse(
    slaMiss: String? = null,
    operatorName: String? = null,
    approvals: List<ConciergeApprovalEntity> = emptyList(),
): TaskResponse = TaskResponse(
    id = id,
    kind = kind,
    status = status,
    origin = origin,
    requestedAt = requestedAt,
    approvedAt = approvedAt,
    completedAt = completedAt,
    workRefs = workRefs.toList(),
    refusalReason = refusalReason,
    notes = notes,
    referral = referral,
    operatorName = operatorName,
    slaMiss = slaMiss,
    approvals = approvals.map(ConciergeApprovalEntity::toResponse),
)

fun ConciergeApprovalEntity.toResponse(): ApprovalResponse =
    ApprovalResponse(id = id, taskId = taskId, scope = scope, channel = channel, approvedAt = approvedAt)

fun ConciergeStaffEntity.toResponse(): StaffResponse =
    StaffResponse(id = id, userId = userId, role = role, name = name, active = active)

fun ConciergeTemplateEntity.toResponse(): TemplateResponse =
    TemplateResponse(id = id, name = name, menu = menu.toList(), version = version, active = active)

/**
 * Client read surfaces (BA 04). The board read runs in its own owner-scoped
 * transaction, then the operator-name lookup composes *outside* it so it still
 * borrows an `svc`-scope connection (`RlsAwareDataSource` sets the scope at
 * borrow time; a lookup inside the client transaction would see no staff row).
 */
@Component
class ConciergeViews(
    private val contracts: ConciergeContractService,
    private val board: ConciergeTaskBoard,
    private val staffing: ConciergeStaffing,
    private val approvals: ConciergeApprovalService,
) {

    /** The current engagement in any state - exited history stays served (BR-10/BR-11). */
    fun clientContract(userId: String): ContractResponse? =
        contracts.clientView(userId)?.let { it.toResponse(contracts.sla(it), approvals.setupApprovalFor(it.id)) }

    /** Work log (BA 04): freshest board state plus operator name and approval history. */
    fun clientTasks(userId: String): List<TaskResponse> =
        board.listForClient(userId).map { row ->
            row.toResponse(
                slaMiss = board.slaMiss(row),
                operatorName = staffing.staffName(row.operatorId),
                approvals = board.approvalsFor(row.id),
            )
        }
}
