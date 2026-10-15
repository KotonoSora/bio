# Creator Revenue Share Gated Backend Plan (computation + cycles — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/creator-revenue-share.md`. Storage contract in the revenue-share database plan above (earnings ledger, shared batches; nothing created now). Screens in the frontend plan (splits next to this file).

## Purpose

Hold the buildable server design for creator ad earnings: computation off filtered counts, confirmation, shared payout cycles, clawbacks, farming ladder, and transparency minimums. Until the plan update, this file's only executable content is the absence gate.

## Users

- Backend developer (future): ships estimation, close, cycle runner participation, clawbacks in package `com.app.ads.earnings` (GATED).
- QA (now): asserts absence; (future) earnings/clawback/farming/runner drills.
- Creator/operator/finance (future): earns, operates, books off one ledger pattern.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith earnings projector (package `com.app.ads.earnings`, GATED). Monolith pros: estimation reads filtered aggregates in-process; close/cycle share the affiliate runner's transaction baseline. Microservices cons avoided: an earnings service for daily-grain projections with no ≥10x asymmetry. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, data-jpa, security, scheduling, actuator, micrometer. Load posture: daily estimation batch (not per-event); cycle runs monthly (bounded); statements generated per earner from ledger rows (no live computation at payout).
2. Step 1 — estimation (GATED): daily job projects filtered aggregates × stamped rate version → `estimated` lines (idempotent recompute by PK — reruns converge). Quarantine rows unreadable here (no import path exists — asserted by ArchUnit at plan update).
3. Step 2 — confirmation (GATED): close rule (flight or monthly, single rule) flips `estimated → confirmed`; `paid` only on rail confirmation. Thresholds + tax/identity gating shared with affiliate mechanics (same boundary: incomplete paperwork blocks batch entry with fix-it notice, earnings intact).
4. Step 3 — shared payout cycle (GATED): monthly, minimum threshold (per Q2), sub-threshold roll-forward (never expiring without notice); audited batch (freeze → instruct → execute → reconcile → `paid`); VN bank-rail transfers with dual control (same discipline as manual bank refunds); per-cycle statements (dashboard + email). One runner, earner-type partitioned; per-earner statements; no cross-visibility.
5. Step 4 — clawbacks + farming (GATED): invalid-traffic findings move tainted share to `clawed_back` with evidence + notice (proportional only); self-farming voids tainted + warn-then-restrict ladder to opt-in revocation (legitimate history preserved).
6. Step 5 — transparency minimums (GATED): estimated vs confirmed balances, per-flight breakdown, filter-exclusion counts affecting the creator's pages, threshold progress, payout history with references — "why did my earnings drop" answerable from dashboard data alone (clawback lines carry reasons + evidence links).
7. Step 6 — gate enforcement NOW: zero earnings tables/endpoints/jobs in current images (grep + migration audit fails the tag on any `creator_ad_earnings|creator-ads/` surface).

## Data / API touches

- Schema: database plan above (none now). Endpoints (GATED, absent from current OpenAPI): creator `GET /api/v1/creator-ads/earnings`, `GET /api/v1/creator-ads/statements/:id`, opt-in toggle (inventory slice); ops batch confirm + clawback actions (audited). All financial actions append `audit_log`.

## Acceptance criteria

- Absence drill (now): zero creator-earnings surface pre-update.
- Earnings drill (at build): end-to-end reconciliation; close flips; statements match payouts.
- Clawback drill (at build): proportional + noticed + evidenced; legitimate payable.
- Farming drill (at build): tainted voided, ladder triggered, history preserved.
- Shared-runner drill (at build): one batch, correct statements, zero cross-visibility.
- Auth drill (at build): cross-creator 403 + RLS deny.

## Out of scope

- Ledger/batch contracts (database plan above); earnings screens (frontend plan); inventory/counting; affiliate commissions (separate earner type); promo; creator analytics.

## Open questions

- Q1: Confirmation point (see database plan Q1)?
- Q2: Threshold floor (see database plan Q2)?
