package com.resume.account.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Feature 03 plan composition drills (AC-04: free plainly / pro with rows /
 * projection outage -> tier-only; S-1: the projection is only consulted for pro).
 */
class PlanServiceTest {

    private class FakeEntitlements(private val plan: String) : EntitlementSnapshotSource {
        override fun snapshot(userId: String) =
            EntitlementSnapshot(userId, plan = plan, capabilities = emptyMap(), resolvedAt = Instant.now())
    }

    private class FakeProjections(private val row: SubscriptionLifecycleView?, private val calls: MutableList<String> = mutableListOf()) :
        SubscriptionProjectionSource {
        override fun fetch(userId: String): SubscriptionLifecycleView? {
            calls.add(userId)
            return row
        }
    }

    private fun service(plan: String, row: SubscriptionLifecycleView?, calls: MutableList<String> = mutableListOf()): PlanService =
        PlanService(FakeEntitlements(plan), FakeProjections(row, calls))

    @Test
    fun `free renders plainly - the projection is never even consulted (AC-04, BR-03)`() {
        val calls = mutableListOf<String>()
        val view = service(
            "free",
            SubscriptionLifecycleView(status = "active", term = "monthly", periodStart = Instant.now(), periodEnd = Instant.now(), nextAmountVnd = 1),
            calls,
        ).plan("u1")
        assertEquals("free", view.plan)
        assertNull(view.subscription)
        assertNull(view.trial)
        // Tier-only means the projection call disappears completely for free.
        assertEquals(0, calls.size)
    }

    @Test
    fun `pro row maps additively with the derived trial state (TL 03)`() {
        val start = Instant.now()
        val end = start.plus(7, ChronoUnit.DAYS)
        val row = SubscriptionLifecycleView(
            plan = "pro",
            status = "trial",
            term = "monthly",
            periodStart = start,
            periodEnd = end,
            nextAmountVnd = 59_000,
        )
        val view = service("pro", row).plan("u1")
        assertEquals("pro", view.plan)
        assertEquals("trial", view.subscription?.status)
        assertEquals("monthly", view.subscription?.term)
        assertEquals(start, view.subscription?.periodStart)
        assertEquals(end, view.subscription?.periodEnd)
        assertEquals(59_000, view.subscription?.nextAmountVnd)
        assertNull(view.subscription?.graceEndsAt)
        assertEquals(7, view.trial?.days)
        assertEquals(end, view.trial?.endsAt)
    }

    @Test
    fun `grace row carries the additive graceEndsAt only in grace`() {
        val end = Instant.now()
        val row = SubscriptionLifecycleView(
            plan = "pro",
            status = "grace",
            term = "yearly",
            periodStart = end,
            periodEnd = end,
            graceEndsAt = end.plusSeconds(86_400),
            nextAmountVnd = 601_800,
        )
        val view = service("pro", row).plan("u1")
        assertEquals("grace", view.subscription?.status)
        assertEquals("yearly", view.subscription?.term)
        assertEquals(601_800, view.subscription?.nextAmountVnd)
        assertEquals(view.subscription?.periodEnd, view.subscription?.graceEndsAt?.minusSeconds(86_400))
    }

    @Test
    fun `projection outage degrades to tier only - rows absent, never invented (AC-04, S-9)`() {
        val view = service("pro", null).plan("u1")
        assertEquals("pro", view.plan)
        assertNull(view.subscription)
        assertNull(view.trial)
    }

    @Test
    fun `absent rows are not copied additively - non-trial statuses never derive a trial`() {
        val start = Instant.now()
        val end = start.plus(30, ChronoUnit.DAYS)
        val row = SubscriptionLifecycleView(
            plan = "pro",
            status = "active",
            term = "monthly",
            periodStart = start,
            periodEnd = end,
            nextAmountVnd = 59_000,
        )
        val view = service("pro", row).plan("u1")
        assertNull(view.trial)
        assertEquals("active", view.subscription?.status)
    }
}
