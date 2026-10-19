package com.resume.account.domain.concierge

import com.resume.account.api.StaffResponse
import com.resume.account.api.TemplateResponse
import com.resume.account.config.ConciergeProperties
import com.resume.account.domain.ApiException
import com.resume.account.domain.AuditService
import com.resume.account.data.ConciergeStaffEntity
import com.resume.account.data.ConciergeStaffRepository
import com.resume.account.data.ConciergeTemplateEntity
import com.resume.account.data.ConciergeTemplateRepository
import com.resume.account.domain.Tokens
import com.resume.account.security.RlsContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Problem mapping for feature 05. Copy is the BA 05 message table verbatim
 * (`docs/01-requirements-ba/05-concierge-managed-service/05-exception-handling-and-errors.md`);
 * `code` strings are the frozen TL 03 catalog (additions documented inline).
 */
object ConciergeErrors {

    fun contractNotFound(): ApiException =
        ApiException.notFound("contract_not_found", "No concierge plan yet.")

    fun taskNotFound(): ApiException =
        ApiException.notFound("task_not_found", "That item is no longer on the board.")

    /** BR-01/BR-12: off-menu refusal carries the referral path. */
    fun offMenu(referralUrl: String): ApiException = ApiException(
        status = org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
        code = "task_off_menu",
        detail = "This isn't in your plan - here's what we can do instead:",
        extras = mapOf("referral" to referralUrl),
    )

    /**
     * BR-12 volume refusal. ADDITIVE code (the TL catalog lists `task_off_menu`
     * for off-menu only; over-volume is a distinct refusal with SOW-update
     * guidance) - additive-only governance, nothing renamed.
     */
    fun volumeExceeded(): ApiException = ApiException(
        status = org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
        code = "volume_exceeded",
        detail = "You've reached this item's monthly volume. We can adjust your plan.",
    )

    /** BA 05: approval/board writes on an inactive contract. */
    fun approvalsClosed(): ApiException =
        ApiException.conflict("transition_conflict", "This contract is paused or exited - approvals are closed.")

    fun transitionConflict(detail: String): ApiException =
        ApiException.conflict("transition_conflict", detail)

    fun staffingIncomplete(missing: List<String>): ApiException = ApiException(
        status = org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
        code = "staffing_incomplete",
        detail = "Staffing register incomplete - missing ${missing.joinToString(", ")}. Activation is blocked " +
            "until the register shows at least one SOW template, one operator, and one agency owner.",
    )

    fun taskForbidden(): ApiException =
        ApiException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "task_forbidden",
            "This action needs an assigned agency operator or owner.",
        )

    fun lastOwnerForbidden(): ApiException = ApiException.conflict(
        "last_owner_forbidden",
        "An agency needs at least one owner - register another owner before removing this one.",
    )

    /** S-9: expired, replayed, or forged signed-email links. */
    fun signatureInvalid(): ApiException = ApiException(
        status = org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
        code = "signature_invalid",
        detail = "This confirmation link is no longer valid - a new one has been sent.",
    )
}

/**
 * Staffing register + presence gate (BR-08, S-2). Owners are config-provisioned
 * first (`concierge.owner-user-ids`) and registered in `concierge_staff`
 * afterwards; the activation gate is a query, never runbook discipline.
 */
@Service
class ConciergeStaffing(
    private val staff: ConciergeStaffRepository,
    private val templates: ConciergeTemplateRepository,
    private val props: ConciergeProperties,
    private val limiter: ConciergeRateLimiter,
    private val audit: AuditService,
    @Value("\${internal.jwt.public-key-pem:}") private val internalKeyPem: String,
) {

    data class Presence(val templates: Long, val operators: Long, val owners: Long) {
        val complete: Boolean get() = templates > 0 && operators > 0 && owners > 0
        val missing: List<String>
            get() = buildList {
                if (templates == 0L) add("a SOW template")
                if (operators == 0L) add("an operator")
                if (owners == 0L) add("an agency owner")
            }
    }

    @Transactional(readOnly = true)
    fun presence(): Presence = RlsContext.asService {
        Presence(
            templates = templates.countActive(),
            operators = staff.countActiveByRole("operator"),
            owners = staff.countActiveByRole("owner"),
        )
    }

    fun requireComplete() {
        val presence = presence()
        if (!presence.complete) throw ConciergeErrors.staffingIncomplete(presence.missing)
    }

    fun isOwner(actor: String?): Boolean {
        if (actor.isNullOrBlank()) return false
        // Dev/E0 only: the empty-boot identity is the config-provisioned owner
        // (same precedent as the dev-user branch in the internal JWT filter).
        if (actor == "dev-user" && internalKeyPem.isBlank()) return true
        if (props.ownerUserIds.any { it.isNotBlank() && it == actor }) return true
        val row = RlsContext.asService { staff.findByUserId(actor) }
        return row?.active == true && row.role == "owner"
    }

    fun isStaff(actor: String?): Boolean {
        if (isOwner(actor)) return true
        if (actor.isNullOrBlank()) return false
        val row = RlsContext.asService { staff.findByUserId(actor) }
        return row?.active == true
    }

    fun requireOwner(actor: String?) {
        if (!isOwner(actor)) throw ConciergeErrors.taskForbidden()
    }

    fun requireStaff(actor: String?) {
        if (!isStaff(actor)) throw ConciergeErrors.taskForbidden()
    }

    @Transactional
    fun register(actor: String?, userId: String, role: String, name: String?): StaffResponse = RlsContext.asService {
        requireOwner(actor)
        limiter.ops(actor ?: "svc")
        if (userId.isBlank()) throw ApiException.validation("userId is required.")
        if (role !in setOf("operator", "owner")) throw ApiException.validation("role must be operator or owner.")
        val existing = staff.findByUserId(userId)
        if (existing != null) {
            if (!existing.active) {
                existing.active = true
                existing.removedAt = null
                existing.role = role
                existing.name = name
                staff.save(existing)
                audit.record(actor ?: "svc", "concierge.staff_registered", target = existing.id, subjectUserId = userId)
            }
            return@asService existing.toResponse()
        }
        val row = staff.save(
            ConciergeStaffEntity(id = Tokens.newId("cnc_staff"), userId = userId, role = role, name = name),
        )
        audit.record(actor ?: "svc", "concierge.staff_registered", target = row.id, subjectUserId = userId)
        row.toResponse()
    }

    @Transactional
    fun remove(actor: String?, staffId: String): Boolean = RlsContext.asService {
        requireOwner(actor)
        limiter.ops(actor ?: "svc")
        val row = staff.findById(staffId).orElse(null)
            ?: throw ApiException.notFound("not_found", "No such staff row.")
        if (row.role == "owner" && staff.countActiveByRole("owner") <= 1) {
            throw ConciergeErrors.lastOwnerForbidden()
        }
        staff.delete(row)
        audit.record(actor ?: "svc", "concierge.staff_removed", target = staffId, subjectUserId = row.userId)
        true
    }

    @Transactional(readOnly = true)
    fun staffRows(actor: String?): List<StaffResponse> = RlsContext.asService {
        requireStaff(actor)
        limiter.ops(actor ?: "svc")
        staff.listAll().map { it.toResponse() }
    }

    @Transactional
    fun createTemplate(actor: String?, name: String, menu: List<String>, version: Int): TemplateResponse =
        RlsContext.asService {
            requireOwner(actor)
            limiter.ops(actor ?: "svc")
            if (name.isBlank()) throw ApiException.validation("Template name is required.")
            val kinds = menu.map(String::trim).filter(String::isNotEmpty).distinct()
            if (kinds.isEmpty()) throw ApiException.validation("A template needs at least one menu kind.")
            val row = templates.save(
                ConciergeTemplateEntity(
                    id = Tokens.newId("cnc_tpl"),
                    name = name.take(120),
                    menu = kinds.toMutableList(),
                    version = if (version > 0) version else 1,
                ),
            )
            audit.record(actor ?: "svc", "concierge.template_created", target = row.id)
            row.toResponse()
        }

    @Transactional
    fun patchTemplate(
        actor: String?,
        templateId: String,
        name: String?,
        menu: List<String>?,
        version: Int?,
        active: Boolean?,
    ): TemplateResponse = RlsContext.asService {
        requireOwner(actor)
        limiter.ops(actor ?: "svc")
        val row = templates.findById(templateId).orElse(null)
            ?: throw ApiException.notFound("not_found", "No such template.")
        name?.takeIf { it.isNotBlank() }?.let { row.name = it.take(120) }
        // An empty list means "not provided" (a partial patch such as
        // `{"active": false}` must not wipe the menu).
        menu?.takeIf { it.isNotEmpty() }?.let {
            val kinds = it.map(String::trim).filter(String::isNotEmpty).distinct()
            if (kinds.isEmpty()) throw ApiException.validation("A template needs at least one menu kind.")
            row.menu = kinds.toMutableList()
        }
        version?.takeIf { it > 0 }?.let { row.version = it }
        active?.let { row.active = it }
        templates.save(row)
        audit.record(actor ?: "svc", "concierge.template_updated", target = row.id)
        row.toResponse()
    }

    @Transactional(readOnly = true)
    fun templateRows(actor: String?): List<TemplateResponse> = RlsContext.asService {
        requireStaff(actor)
        limiter.ops(actor ?: "svc")
        templates.listAll().map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun activeMenuKinds(): Set<String> = RlsContext.asService {
        templates.listAll().filter { it.active }.flatMap { it.menu }.toSet()
    }

    fun staffName(operatorId: String?): String? {
        if (operatorId.isNullOrBlank()) return null
        return RlsContext.asService { staff.findByUserId(operatorId) }?.name
    }
}
