package com.resume.gateway.proxy

import com.resume.gateway.security.InternalJwtService
import com.resume.gateway.security.PlatformHosts
import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Feature 04 T-04 gateway gate drills on a real Redis: platform hosts pass
 * through untouched, verified bindings serve with the canonical host, unknown
 * and blocked hosts render the same neutral decision, resolve failures degrade
 * to neutral (never to serving, with the degrade counter), and the
 * `domain.state_changed.v1` invalidation drops the cached decision. The mesh
 * side is a local stub - the gateway contract is exactly the HTTP shape it
 * returns (TL 03 resolve payload).
 */
@Testcontainers
class HostRoutingServiceIT {

    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<Nothing> =
            GenericContainer<Nothing>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379)
    }

    private lateinit var factory: LettuceConnectionFactory
    private lateinit var template: StringRedisTemplate
    private lateinit var meters: SimpleMeterRegistry
    private lateinit var stub: StubProfileService

    @BeforeEach
    fun setUp() {
        factory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379)).apply { afterPropertiesSet() }
        template = StringRedisTemplate(factory).apply { afterPropertiesSet() }
        factory.connection.use { it.serverCommands().flushDb() }
        meters = SimpleMeterRegistry()
        stub = StubProfileService()
    }

    @AfterEach
    fun tearDown() {
        stub.close()
        factory.destroy()
    }

    private fun service(): HostRoutingService = HostRoutingService(
        redis = template,
        jwt = InternalJwtService(kid = "it", privateKeyPem = ""),
        platformHosts = PlatformHosts("localhost,127.0.0.1,::1"),
        meters = meters,
        profileUrl = stub.url,
        timeoutMs = 1000,
    )

    @Test
    fun `platform hosts never reach the resolve`() {
        assertEquals(HostRoutingService.Decision.Platform, service().decide("localhost"))
        assertEquals(HostRoutingService.Decision.Platform, service().decide("LOCALHOST:8080"))
        assertTrue(stub.paths.isEmpty(), "no resolve call for a platform host")
        assertEquals(0, template.keys("gw:domain:*")?.size ?: 0)
    }

    @Test
    fun `verified bindings serve with the canonical host and cache positive for 30 s`() {
        val host = "blog-${System.nanoTime()}.example.com"
        stub.body = { """{"result":"profile","username":"alice","canonicalHost":"$host"}""" }

        assertEquals(HostRoutingService.Decision.Serve("alice", host), service().decide(host))
        assertEquals(listOf("/internal/v1/domains/$host"), stub.paths)
        assertEquals("p:alice|$host", template.opsForValue().get("gw:domain:$host"))
        val ttl = template.getExpire("gw:domain:$host")
        assertTrue(ttl in 1L..30L, "positive decisions cache 30 s (was $ttl)")

        // Second look is cache-served: the stub sees no new request.
        assertEquals(HostRoutingService.Decision.Serve("alice", host), service().decide(host))
        assertEquals(1, stub.paths.size)
    }

    @Test
    fun `unknown and blocked hosts render the same neutral decision and cache negative`() {
        val unknown = "unknown-${System.nanoTime()}.example.com"
        val blocked = "blocked-${System.nanoTime()}.example.com"
        stub.body = { if (it.endsWith(blocked)) """{"result":"blocked"}""" else """{"result":"unknown"}""" }

        assertEquals(HostRoutingService.Decision.Neutral("unknown"), service().decide(unknown))
        assertEquals(HostRoutingService.Decision.Neutral("blocked"), service().decide(blocked))
        assertEquals("unknown", template.opsForValue().get("gw:domain:$unknown"))
        assertEquals("blocked", template.opsForValue().get("gw:domain:$blocked"))
        assertTrue(template.getExpire("gw:domain:$unknown") in 1L..60L, "negative decisions cache 60 s")
    }

    @Test
    fun `resolve failure degrades to neutral with the counter, never to serving`() {
        // A closed port fails fast; the shape of the failure must not matter.
        val degraded = HostRoutingService(
            redis = template,
            jwt = InternalJwtService(kid = "it", privateKeyPem = ""),
            platformHosts = PlatformHosts("localhost"),
            meters = meters,
            profileUrl = "http://127.0.0.1:1",
            timeoutMs = 500,
        )
        val host = "degraded-${System.nanoTime()}.example.com"
        assertEquals(HostRoutingService.Decision.Neutral("resolve_degraded"), degraded.decide(host))
        assertEquals(1.0, meters.counter("gateway.host.resolve_degrade").count())
        assertEquals("blocked", template.opsForValue().get("gw:domain:$host"), "degrade caches negative")
    }

    @Test
    fun `malformed profile payload never serves`() {
        val host = "malformed-${System.nanoTime()}.example.com"
        stub.body = { """{"result":"profile"}""" }
        assertEquals(HostRoutingService.Decision.Neutral("resolve_malformed"), service().decide(host))
    }

    @Test
    fun `state change invalidation drops the cached decision immediately`() {
        val host = "flip-${System.nanoTime()}.example.com"
        stub.body = { """{"result":"unknown"}""" }
        val routing = service()
        assertEquals(HostRoutingService.Decision.Neutral("unknown"), routing.decide(host))

        stub.body = { """{"result":"profile","username":"alice","canonicalHost":"$host"}""" }
        routing.invalidate(host) // domain.state_changed.v1 consumer hook (D-4)
        assertEquals(HostRoutingService.Decision.Serve("alice", host), routing.decide(host))
    }

    /** Stub profile-service: records the internal paths it was asked for. */
    private class StubProfileService : AutoCloseable {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val paths = CopyOnWriteArrayList<String>()

        @Volatile
        var body: (String) -> String = { """{"result":"unknown"}""" }

        val url: String get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/internal/v1/domains/") { exchange ->
                val path = URLDecoder.decode(exchange.requestURI.path, Charsets.UTF_8)
                paths.add(path)
                val bytes = body(path).toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
        }
    }
}
