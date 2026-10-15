# Affiliate Commission Payout Gated Backend Plan (computation + cycles — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/affiliate/commission-payout.md`. Storage contract in the commission database plan above (commissions + shared batches; nothing created now). Screens in the frontend plan (splits next to this file).

## Purpose

Hold the buildable server design for boring payouts: frozen-snapshot computation, state machine with reversal discipline, audited monthly batches with idempotent confirms, VN bank-rail execution with dual control, and the tax boundary. Until the plan update, this file's only executable content is the absence gate.

## Users

- Backend developer (future): ships computation + cycle runner + reversals in package `com.app.aff.payout` (GATED).
- QA (now): asserts absence; (future) computation/cycle/reversal/duplicate/tax drills.
- Affiliate/operator/finance (future): paid predictably, books cleanly.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith payout projector (package `com.app.aff.payout`, GATED). Monolith pros: computation reads confirmed attributions in-process; batch freeze→confirm shares one transaction baseline with the ledger; one audit chain. Microservices cons avoided: a payout service for monthly-batch cadence with no ≥10x asymmetry. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, data-jpa, security, scheduling, actuator, micrometer. Load posture: computation per confirmed attribution (event-paced, trivial); cycle runs monthly (bounded batch); statements generated from ledger rows (no live money math at payout).
2. Step 1 — computation (GATED): on attribution confirmation, commission = order net × stamped rate snapshot (history-frozen; multi-item/partial-refund net proportionally per line; integer VND; rounding favors affiliate + logged per line). `attribution_id` UNIQUE makes double-computation impossible (second attempt violates, maps to success-without-effect — not an error path).
3. Step 2 — state machine (GATED): `pending → payable` (refund window passes clean) → `in_payout` (batch freeze) → `paid` (rail confirmation) | `failed` (bounded auto-retry with backoff, then exactly one ops case). Reversals: `pending`/`payable → reversed` with linked refund/dispute ref; `paid` → ops debit against future payables (affiliate-notified, audited — the only post-payout money movement, and it moves forward-ledger, never backward-bank).
4. Step 3 — monthly cycle (GATED): threshold gate (per Q1; sub-threshold rolls forward, never expires silently) → freeze eligible → generate bank instructions + per-earner statements → dual-control execute → reconcile confirmations → mark `paid`. Tax-paperwork gate at batch entry (incomplete → blocked with fix-it notice, earnings intact). Confirm re-run is a no-op (idempotent); double-instruct while frozen impossible (state-guarded, tested).
5. Step 4 — VN bank rail (GATED): batch instruction file + dual-control approval via existing finance ops flow (same discipline as manual bank refunds — one money-out discipline, not two). No new rail integration (e-wallet/foreign need compliance review first).
6. Step 5 — statements + support (GATED): per-cycle statements (lines, rates, reversals, threshold progress, payout ref) dashboard + email; missing-payout flow starts at statement id (traceability by artifact, not memory).
7. Step 6 — gate enforcement NOW: zero commission/batch tables/endpoints/jobs in current images (grep + migration audit fails the tag on any `commissions|payout_batches|affiliate/earnings|ops/payout` surface).

## Data / API touches

- Schema: database plan above (none now). Endpoints (GATED, absent from current OpenAPI): affiliate earnings + statements reads; ops batch freeze/instruct/confirm (audited). All financial actions append `audit_log`.

## Acceptance criteria

- Absence drill (now): zero commission/payout surface pre-update.
- Computation drill (at build): snapshot-frozen math; proportional partials; favoring rounding logged.
- Cycle drill (at build): full cycle with matching statements/emails; single-line failure isolated with one case.
- Reversal drill (at build): forward-debit only, notified, chained.
- Duplicate drill (at build): confirm re-run no-op; double-instruct impossible.
- Tax drill (at build): paperwork gate with intact earnings; summaries producible.

## Out of scope

- Commission/batch contracts (database plan above); earnings/statement screens (frontend plan); enrollment/tiers (program plans); link/code/windows (tracking plans); creator lines (same runner); referral credits; influencer batches; agency; accounting reports.

## Open questions

- Q1: Threshold + cycle values (see database plan Q1)?
- Q2: Tax rules + forms (see database plan Q2)?
