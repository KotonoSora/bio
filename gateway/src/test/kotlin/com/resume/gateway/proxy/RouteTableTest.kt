package com.resume.gateway.proxy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RouteTableTest {

    private val routes = RouteTable()

    @Test
    fun `session api routes to the owning service`() {
        assertEquals("account-service", routes.resolve("/api/v1/accounts/me")?.service)
        assertEquals("account-service", routes.resolve("/api/v1/auth/login/google")?.service)
        assertEquals("profile-service", routes.resolve("/api/v1/profiles/me")?.service)
        assertEquals("link-service", routes.resolve("/api/v1/links")?.service)
        assertEquals("resume-service", routes.resolve("/api/v1/resumes/res_1")?.service)
        assertEquals("shop-service", routes.resolve("/api/v1/products/mine")?.service)
        assertEquals("billing-service", routes.resolve("/api/v1/checkout/sessions")?.service)
        assertEquals("publish-service", routes.resolve("/api/v1/publish/posts")?.service)
    }

    @Test
    fun `anonymous surfaces route by specificity`() {
        assertEquals("profile-service", routes.resolve("/api/t/leads/submit/blk_1")?.service)
        assertEquals("analytics-service", routes.resolve("/api/t/someuser")?.service)
        assertEquals("shop-service", routes.resolve("/api/merch/partner-callback")?.service)
        assertEquals("publish-service", routes.resolve("/webhooks/social/instagram")?.service)
        assertEquals("publish-service", routes.resolve("/webhooks/instagram")?.service)
        assertEquals("billing-service", routes.resolve("/webhooks/stripe")?.service)
        assertEquals("link-service", routes.resolve("/go/abc123")?.service)
        assertEquals("resume-service", routes.resolve("/cv/res_1")?.service)
    }

    @Test
    fun `tier presentation reads route to their owning services (feature 03, D-1 and D-2)`() {
        // The public plans read is billing's (feature 35's serving surface); the
        // trigram-exact path never bleeds into the account plan read.
        assertEquals("billing-service", routes.resolve("/api/v1/plans")?.service)
        assertEquals("billing-service", routes.resolve("/api/v1/plans/extra/wrong")?.service)
        // The account plan read is account-service's, anonymous by contract
        // (401 at the gateway; the service self-checks the session).
        assertEquals("account-service", routes.resolve("/api/v1/plan")?.service)
        // Near-misses never guess an owner.
        assertNull(routes.resolve("/api/v1/plansx"))
    }

    @Test
    fun `billing forward surfaces route to billing-service (features 36-39)`() {
        assertEquals("billing-service", routes.resolve("/api/v1/billing/plan/cancel")?.service)
        assertEquals("billing-service", routes.resolve("/api/v1/billing/trial/start")?.service)
        assertNull(routes.resolve("/api/v1/billingx/plan/cancel"))
    }

    @Test
    fun `public ssr pages route to public-site-service`() {
        assertEquals("public-site-service", routes.resolve("/someuser")?.service)
        assertEquals("public-site-service", routes.resolve("/s/ms.js")?.service)
    }

    @Test
    fun `reserved slugs and infra segments never route`() {
        assertNull(routes.resolve("/app"))
        assertNull(routes.resolve("/admin"))
        assertNull(routes.resolve("/api"))
        assertNull(routes.resolve("/login"))
        assertNull(routes.resolve("/media"))
        assertNull(routes.resolve("/internal-media"))
    }

    @Test
    fun `unknown and non-routed paths fail closed`() {
        assertNull(routes.resolve("/api/v1/unknown"))
        assertNull(routes.resolve("/internal/v1/profiles/by-username/x"))
        assertNull(routes.resolve("/media/profile/avatars/x.jpg"))
        assertNull(routes.resolve("/app/assets/index-abc.js"))
    }
}
