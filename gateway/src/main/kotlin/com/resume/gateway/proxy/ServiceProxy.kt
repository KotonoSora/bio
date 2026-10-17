package com.resume.gateway.proxy

import com.resume.gateway.security.CachedBodyRequest
import com.resume.gateway.security.EdgeIdentity
import com.resume.gateway.security.InternalJwtService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.security.SecureRandom
import java.time.Duration

/**
 * Server-side forwarding to the owning service (01-architecture-and-service-topology.md):
 * the client session terminates at the gateway, which mints the internal identity JWT
 * (RS256, aud=<service>, 60 s TTL) per request - session callers carry `sid` +
 * `auth_time` claims from the edge identity (feature 01 D-6); raw client headers
 * never cross the mesh (cookies and client Authorization are stripped,
 * X-Forwarded-* and traceparent are set). Upstream responses stream back
 * untouched, including X-Accel-Redirect, which the Nginx edge turns into a gated
 * byte read (09-local-storage-and-nginx-spec.md).
 */
@Service
class ServiceProxy(
    private val routes: RouteTable,
    private val jwt: InternalJwtService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val rng = SecureRandom()

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            ).apply { setReadTimeout(Duration.ofSeconds(30)) },
        )
        .build()

    fun forward(req: HttpServletRequest, res: HttpServletResponse) {
        val target = routes.resolve(req.requestURI)
        if (target == null) {
            problem(res, 404, "not_found", "no route for ${req.requestURI}")
            return
        }
        val query = req.queryString?.let { "?$it" } ?: ""
        val uri = URI.create(target.baseUrl + req.requestURI + query)
        val method = HttpMethod.valueOf(req.method)
        val payload = requestBody(req)

        try {
            val spec = client.method(method).uri(uri).headers { it.addAll(outboundHeaders(req, target)) }
            val response = if (payload != null && method != HttpMethod.GET && method != HttpMethod.HEAD) {
                spec.body(payload).exchange { _, upstream -> upstream }
            } else {
                spec.exchange { _, upstream -> upstream }
            }
            res.status = response.statusCode.value()
            response.headers.forEach { name, values ->
                if (name.lowercase() !in RESPONSE_DENY) values.forEach { res.addHeader(name, it) }
            }
            val bytes = response.body.readBytes()
            res.setContentLength(bytes.size)
            if (bytes.isNotEmpty()) res.outputStream.write(bytes)
        } catch (ex: RestClientException) {
            log.warn("upstream unavailable: {} {} -> {} ({})", req.method, req.requestURI, target.service, ex.message)
            problem(res, 503, "upstream_unavailable", "owning service unavailable")
        } catch (ex: IOException) {
            // Client abort mid-stream: nothing to write, never fabricate a response.
            log.warn("upstream transport error: {} {} -> {} ({})", req.method, req.requestURI, target.service, ex.message)
            problem(res, 503, "upstream_unavailable", "owning service unavailable")
        }
    }

    private fun outboundHeaders(req: HttpServletRequest, target: RouteTable.Target): HttpHeaders {
        val headers = HttpHeaders()
        req.headerNames.toList().forEach { name ->
            if (name.lowercase() !in REQUEST_DENY) headers.addAll(name, req.getHeaders(name).toList())
        }
        val base = URI.create(target.baseUrl)
        headers.set(HttpHeaders.HOST, "${base.host}:${base.port}")
        val priorXff = req.getHeader("X-Forwarded-For")
        headers.set("X-Forwarded-For", if (priorXff != null) "$priorXff, ${req.remoteAddr}" else req.remoteAddr)
        headers.set("X-Forwarded-Proto", req.scheme)
        headers.set("X-Forwarded-Host", req.getHeader("Host") ?: "")
        headers.set("traceparent", req.getHeader("traceparent") ?: newTraceparent())
        if (jwt.configured) {
            val identity = edgeIdentity()
            val claims = if (identity != null) {
                mapOf<String, Any>("sid" to identity.sid, "auth_time" to identity.authTimeEpochSeconds)
            } else {
                emptyMap()
            }
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issue(userId = subject(), audience = target.service, extraClaims = claims))
        }
        return headers
    }

    private fun edgeIdentity(): EdgeIdentity? =
        SecurityContextHolder.getContext().authentication?.details as? EdgeIdentity

    private fun subject(): String {
        edgeIdentity()?.let { return it.userId }
        val auth = SecurityContextHolder.getContext().authentication
        return if (auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken) auth.name else "anonymous"
    }

    private fun requestBody(req: HttpServletRequest): ByteArray? {
        if (req.method == "GET" || req.method == "HEAD") return null
        if (req is CachedBodyRequest) return req.body.takeIf { it.isNotEmpty() }
        return req.inputStream.readBytes().takeIf { it.isNotEmpty() }
    }

    private fun newTraceparent(): String {
        val trace = ByteArray(16).also(rng::nextBytes).joinToString("") { "%02x".format(it) }
        val span = ByteArray(8).also(rng::nextBytes).joinToString("") { "%02x".format(it) }
        return "00-$trace-$span-01"
    }

    private fun problem(res: HttpServletResponse, status: Int, code: String, detail: String) {
        if (res.isCommitted) return
        try {
            res.status = status
            res.contentType = "application/problem+json"
            val trace = MDC.get("traceId") ?: ""
            res.writer.write(
                """{"type":"about:blank","title":"$code","status":$status,"code":"$code","detail":"$detail","traceId":"$trace"}""",
            )
        } catch (_: IOException) {
            // Client already gone; nothing to report.
        }
    }

    companion object {
        private val REQUEST_DENY = setOf(
            "host", "cookie", "authorization", "content-length", "connection", "keep-alive",
            "transfer-encoding", "te", "trailer", "upgrade", "proxy-authorization", "expect",
            "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "x-forwarded-port", "forwarded",
            "x-csrf-token",
        )
        private val RESPONSE_DENY = setOf(
            "connection", "keep-alive", "transfer-encoding", "content-length", "te", "trailer",
            "upgrade", "proxy-authenticate", "proxy-authorization",
        )
    }
}
