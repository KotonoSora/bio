package com.resume.account.api

import com.resume.account.domain.AccountService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Internal REST: JSON, additive-only, RFC 9457 errors, TEXT ids, ISO8601.
 * Mesh-only (gateway denies the internal namespace from the public edge); service JWT required.
 * Entitlement snapshot: consumers fail closed to the freest tier when unavailable.
 */
@RestController
@RequestMapping("/internal/v1/entitlements")
class InternalAccountController(private val accounts: AccountService) {
    @GetMapping("/{userId}")
    fun byUser(@PathVariable userId: String): Map<String, String> {
        val a = accounts.me(userId).getOrElse { return mapOf("userId" to userId, "tier" to "free") }
        return mapOf("userId" to a.userId, "tier" to a.entitlementTier)
    }
}
