# Promo Codes - Step-by-Step Implementation Plan (feature 39)

Phase 2 TL · Order matters: schema, pricing service, checkout wiring, grant hook, job passes, ops surfaces, drills. Package `com.app.billing.promo`. BA sources: `docs/01-requirements-ba/39-promo-codes/`.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V6__promo_codes.sql` per `02-database-schema-design.md` + drift tests (cap race, guards, RLS probes) | 38's V5 applied | V6 drills 1-6 green |
| T-02 | BE | `CouponPricingService`: normalize + HMAC lookup + single evaluation path + quote math (all three kinds) + normalized timing | T-01 | AC-05, AC-06 |
| T-03 | BE | Validate + credits-read endpoints (buyer surface, Turnstile for guests, throttles) | T-02 | AC-06; registry rows live |
| T-04 | BE | Checkout wiring (33): pricing delegation, `discount_vnd` storage, 0-VND mint branch (`provider='promo'`, handoff `none`, immediate grant), compensation from terminal transitions | T-02 | AC-04, AC-14 |
| T-05 | BE | Budget discipline: guarded decrement/create, restore-on-terminal-once, 80% alert, 100% auto-pause audit | T-04 | AC-02, AC-14 |
| T-06 | BE | Grant hook (34's txn): eligibility re-check, guarded `uses`/`redeemed`, `term_extend` ledger insert, `source='coupon'` stamping, in-flight honored audit | T-04 | AC-03, AC-07, AC-12 |
| T-07 | BE | Campaign admin: CRUD + lifecycle transitions + burndown + audit spine | T-01 | AC-01, AC-13 |
| T-08 | BE | Batch admin: atomic generation (hash-only storage), one-time export + ack, void-unredeemed, stats, invite binding | T-01 | AC-08, AC-09 |
| T-09 | BE | Credits admin: goodwill issuance (config-gated), ops ledger read | T-01 | AC-10 (goodwill half) |
| T-10 | BE | Job passes in 36's job: campaign windows, credit application (guarded consume), coupon expiry summary | T-06, T-09 | AC-10, AC-03 (window half) |
| T-11 | BE | Abuse signals: velocity metrics + campaign auto-pause (system actor); no auto-bans | T-05, T-07 | AC-13 |
| T-12 | FE | Checkout code field + math line + replacement confirm; credits row on the billing page | T-03, T-04 | AC-05, AC-11 UI |
| T-13 | FE | Ops console: campaign lifecycle + burndown, batch generate/export/void/stats, goodwill form | T-07, T-08, T-09 | Ops flows complete |
| T-14 | BE/DevOps | Cross-edits per `01-architecture-and-tech-stack.md` (32 chain/config/registry, 33 rules, 34 hook lines, 36 job lines) | T-04..T-10 | Diff review green |
| T-15 | BE/DevOps | Drills: cap race, pause latency, oracle timing matrix, single-use race, 100k batch, void, compensation rerun, job idempotency | T-05..T-14 | AC-01..AC-14 complete |
| T-16 | DevOps | Metrics + alerts (burn 80/100, auto-pauses, validate outcome classes, in-flight honored, credits applied) + CI greps (DDL owner, no ad-hoc discount writes, no client `promo`) | T-11..T-15 | NFR-18, NFR-04 |

## Sequencing notes

1. T-01 first: the cap CHECK and guarded update are the exposure floor; nothing prices before they land.
2. T-04 is the only writer of `discount_vnd`/`amount_vnd` drift — review it as money code with 33's owner.
3. T-06 touches 34's grant transaction: land it with T-14's 34 cross-edit so the spec and code move together.
4. T-10 extends 36's job — same advisory lock, separate batches, idempotent reruns; never reimplement period math (call 36's write path).
5. Rollout: no feature flag. Campaigns simply do not exist until ops creates one; the validate endpoint ships dark-safe (no live campaign -> neutral rejections).
6. Referral surface stays unbuilt (gated); `referralDays` config key ships with `goodwillMaxDays` so the later surface has no backlog dependency on this feature.

## Definition of done

AC-01..AC-14 green in CI; full-tree validation (word caps + backticked path resolution) green; greps: single DDL owner, no ad-hoc discount math outside `promo`, no plaintext code persistence, no client-settable `promo` provider, no DELETE grants on promo tables; `MIGRATION.md` row 39 flips to `done` at the batch tail with `monetize/promo-code/*` deleted.
