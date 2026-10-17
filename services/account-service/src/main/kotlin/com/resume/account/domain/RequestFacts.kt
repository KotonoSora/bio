package com.resume.account.domain

import com.resume.account.domain.Tokens.sha256Hex
import jakarta.servlet.http.HttpServletRequest

/**
 * Coarse request facts only (NFR-12): no raw IP or user-agent is ever stored or
 * logged. `ip_class` is the hashed /24 (v4) or /48 (v6) network class; the
 * session list renders a masked, non-reversible label from it.
 */
object RequestFacts {

    /** Client hop: the edge appends the caller first; the first XFF entry is the client. */
    fun clientIp(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()?.ifBlank { null }
            ?: request.remoteAddr

    fun ipClass(ip: String?): String? {
        val raw = ip ?: return null
        val classed = when {
            raw.contains(':') -> raw.split(":").take(3).joinToString(":") + "::/48"
            raw.split(".").size == 4 -> raw.substringBeforeLast(".") + ".0/24"
            else -> return sha256Hex("unknown").take(12)
        }
        return sha256Hex(classed).take(12)
    }

    fun ipClass(request: HttpServletRequest): String? = ipClass(clientIp(request))

    /** Coarse UA family label only ("Chrome on macOS"); never the raw UA. */
    fun deviceLabel(userAgent: String?): String? {
        val ua = userAgent ?: return null
        val browser = when {
            ua.contains("Edg/") -> "Edge"
            ua.contains("OPR/") || ua.contains("Opera") -> "Opera"
            ua.contains("Chrome/") -> "Chrome"
            ua.contains("Firefox/") -> "Firefox"
            ua.contains("Safari/") -> "Safari"
            else -> null
        }
        val platform = when {
            ua.contains("iPhone") || ua.contains("iPad") -> "iOS"
            ua.contains("Android") -> "Android"
            ua.contains("Mac OS X") || ua.contains("Macintosh") -> "macOS"
            ua.contains("Windows") -> "Windows"
            ua.contains("Linux") -> "Linux"
            else -> null
        }
        return when {
            browser != null && platform != null -> "$browser on $platform"
            browser != null -> browser
            platform != null -> platform
            else -> "Unknown device"
        }
    }

    fun deviceLabel(request: HttpServletRequest): String? = deviceLabel(request.getHeader("User-Agent"))
}
