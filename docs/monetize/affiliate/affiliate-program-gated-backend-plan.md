# Affiliate Program Gated Backend Plan (enrollment + tiers + ladder — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/affiliate/affiliate-program.md`. Storage contract in the program database plan above (affiliates + tiers; nothing created now). Screens in the program frontend plan (splits next to this file).

## Purpose

Hold the buildable server design for program membership: audited manual-approval enrollment, versioned tiers with next-month stepping, attribution-basis enforcement, the conduct ladder with appeal, and the exposure-cap brake. Until the plan update, this file's only executable content is the absence gate.

## Users

- Backend developer (future): ships enrollment + tiers + ladder in package `com.app.aff.program` (GATED).
- QA (now): asserts absence; (future) enrollment/tier/conduct/cap/price drills.
- Affiliate/operator (future): enrolls, tiers up, appeals with written notice.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith program module (package `com.app.aff.program`, GATED). Monolith pros: enrollment + tier + terms-acceptance share one transaction + audit; tier computation reads order history in-process. Microservices cons avoided: a program service for application-volume traffic with no ≥10x asymmetry. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, data-jpa, security, actuator, micrometer. Load posture: enrollment application-paced; tier evaluation monthly batch; no hot paths in this slice.
2. Step 1 — enrollment (GATED): public application intake (Turnstile-guarded: stranger-intake is bot-shaped) with identity + payout details + channel description → `applied` → manual ops approval ONLY (no auto-approve transition exists in the state machine — unskippable by construction, not by config) → `active` with attributable links/codes. Suspension freezes attribution, holds balances; termination forfeits tainted-only with written notice + appeal window logged before forfeiture executes.
3. Step 2 — tiers (GATED): config-driven versioned rates; volume evaluation monthly (threshold crossed mid-month steps NEXT month's rate; current-month attributions keep prior rate — per-order rate stamps make this auditable, not trustable); rate changes notify (material adverse changes require re-acceptance before applying to the affiliate).
4. Step 3 — attribution-basis gate (GATED): only paid plan grants + paid product orders commission (trials/0-VND/cancelled/expired/self-purchase filtered at attribution, owned by the tracking slice; this slice defines the rule, tracking enforces it — single definition, referenced).
5. Step 4 — conduct ladder (GATED): signals (cookie-stuffing, brand-bidding, spam, self-referral, misrepresentation) → warn → suspend → terminate-upheld, each step written-noticed with appeal window; forfeiture executes only after appeal lapses/fails, scoped to tainted earnings (legitimate history needs separate review to touch — two-step destruction, never one click).
6. Step 5 — economics brake (GATED): exposure-cap monitor per period; breach pauses new-accrual review (queue + notice), earned balances untouched and visible.
7. Step 6 — abuse: Turnstile on intake; per-IP application throttles (mass-application fraud is the signup attack); payout-details encrypted at write (never logged, even on validation failure — redaction at the DTO boundary).
8. Step 7 — gate enforcement NOW: zero affiliate tables/endpoints/jobs in current images (grep + migration audit fails the tag on any `affiliates|affiliate/|commission_tiers` surface).

## Data / API touches

- Schema: database plan above (none now). Endpoints (GATED, absent from current OpenAPI): public application intake (Turnstile); affiliate self-service (profile, links/codes, read-only earnings); ops approve/suspend/terminate + tier changes (audited). No affiliate-settable rates anywhere (no such endpoint exists to audit — absence asserted).

## Acceptance criteria

- Absence drill (now): zero affiliate surface pre-update.
- Enrollment drill (at build): manual-approval path only; auto-approval unrepresentable.
- Tier drill (at build): next-month stepping with per-order stamps.
- Conduct drill (at build): ladder with notice + appeal; tainted-only forfeiture.
- Price drill (at build): referred == direct totals (margin-funded proof).
- Cap drill (at build): brake pauses review, balances intact.
- Auth drill (at build): cross-affiliate 403 + RLS deny.

## Out of scope

- Table/tier/terms contracts (database plan above); affiliate screens (frontend plan); link/code/windows (tracking plans); computation/payouts (commission plans); referral credits; influencer batches; agency; P5.

## Open questions

- Q1: Launch rates flat vs tiered (see database plan Q1)?
- Q2: Net vs gross base (see database plan Q2)?
