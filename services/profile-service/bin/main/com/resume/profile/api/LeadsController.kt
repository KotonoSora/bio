package com.resume.profile.api

import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class LeadSubmitRequest(@field:NotBlank val email: String, val name: String? = null)

/** Anonymous write surface: Turnstile-class + throttled at the gateway, queue-mediated. Uniform 202. */
@RestController
@RequestMapping("/api/t/leads/submit")
class LeadsController {
    @PostMapping("/{blockId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submit(@PathVariable blockId: String, @RequestBody req: LeadSubmitRequest): Map<String, Boolean> =
        mapOf("accepted" to true)
}
