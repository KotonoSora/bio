package com.resume.account.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
}

interface LinkedAccountRepository : JpaRepository<LinkedAccountEntity, String> {
    fun findByProviderIdAndProviderSub(providerId: String, providerSub: String): LinkedAccountEntity?
    fun findByUserIdOrderByLinkedAtAsc(userId: String): List<LinkedAccountEntity>
    fun findByUserIdAndProviderId(userId: String, providerId: String): LinkedAccountEntity?
    fun countByUserId(userId: String): Long
}

interface SessionRepository : JpaRepository<SessionEntity, String> {
    fun findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId: String): List<SessionEntity>
    fun countByUserIdAndRevokedAtIsNull(userId: String): Long

    @Query(
        "select s from SessionEntity s where s.revokedAt is null " +
            "and (s.idleExpiresAt < :now or s.absoluteExpiresAt < :now)",
    )
    fun findExpired(@Param("now") now: Instant): List<SessionEntity>
}

interface AuditRepository : JpaRepository<AuditEntryEntity, String> {
    fun findTop50ByUserIdOrderByAtDesc(userId: String): List<AuditEntryEntity>
}
