package com.resume.gateway.api

import com.resume.gateway.proxy.HostRoutingService
import com.resume.gateway.proxy.ServiceProxy
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Forwards the public edge route table to the owning service (01): the session
 * API, anonymous write surfaces, short links, public resume reads and SSR profile
 * pages. Authorization classes are decided by SecurityConfig before this point;
 * ServiceProxy mints the internal identity JWT for the downstream service.
 *
 * Feature 04: SSR profile paths are Host-routed - a non-platform Host must
 * resolve to a `verified` binding first (T-04); `blocked`/`unknown`/resolve
 * failure short-circuit to the same neutral error page (NFR-09, fail-safe) and
 * never reach a service.
 */
@RestController
class ForwardingController(
    private val proxy: ServiceProxy,
    private val hosts: HostRoutingService,
) {

    @RequestMapping("/api/**")
    fun api(req: HttpServletRequest, res: HttpServletResponse) = proxy.forward(req, res)

    @RequestMapping("/go/**")
    fun go(req: HttpServletRequest, res: HttpServletResponse) = proxy.forward(req, res)

    @RequestMapping("/webhooks/**")
    fun webhooks(req: HttpServletRequest, res: HttpServletResponse) = proxy.forward(req, res)

    @RequestMapping("/cv/**")
    fun cv(req: HttpServletRequest, res: HttpServletResponse) = proxy.forward(req, res)

    @GetMapping("/s/ms.js")
    fun microScript(req: HttpServletRequest, res: HttpServletResponse) = proxy.forward(req, res)

    @GetMapping("/{username:[A-Za-z0-9][A-Za-z0-9_-]*}")
    fun profile(
        req: HttpServletRequest,
        res: HttpServletResponse,
        @PathVariable username: String,
    ) {
        when (val decision = hosts.decide(req.getHeader("Host"))) {
            is HostRoutingService.Decision.Serve -> {
                // BR-04: a verified host renders the BOUND profile's SSR - the
                // requested slug never changes whose page is served. The resolve
                // username is the only address that leaves the gateway, so the
                // canonical stays https://{host}/{bound} for every path.
                if (decision.username.isBlank()) {
                    neutral(res)
                } else {
                    proxy.forward(
                        req,
                        res,
                        mapOf("X-RB-Host-Context" to decision.canonicalHost),
                        pathOverride = "/${decision.username}",
                    )
                }
            }
            is HostRoutingService.Decision.Neutral -> neutral(res)
            HostRoutingService.Decision.Platform -> proxy.forward(req, res)
        }
    }

    /**
     * The one neutral error surface for foreign hosts (BR-04/BR-11): one shape,
     * one timing class, no profile echo, no redirect, no cookies; negative-cache
     * 60 s (NFR-05) so a forgotten host cannot hammer the resolve path.
     */
    private fun neutral(res: HttpServletResponse) {
        if (res.isCommitted) return
        res.status = 404
        res.contentType = "text/html; charset=utf-8"
        res.setHeader("Cache-Control", "public, max-age=60")
        res.writer.write(
            "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"robots\" content=\"noindex\"><title>Site not available</title></head>" +
                "<body><h1>This site isn't available.</h1></body></html>",
        )
    }
}
