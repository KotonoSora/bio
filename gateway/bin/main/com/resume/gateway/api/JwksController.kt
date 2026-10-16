package com.resume.gateway.api

import com.resume.gateway.security.InternalJwtService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwksController(private val jwt: InternalJwtService) {
    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> = mapOf("keys" to listOf(jwt.publicJwk()))
}
