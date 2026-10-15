# Creator Revenue Share Gated Database Plan (earnings ledger — designed, not built)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/creator-revenue-share.md`. Consumed by the revenue-share backend plan (computation, cycles, clawbacks; splits next to this file). Counted events live in the serving-measurement aggregates (owned there); rate models follow the inventory pricing decision (owned there). No migration — nothing is created until the plan update.

## Purpose

Hold the buildable earnings-ledger schema: estimated→confirmed→paid→clawed_back lines off filtered counts only, shared payout-batch partitioning with affiliates, and the clawback proportionality rule. Exactly the future `CREATE TABLE`s, written once at plan update; zero tables now. Every dong traces to a counted, fraud-filtered event — quarantined traffic never earns, even if later cleared (cleared traffic re-enters aggregates, not earnings, keeping payouts dispute-proof).

## Users

- Backend developer (future): runs these scripts once at plan update; earnings only from filtered aggregates.
- QA (now): absence drill; (future) earnings/clawback/farming/shared-runner drills.
- Creator/finance (future): reads estimates vs confirmed; books expense off the same pattern as affiliates.

## Functional requirements

1. Present state: zero earnings tables, zero payout endpoints for creators. Absence verified per release.
2. Table `ads.creator_ad_earnings` (future) — one row per creator per day per flight: `day`, `profile_id`, `flight_id`, `amount_vnd`, `status` (`estimated|confirmed|paid|clawed_back`), `rate_version` (rate snapshot stamp — rate changes apply to future events only, never rewrite history). PK `(day, profile_id, flight_id)` (idempotent recompute key — re-running estimation converges). Estimates accrue from filtered aggregates only (quarantine-excluded traffic is invisible here by construction — no code path reads quarantine rows for earnings, asserted in review).
3. Confirmation point (designed, decided at plan update per Q1): flight/invoice close OR fixed monthly close flips `estimated → confirmed` (one rule for all flights — mixed close rules would make statements unexplainable). `paid` flips only on payout-rail confirmation (bank-confirmed, not batch-sent — sent-but-unconfirmed money is not earnings history yet).
4. Clawback rule: post-close invalid-traffic findings move ONLY the tainted share to `clawed_back` with linked evidence + creator notice (proportional — legitimate earnings untouched and still payable; silent mass-clawbacks are the trust-killer this rule forbids). Never rewrites history rows (status flip + reason, append-only discipline like refunds' credit notes).
5. Shared payout batches: ONE `payout_batches` runner with earner-type partitioning serving affiliates + creators (table owned once in the affiliate commission-payout slice — cross-referenced, never redefined; this slice adds `earner_type='creator'` rows through the same runner). Per-cycle statements per earner, no cross-visibility (affiliate never sees creator lines and vice versa — partition enforced in queries + RLS, tested).
6. Rate versioning: rev-share rate config effective-dated rows (owned by catalog discipline); events stamped with applicable version (rate_query_at_compute, not at payout — payout uses stamped history, never current rates).
7. Plan interplay: ad earnings never gate features; gates never force ads (free and pro share one formula — no plan-based rate discrimination in v1, tested by identical computation paths); payout thresholds identical across plans.
8. Self-farming: creator inflating own slots voids tainted earnings (lines → `clawed_back` with farming reason) + warn-then-restrict ladder up to opt-in revocation (ladder owned by the backend plan; legitimacy history preserved — punishment is precise, not scorched-earth).

## Data / API touches

- DDL now: none — assertion: pre-update tags show zero earnings/payout tables for creators (schema + source grep). Future: `creator_ad_earnings` per rule 2 + shared `payout_batches` (owned once in the affiliate slice) + rate-version rows. RLS (future): creators read own lines/statements; batch execution restricted to finance-ops role.

## Acceptance criteria

- Absence drill (now): zero creator-earnings surface every release.
- Earnings drill (at build): event→aggregate→ledger→statement reconciles end to end; close flips confirmed; payout matches statements.
- Clawback drill (at build): tainted share only, with notice + evidence; legitimate payable intact.
- Farming drill (at build): tainted voided, ladder triggered, legitimate history preserved.
- Shared-runner drill (at build): one audited batch, correct per-earner statements, zero cross-visibility.
- Auth drill (at build): cross-creator 403 + RLS deny.

## Out of scope

- Computation/cycles/clawback flows (backend plan); earnings screens (frontend plan); inventory/safety/counting (sibling plans); affiliate commissions (separate earner type, shared runner); promo; creator analytics; third-party ad-tech; P5.

## Open questions

- Q1: Confirmation point — flight close vs monthly close (BA Q1)?
- Q2: Creator payout threshold — affiliate floor or lower for early opt-in (BA Q2)?
