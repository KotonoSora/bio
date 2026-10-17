package com.resume.billing.api

import com.resume.billing.domain.CheckoutService
import com.resume.billing.domain.Order
import com.resume.billing.domain.PayProvider
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class CheckoutRequest(
    @field:NotBlank val idempotencyKey: String,
    @field:NotNull val provider: PayProvider,
    @field:Min(1000) val amountVnd: Long,
)

@RestController
@RequestMapping("/api/v1/checkout")
class CheckoutController(private val checkout: CheckoutService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun checkout(auth: Authentication?, @RequestBody req: CheckoutRequest): Order {
        val userId = auth?.name ?: "dev-user"
        return checkout.checkout(userId, req.idempotencyKey, req.provider, req.amountVnd).getOrThrow()
    }
}
