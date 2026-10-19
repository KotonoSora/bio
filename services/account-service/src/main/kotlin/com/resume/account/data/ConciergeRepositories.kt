package com.resume.account.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * Feature 05 repositories. Every query runs under the caller's RLS scope
 * (owner predicate for dashboard routes, `svc` for internal handlers); count
 * queries are used for envelope and staffing checks (never layer-1 data).
 */
interface ConciergeTemplateRepository : JpaRepository<ConciergeTemplateEntity, String> {
    @Query("select t from ConciergeTemplateEntity t order by t.createdAt asc")
    fun listAll(): List<ConciergeTemplateEntity>

    @Query("select count(t) from ConciergeTemplateEntity t where t.active = true")
    fun countActive(): Long
}

interface ConciergeStaffRepository : JpaRepository<ConciergeStaffEntity, String> {
    fun findByUserId(userId: String): ConciergeStaffEntity?

    @Query("select s from ConciergeStaffEntity s order by s.createdAt asc")
    fun listAll(): List<ConciergeStaffEntity>

    @Query("select count(s) from ConciergeStaffEntity s where s.active = true and s.role = :role")
    fun countActiveByRole(@Param("role") role: String): Long

    @Modifying
    @Query("delete from ConciergeStaffEntity s where s.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}

interface ConciergeContractRepository : JpaRepository<ConciergeContractEntity, String> {

    /** The client's one live contract (draft/active/paused); exited rows stay historical. */
    @Query("select c from ConciergeContractEntity c where c.userId = :userId and c.status <> 'exited'")
    fun findLive(@Param("userId") userId: String): ConciergeContractEntity?

    /**
     * The client's current engagement for read surfaces - latest row in any
     * state, so history and work references stay visible after exit
     * (BR-10 "history remain[s] intact", BR-11 retention).
     */
    fun findFirstByUserIdOrderByCreatedAtDesc(userId: String): ConciergeContractEntity?

    @Query("select c from ConciergeContractEntity c where c.status <> 'exited' order by c.createdAt desc")
    fun listLive(): List<ConciergeContractEntity>

    @Query("select c from ConciergeContractEntity c order by c.createdAt desc")
    fun listAll(): List<ConciergeContractEntity>

    @Modifying
    @Query("delete from ConciergeContractEntity c where c.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}

interface ConciergeTaskRepository : JpaRepository<ConciergeTaskEntity, String> {
    @Query("select t from ConciergeTaskEntity t where t.contractId = :contractId order by t.requestedAt asc")
    fun listByContract(@Param("contractId") contractId: String): List<ConciergeTaskEntity>

    @Query("select t from ConciergeTaskEntity t where t.id = :id and t.userId = :userId")
    fun findOwned(@Param("id") id: String, @Param("userId") userId: String): ConciergeTaskEntity?

    @Query(
        "select count(t) from ConciergeTaskEntity t where t.contractId = :contractId and t.kind = :kind " +
            "and t.status not in ('refused', 'cancelled') and t.requestedAt >= :since",
    )
    fun countVolumeThisMonth(
        @Param("contractId") contractId: String,
        @Param("kind") kind: String,
        @Param("since") since: Instant,
    ): Long

    @Query(
        "select t from ConciergeTaskEntity t where t.contractId = :contractId and t.status in ('requested', 'approved')",
    )
    fun listQueued(@Param("contractId") contractId: String): List<ConciergeTaskEntity>

    @Modifying
    @Query("delete from ConciergeTaskEntity t where t.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}

interface ConciergeApprovalRepository : JpaRepository<ConciergeApprovalEntity, String> {

    @Query("select a from ConciergeApprovalEntity a where a.taskId = :taskId order by a.approvedAt desc")
    fun listByTask(@Param("taskId") taskId: String): List<ConciergeApprovalEntity>

    /** Setup-plan approvals carry no task (BA 03 step 2). */
    @Query(
        "select a from ConciergeApprovalEntity a where a.contractId = :contractId and a.taskId is null " +
            "order by a.approvedAt desc",
    )
    fun listSetup(@Param("contractId") contractId: String): List<ConciergeApprovalEntity>

    @Query("select count(a) from ConciergeApprovalEntity a where a.userId = :userId")
    fun countByUserId(@Param("userId") userId: String): Long
}
