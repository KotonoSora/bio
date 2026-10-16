package com.resume.publicsite.api

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

/**
 * The micro-script (features 10-11): server-rendered/cached by public-site-service,
 * never by the SPA. Edge-cached; correctness is TTL-bound like all public reads.
 */
@RestController
class MicroScriptController {
    @GetMapping("/s/ms.js", produces = ["application/javascript"])
    fun microscript(): ResponseEntity<String> {
        val js = "(function(){window.__rbq=window.__rbq||[];window.rbTrack=function(e){window.__rbq.push(e)};})();"
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS))
            .contentType(MediaType.parseMediaType("application/javascript"))
            .body(js)
    }
}
