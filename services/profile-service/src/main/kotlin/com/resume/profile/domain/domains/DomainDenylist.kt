package com.resume.profile.domain.domains

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Denylist row (Q-01): exact host or suffix pattern (`*.brand.com`),
 * normalized lowercase. Matched at add AND on every recheck (S-4);
 * rows are ops-maintained via the audited route.
 */
@Entity
@Table(name = "domain_denylist")
class DomainDenylist(
    @Id var id: String = "",
    @Column(nullable = false, unique = true) var hostname: String = "",
    @Column(nullable = false) var reason: String = "",
    @Column(name = "added_at", nullable = false) var addedAt: Instant = Instant.now(),
) {
    override fun toString(): String = "DomainDenylist($hostname, $reason)"
}
