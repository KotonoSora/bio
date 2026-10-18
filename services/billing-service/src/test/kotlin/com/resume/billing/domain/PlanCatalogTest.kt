package com.resume.billing.domain

import com.resume.billing.config.CatalogProperties
import com.resume.billing.config.CatalogValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Feature 03 drills from the plan file: yearly math to the exact VND (AC-03,
 * BR-04), absence semantics (AC-09, BR-03), and the profiles.limit
 * registration conformance (AC-05, BR-10, D-3).
 */
class PlanCatalogTest {

    private fun catalog(
        monthlyVnd: Long? = null,
        yearlyDiscountPct: Int? = null,
        trialDays: Int? = null,
    ): PlanCatalog = PlanCatalog(CatalogProperties().apply {
        this.monthlyVnd = monthlyVnd
        this.yearlyDiscountPct = yearlyDiscountPct
        this.trialDays = trialDays
    })

    @Test
    fun `yearly math is half-up over the whole year, never per-month compounding (AC-03)`() {
        val c = catalog(monthlyVnd = 59_000, yearlyDiscountPct = 15)
        // 59,000 x 12 = 708,000; 15% = 106,200; yearly = 601,800.
        assertEquals(601_800, c.view().prices?.yearlyVnd)

        // A rounding case: 7% of 69,999 x 12 = 839,988 -> discount 58,799.66..
        // floors half-up at the half-dong mark per the contract fixture.
        val odd = catalog(monthlyVnd = 69_999, yearlyDiscountPct = 7)
        assertEquals(781_189, odd.view().prices?.yearlyVnd)
    }

    @Test
    fun `no discount configured renders a plain 12x monthly (BR-04)`() {
        val c = catalog(monthlyVnd = 59_000)
        val prices = c.view().prices
        assertEquals(708_000, prices?.yearlyVnd)
        // Absence, never "0%" (AC-09).
        assertEquals(null, prices?.yearlyDiscountPct)
    }

    @Test
    fun `unset prices mean no prices serve - names and claims only (AC-09, TL 05)`() {
        val view = catalog().view()
        assertEquals(null, view.prices)
        assertEquals(null, view.trialDays)
        assertEquals("free", view.tiers.first().id)
        assertTrue(view.tiers.all { it.claims.isNotEmpty() })
    }

    @Test
    fun `absent config is honored independently - badge and trial CTA omit independently`() {
        val noPct = catalog(monthlyVnd = 59_000, trialDays = 7).view()
        assertEquals(708_000, noPct.prices?.yearlyVnd)
        assertEquals(null, noPct.prices?.yearlyDiscountPct)
        assertEquals(7, noPct.trialDays)

        val noDays = catalog(monthlyVnd = 59_000, yearlyDiscountPct = 44).view()
        assertEquals(44, noDays.prices?.yearlyDiscountPct)
        assertEquals(null, noDays.trialDays)
    }

    @Test
    fun `profiles limit registers free 1 and pro unlimited (BR-10, D-3)`() {
        val c = catalog()
        assertEquals(mapOf("profiles.limit" to "1"), c.capabilities("free"))
        assertEquals(mapOf("profiles.limit" to "unlimited"), c.capabilities("pro"))
        assertEquals(emptyMap<String, String>(), c.capabilities("unknown"))
    }

    @Test
    fun `two tiers only, plainly named (BR-01)`() {
        val view = catalog().view()
        assertEquals(listOf("free", "pro"), view.tiers.map { it.id })
        assertEquals(listOf("Free", "Pro"), view.tiers.map { it.name })
    }
}

/** NFR-05 boot drill: invalid catalog refuses startup; the 44 ceiling accepts. */
class CatalogValidationTest {

    private fun props(yearlyDiscountPct: Int? = null, monthlyVnd: Long? = null, trialDays: Int? = null) =
        CatalogProperties().apply {
            this.yearlyDiscountPct = yearlyDiscountPct
            this.monthlyVnd = monthlyVnd
            this.trialDays = trialDays
        }

    @Test
    fun `discount above 44 percent refuses boot`() {
        assertThrows<IllegalArgumentException> { CatalogValidator.validate(props(yearlyDiscountPct = 45)) }
    }

    @Test
    fun `the 44 ceiling accepts`() {
        CatalogValidator.validate(props(yearlyDiscountPct = 44))
        CatalogValidator.validate(props(yearlyDiscountPct = 0))
    }

    @Test
    fun `unset values are valid - absence is the design`() {
        CatalogValidator.validate(props())
    }

    @Test
    fun `non-positive trial days refuse boot`() {
        assertThrows<IllegalArgumentException> { CatalogValidator.validate(props(trialDays = 0)) }
        assertThrows<IllegalArgumentException> { CatalogValidator.validate(props(trialDays = -3)) }
    }

    @Test
    fun `negative prices refuse boot`() {
        assertThrows<IllegalArgumentException> { CatalogValidator.validate(props(monthlyVnd = -1)) }
    }

    @Test
    fun `positive days and prices accept`() {
        CatalogValidator.validate(props(monthlyVnd = 59_000, yearlyDiscountPct = 44, trialDays = 7))
    }

    @Test
    fun `version carries a truthy id for cache keys (G4)`() {
        val view = PlanCatalog(CatalogProperties()).view()
        assertFalse(view.version.isBlank())
        assertTrue(view.version.isNotBlank())
    }
}
