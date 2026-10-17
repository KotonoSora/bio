package com.resume.account.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class AccountResponse(val id: String, val userId: String, val email: String, val displayName: String?, val entitlementTier: String)
data class UpdateAccountRequest(@field:NotBlank val displayName: String, @field:Email val email: String)
