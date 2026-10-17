package com.resume.account.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface UserRepository : JpaRepository<UserEntity, String> {

    @Query("select u from UserEntity u where lower(u.email) = lower(:email)")
    fun byLowerEmail(@Param("email") email: String): List<UserEntity>

    /** Verified contact-email owners other than [userId] (BR-03 uniqueness). */
    @Query(
        "select count(u) from UserEntity u where lower(u.contactEmail) = lower(:email) " +
            "and u.contactVerifiedAt is not null and u.id <> :userId",
    )
    fun countVerifiedContactOutside(@Param("userId") userId: String, @Param("email") email: String): Long

    /** Purge path (feature 02): hard delete under the `svc` RLS scope. */
    @Modifying
    @Query("delete from UserEntity u where u.id = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}

interface LinkedAccountRepository : JpaRepository<LinkedAccountEntity, String> {
    fun findByProviderIdAndProviderSub(providerId: String, providerSub: String): LinkedAccountEntity?
    fun findByUserIdOrderByLinkedAtAsc(userId: String): List<LinkedAccountEntity>
    fun findByUserIdAndProviderId(userId: String, providerId: String): LinkedAccountEntity?
    fun countByUserId(userId: String): Long

    @Modifying
    @Query("delete from LinkedAccountEntity l where l.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}

interface SessionRepository : JpaRepository<SessionEntity, String> {
    fun findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId: String): List<SessionEntity>
    fun countByUserIdAndRevokedAtIsNull(userId: String): Long

    @Query(
        "select s from SessionEntity s where s.revokedAt is null " +
            "and (s.idleExpiresAt < :now or s.absoluteExpiresAt < :now)",
    )
    fun findExpired(@Param("now") now: Instant): List<SessionEntity>

    @Modifying
    @Query("delete from SessionEntity s where s.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int

    /** Residual probe: every row for the account, revoked or not. */
    fun countByUserId(userId: String): Long
}

interface AuditRepository : JpaRepository<AuditEntryEntity, String> {
    fun findTop50ByUserIdOrderByAtDesc(userId: String): List<AuditEntryEntity>
}

/** Deletion records (feature 02; `deletion_requests`). */
interface DeletionRequestRepository : JpaRepository<DeletionRequestEntity, String> {
    fun findByUserId(userId: String): DeletionRequestEntity?

    /** Daily runner due set: grace rows past their purge date. */
    @Query("select d from DeletionRequestEntity d where d.status = 'grace' and d.purgeDueAt <= :now")
    fun findDue(@Param("now") now: Instant): List<DeletionRequestEntity>

    /** Day-23 reminder set: grace rows with no send yet, requested long enough ago, not yet due. */
    @Query(
        "select d from DeletionRequestEntity d where d.status = 'grace' and d.reminderSentAt is null " +
            "and d.requestedAt <= :reminderCutoff and d.purgeDueAt > :now",
    )
    fun findReminderDue(@Param("reminderCutoff") reminderCutoff: Instant, @Param("now") now: Instant): List<DeletionRequestEntity>

    /** Residual probe set: purged accounts with a certificate reference. */
    fun findByStatus(status: String): List<DeletionRequestEntity>

    /**
     * Per-account DB guard (S-3, truth): atomically claims a due row for this
     * run. A row attempted within `staleBefore` loses the claim (another runner
     * is/was processing it); 0 rows updated = skip this pass.
     */
    @Modifying
    @Transactional
    @Query(
        "update DeletionRequestEntity d set d.purgeStartedAt = :now, d.runId = :runId " +
            "where d.id = :id and d.status = 'grace' " +
            "and (d.purgeStartedAt is null or d.purgeStartedAt < :staleBefore)",
    )
    fun claim(
        @Param("id") id: String,
        @Param("runId") runId: String,
        @Param("now") now: Instant,
        @Param("staleBefore") staleBefore: Instant,
    ): Int

    /** Stuck set (S-9): grace rows whose attempt never completed within the alert window. */
    @Query(
        "select d from DeletionRequestEntity d where d.status = 'grace' " +
            "and d.purgeStartedAt is not null and d.purgeStartedAt < :staleBefore",
    )
    fun findStuck(@Param("staleBefore") staleBefore: Instant): List<DeletionRequestEntity>

    /** Single-send guard (NFR-03): first writer wins; 0 rows = already sent or no longer grace. */
    @Modifying
    @Transactional
    @Query(
        "update DeletionRequestEntity d set d.reminderSentAt = :now " +
            "where d.userId = :userId and d.status = 'grace' and d.reminderSentAt is null",
    )
    fun markReminderSent(@Param("userId") userId: String, @Param("now") now: Instant): Int

    /**
     * Terminal flip (only after the certificate rename succeeded). Conditional
     * on `grace` so a restore that won the race is never overwritten; 0 rows =
     * the account was cancelled mid-run.
     */
    @Modifying
    @Transactional
    @Query(
        "update DeletionRequestEntity d set d.status = 'purged', d.purgedAt = :now, " +
            "d.certificateRef = :ref, d.runId = :runId where d.id = :id and d.status = 'grace'",
    )
    fun markPurged(
        @Param("id") id: String,
        @Param("runId") runId: String,
        @Param("now") now: Instant,
        @Param("ref") ref: String,
    ): Int
}
