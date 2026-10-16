package com.resume.gateway.api

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
 */
@RestController
class ForwardingController(private val proxy: ServiceProxy) {

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
    ) = proxy.forward(req, res)
}
