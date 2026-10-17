package com.resume.account.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

 /** SSO entry points (SSO-only; no passwords, no reset flow). Sessions terminate at the gateway. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    @GetMapping("/login/{provider}")
    fun login(@PathVariable provider: String): Map<String, String> =
        mapOf("provider" to provider.lowercase(), "authorizeUrl" to "/oauth2/authorization/$provider")
}
