package com.resume.profile.domain.domains

import com.resume.profile.data.CustomDomainRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Any-owner existence probe (BR-03/BR-11): owner RLS hides rows the caller
 * does not own, so two cross-account questions need one svc-scope read - the
 * `403` foreign vs `404` unknown distinction, and the add-time takeover guard
 * (a foreign-held hostname must answer `409 domain_taken`, never a raw unique
 * violation). REQUIRES_NEW is load-bearing: RlsAwareDataSource binds
 * `app.context` when a connection is borrowed, so the probe must borrow its
 * own connection inside the caller's `RlsUserHolder.asService` block (the
 * caller's connection keeps owner scope). The probe only returns the row; the
 * caller compares `user_id` - the context alone is never an authorization
 * (baseline 08).
 */
@Component
class DomainExistenceProbe(private val domains: CustomDomainRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun lookup(hostname: String): CustomDomain? = domains.findByHostname(hostname)
}
