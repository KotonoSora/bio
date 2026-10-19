package com.resume.gateway.proxy

import com.resume.gateway.config.GatewayRoutes

/**
 * Gateway forwarding table (01-architecture-and-service-topology.md): the gateway
 * routes the SSO surface (`/api/auth/`), the owner API (`/api/v1/me`), the tier
 * presentation reads (`/api/v1/plan`, `/api/v1/plans`, forward `/api/v1/billing/`),
 * custom-domain owner CRUD (`/api/v1/domains/`), short links (`/go/`), public
 * resume reads (`/cv/`) and `/{username}` (public reads) to the owning service of
 * the closed service map. Most specific prefix wins; unknown API paths fail closed
 * (404) - the gateway never guesses an owner, and the internal mesh namespace
 * stays mesh-only.
 */
class RouteTable(private val serviceHosts: Map<String, String> = GatewayRoutes.SERVICE_HOSTS) {

    data class Target(val service: String, val baseUrl: String)

    fun resolve(path: String): Target? {
        if (SINGLE_SEGMENT.matches(path)) {
            val slug = path.removePrefix("/").lowercase()
            return if (slug in RESERVED_SEGMENTS) null else target("public-site-service")
        }
        if (path == "/s/ms.js") return target("public-site-service")
        for ((prefix, service) in PREFIXES) {
            if (path.startsWith(prefix) || path == prefix.trimEnd('/')) return target(service)
        }
        return null
    }

    private fun target(service: String): Target? =
        serviceHosts[service]?.let { Target(service, it) }

    companion object {
        /** Ordered most-specific-first; public write surfaces verify downstream. */
        val PREFIXES: List<Pair<String, String>> = listOf(
            "/api/auth/" to "account-service",
            "/api/v1/auth/" to "account-service",
            "/api/v1/me/" to "account-service",
            "/api/v1/accounts/" to "account-service",
            "/api/v1/account/" to "account-service",
            "/api/v1/profiles/" to "profile-service",
            "/api/v1/domains/" to "profile-service",
            "/api/t/leads/submit/" to "profile-service",
            "/api/t/" to "analytics-service",
            "/api/v1/links/" to "link-service",
            "/api/v1/resumes/" to "resume-service",
            "/api/v1/products/" to "shop-service",
            "/api/v1/plans/" to "billing-service",
            "/api/v1/plan/" to "account-service",
            "/api/v1/billing/" to "billing-service",
            "/api/v1/checkout/" to "billing-service",
            "/api/v1/publish/" to "publish-service",
            "/api/v1/social/" to "publish-service",
            "/api/merch/" to "shop-service",
            "/webhooks/social/" to "publish-service",
            "/webhooks/instagram" to "publish-service",
            "/webhooks/" to "billing-service",
            "/go/" to "link-service",
            "/cv/" to "resume-service",
        )

        /** Reserved slugs (AGENTS.md) plus infra surfaces never route to a service. */
        val RESERVED_SEGMENTS: Set<String> = setOf(
            "app", "api", "admin", "static", "r", "cv", "shop", "login", "signup",
            "pricing", "blog", "go", "a", "health", "actuator", "webhooks", "media",
            "internal", "internal-media", "oauth2", "logout", "error", "assets", "s",
        )

        private val SINGLE_SEGMENT = Regex("^/[A-Za-z0-9][A-Za-z0-9_-]{0,62}$")
    }
}
