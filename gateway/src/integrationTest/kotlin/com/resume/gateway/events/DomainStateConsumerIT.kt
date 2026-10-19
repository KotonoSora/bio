package com.resume.gateway.events

import com.resume.gateway.proxy.HostRoutingService
import com.resume.gateway.security.InternalJwtService
import com.resume.gateway.security.PlatformHosts
import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Feature 04 state-change transport drill on a real Redis: the consumer group
 * reads `events:profile` from the producer's logical db (db 4) and drops the
 * cached `gw:domain:{host}` decision - the explicit-removal halt at commit
 * (NFR-04; TL 01: the event purge is the primary path, TTLs are the backstop).
 * This pins the stream-db wiring the composed stack initially missed: the
 * gateway's own db (1) is never where the events are read from.
 */
@Testcontainers
class DomainStateConsumerIT {

    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<Nothing> =
            GenericContainer<Nothing>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379)
    }

    private lateinit var gatewayFactory: LettuceConnectionFactory
    private lateinit var gatewayTemplate: StringRedisTemplate
    private lateinit var eventsFactory: LettuceConnectionFactory
    private lateinit var eventsTemplate: StringRedisTemplate
    private lateinit var stub: StubProfileService

    @BeforeEach
    fun setUp() {
        gatewayFactory = factory(database = 1)
        gatewayTemplate = StringRedisTemplate(gatewayFactory).apply { afterPropertiesSet() }
        eventsFactory = factory(database = 4)
        eventsTemplate = StringRedisTemplate(eventsFactory).apply { afterPropertiesSet() }
        gatewayFactory.connection.use { it.serverCommands().flushDb() }
        eventsFactory.connection.use { it.serverCommands().flushDb() }
        stub = StubProfileService()
    }

    @AfterEach
    fun tearDown() {
        stub.close()
        gatewayFactory.destroy()
        eventsFactory.destroy()
    }

    @Test
    fun `a state-change event on the producer's db drops the cached decision`() {
        val host = "flip-${System.nanoTime()}.example.com"
        stub.body = { """{"result":"profile","username":"alice","canonicalHost":"$host"}""" }
        val routing = routing()
        val consumer = DomainStateConsumer(eventsTemplate, routing, consumerName = "it-1")

        consumer.poll() // first poll creates the consumer group at the stream tail
        assertEquals(HostRoutingService.Decision.Serve("alice", host), routing.decide(host))
        assertEquals("p:alice|$host", gatewayTemplate.opsForValue().get("gw:domain:$host"))

        // The producer writes into ITS logical db (4), in the EventPublisher shape.
        eventsTemplate.opsForStream<String, String>().add(
            MapRecord.create(
                "events:profile",
                mapOf(
                    "type" to "domain.state_changed.v1",
                    "aggregateId" to host,
                    "userId" to "usr_it",
                    "occurredAt" to Instant.now().toString(),
                    "attr.hostname" to host,
                    "attr.state" to "removed",
                ),
            ),
        )

        consumer.poll() // the next scheduled sweep

        assertNull(gatewayTemplate.opsForValue().get("gw:domain:$host"), "decision dropped at the event")
        routing.decide(host)
        assertEquals(2, stub.paths.size, "the next look re-resolves after invalidation")
    }

    @Test
    fun `events written to the service's own db are never consumed`() {
        val host = "ghost-${System.nanoTime()}.example.com"
        stub.body = { """{"result":"unknown"}""" }
        val routing = routing()
        val consumer = DomainStateConsumer(eventsTemplate, routing, consumerName = "it-2")

        consumer.poll()
        routing.decide(host)
        assertEquals("unknown", gatewayTemplate.opsForValue().get("gw:domain:$host"))

        // Wrong db (the gateway's own key space): the consumer must not see it.
        gatewayTemplate.opsForStream<String, String>().add(
            MapRecord.create(
                "events:profile",
                mapOf("type" to "domain.state_changed.v1", "attr.hostname" to host),
            ),
        )
        consumer.poll()

        assertEquals(
            "unknown",
            gatewayTemplate.opsForValue().get("gw:domain:$host"),
            "still cached: wrong-db events are invisible",
        )
    }

    private fun factory(database: Int): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(redis.host, redis.getMappedPort(6379))
        cfg.database = database
        return LettuceConnectionFactory(cfg).apply { afterPropertiesSet() }
    }

    private fun routing(): HostRoutingService = HostRoutingService(
        redis = gatewayTemplate,
        jwt = InternalJwtService(kid = "it", privateKeyPem = ""),
        platformHosts = PlatformHosts("localhost,127.0.0.1,::1"),
        meters = SimpleMeterRegistry(),
        profileUrl = stub.url,
        timeoutMs = 1000,
    )

    /** Stub profile-service: records the internal paths it was asked for. */
    private class StubProfileService : AutoCloseable {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val paths = CopyOnWriteArrayList<String>()

        @Volatile
        var body: (String) -> String = { """{"result":"unknown"}""" }

        val url: String get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/internal/v1/domains/") { exchange ->
                val path = java.net.URLDecoder.decode(exchange.requestURI.path, Charsets.UTF_8)
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
