package com.resume.billing.api

import com.resume.billing.domain.GrantService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Mesh-only grant read. Grants are idempotent by design; replay-safe. */
@RestController
@RequestMapping("/internal/v1/grants")
class InternalGrantController(private val grants: GrantService) {
    @GetMapping("/by-order/{orderId}")
    fun byOrder(@PathVariable orderId: String): Map<String, Any> {
        val o = grants.byOrder(orderId).getOrThrow()
        return mapOf("orderId" to o.id, "status" to o.status, "amountVnd" to o.amountVnd, "provider" to o.provider.name)
    }
}
