package com.resume.analytics.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Owner/agency reads. Honesty rule: eviction or loss degrades to zeros, never fabricated values. */
@RestController
@RequestMapping("/internal/v1/stats")
class InternalStatsController {
    @GetMapping("/{username}")
    fun stats(@PathVariable username: String): Map<String, Any> =
        mapOf("username" to username.lowercase(), "views" to 0, "uniques" to 0, "partial" to true)
}
