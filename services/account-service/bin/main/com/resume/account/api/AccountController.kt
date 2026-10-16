package com.resume.account.api

import com.resume.account.domain.AccountService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController(private val accounts: AccountService) {
    @GetMapping("/me")
    fun me(auth: Authentication?): AccountResponse {
        val userId = auth?.name ?: "dev-user"
        val a = accounts.me(userId).getOrThrow()
        return AccountResponse(a.id, a.userId, a.email, a.displayName, a.entitlementTier)
    }
}
