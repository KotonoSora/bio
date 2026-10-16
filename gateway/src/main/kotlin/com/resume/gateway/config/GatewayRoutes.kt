package com.resume.gateway.config

/**
 * Edge route table (01-architecture-and-service-topology.md). The forwarding
 * table in `com.resume.gateway.proxy.RouteTable` routes the session API
 * (`/api/v1/`) to the owning service, the internal mesh namespace stays
 * mesh-only (denied from the public edge in SecurityConfig), short links go
 * to link-service:8080, and public profiles go to public-site-service:8080
 * (SSR). Session state lives at the edge, never in services; W3C traceparent
 * propagates gateway -> service -> queue.
 */
object GatewayRoutes {
    const val SESSION_API = "/api/v1/**"
    const val INTERNAL_MESH = "/internal/v1/**"
    const val SHORT_LINKS = "/go/**"
    const val PUBLIC_PROFILE = "/{username}"

    val SERVICE_HOSTS: Map<String, String> = mapOf(
        "account-service" to "http://account-service:8080",
        "profile-service" to "http://profile-service:8080",
        "link-service" to "http://link-service:8080",
        "public-site-service" to "http://public-site-service:8080",
        "analytics-service" to "http://analytics-service:8080",
        "resume-service" to "http://resume-service:8080",
        "shop-service" to "http://shop-service:8080",
        "billing-service" to "http://billing-service:8080",
        "publish-service" to "http://publish-service:8080",
    )
}
