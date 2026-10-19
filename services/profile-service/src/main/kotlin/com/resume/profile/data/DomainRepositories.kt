package com.resume.profile.data

import com.resume.profile.domain.domains.CustomDomain
import com.resume.profile.domain.domains.DomainDenylist
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/** Binding rows (feature 04 V6); hostname UNIQUE is the takeover guard (S-3). */
interface CustomDomainRepository : JpaRepository<CustomDomain, String> {
    fun findByHostname(host: String): CustomDomain?
    fun existsByHostname(host: String): Boolean
    fun findByUserIdOrderByCreatedAtAsc(userId: String): List<CustomDomain>
    fun countByUserId(userId: String): Long

    /** Worker sweeps (BR-08): states are status literals; NFR-10 windows. */
    fun findByStatus(status: String): List<CustomDomain>

    /** Serving halt at the delete commit (BR-07, NFR-04): the delete is issued in-call. */
    fun deleteByHostname(host: String): Long

    /** Profile paths can never be domain-owned; card business via profile_id only. */
    fun findByProfileId(profileId: String): List<CustomDomain>

    /** Participant purge (feature 02): svc-scoped delete by explicit user predicate. */
    @Modifying
    @Transactional
    @Query("delete from CustomDomain d where d.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}

/** Denylist rows (Q-01): read everywhere, written only via the ops route. */
interface DomainDenylistRepository : JpaRepository<DomainDenylist, String> {
    fun existsByHostname(host: String): Boolean
    fun findByHostname(host: String): DomainDenylist?
}
