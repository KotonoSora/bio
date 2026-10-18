package com.resume.gateway.proxy

import com.resume.gateway.security.InternalJwtService
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Feature 04 BR-04 forwarding contract: the host-routed bio surface rewrites
 * the forwarded path to the resolved (bound) username while the gateway-set
 * `X-RB-Host-Context` (and never the client's own value) reaches the upstream,
 * query strings survive, and responses relay with the deny-list applied.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceProxyTest {

    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<Request>()

    private data class Request(val path: String, val query: String?, val hostContext: String?)

    private lateinit var proxy: ServiceProxy

    @BeforeAll
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(
                Request(
                    path = exchange.requestURI.path,
                    query = exchange.requestURI.query,
                    hostContext = exchange.requestHeaders.getFirst("x-rb-host-context"),
                ),
            )
            val bytes = "render".toByteArray()
            exchange.responseHeaders.add("X-Upstream-Marker", "yes")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        proxy = ServiceProxy(
            RouteTable(mapOf("public-site-service" to "http://127.0.0.1:${server.address.port}")),
            InternalJwtService(kid = "it", privateKeyPem = ""),
        )
    }

    @AfterAll
    fun tearDown() {
        server.stop(0)
    }

    @BeforeEach
    fun resetLog() {
        requests.clear()
    }

    @Test
    fun `host routing rewrites to the bound username and the client context never survives`() {
        val req = MockHttpServletRequest("GET", "/someone-else").apply {
            queryString = "ref=x"
            addHeader("X-RB-Host-Context", "evil.example.test")
        }
        val res = MockHttpServletResponse()

        proxy.forward(
            req,
            res,
            mapOf("X-RB-Host-Context" to "blog.example.test"),
            pathOverride = "/bound-user",
        )

        assertEquals(200, res.status)
        assertEquals("render", res.contentAsString)
        assertEquals("yes", res.getHeader("X-Upstream-Marker"), "upstream response headers relay")
        val seen = requests.single()
        assertEquals("/bound-user", seen.path, "the bound profile is the only address this host may serve")
        assertEquals("ref=x", seen.query, "query strings survive the rewrite")
        assertEquals("blog.example.test", seen.hostContext, "gateway-set context wins; the client value was stripped")
    }

    @Test
    fun `without an override the requested path is forwarded untouched`() {
        val req = MockHttpServletRequest("GET", "/someone-else")
        val res = MockHttpServletResponse()

        proxy.forward(req, res)

        assertEquals(200, res.status)
        assertEquals("/someone-else", requests.last().path)
        assertTrue(requests.last().hostContext == null, "no host context is fabricated for platform reads")
    }

    @Test
    fun `an unknown route fails closed without contacting an upstream`() {
        val before = requests.size
        val req = MockHttpServletRequest("GET", "/internal/v1/domains/x")
        val res = MockHttpServletResponse()

        proxy.forward(req, res)

        assertEquals(404, res.status)
        assertEquals(before, requests.size, "the internal mesh namespace is never proxied")
    }
}
