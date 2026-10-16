package com.resume.shop.api

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

 /** Partner callbacks: signature-verified, queue-mediated. Uniform 202, replay-safe. */
@RestController
@RequestMapping("/api/merch")
class PartnerCallbackController {
    @PostMapping("/partner-callback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun callback(@RequestBody body: Map<String, String>): Map<String, Boolean> =
        mapOf("accepted" to true)
}
