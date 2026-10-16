package com.resume.analytics.api

import com.resume.analytics.domain.DegradeState
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Degrade ladder switch (mesh-only). Shedding is explicit and reversible;
 * income paths (intake 202s, grants, redirects) are never shed by this switch.
 */
@RestController
@RequestMapping("/internal/v1/degrade")
class DegradeController(private val degrade: DegradeState) {

    @GetMapping
    fun status(): Map<String, Any> =
        mapOf("shedRealtime" to degrade.shedRealtime.get(), "firstShedCandidate" to "realtime-aggregation")

    @PostMapping
    fun set(@RequestBody body: Map<String, Boolean>): Map<String, Any> {
        degrade.shedRealtime.set(body["shedRealtime"] ?: false)
        return status()
    }
}
