# Affiliate Tracking, Program, and Payouts - Document Context and Scope (feature 49) - GATED

Phase 1 BA · Feature 49 of 53 — gated backlog 2/6. GATED: design-complete only — no table, route, job, or UI string ships until a spec amendment approves it (BR-01, AC-01). Sources: `affiliate/tracking-attribution.md`, `affiliate/affiliate-program.md`, `affiliate/commission-payout.md`. Implemented (future) by `docs/02-architecture-techlead/49-affiliate/`.

## Context

Three coupled scopes: exactly which affiliate gets credit for which paid order (signed links, cookie + code binding, windows, precedence, reversal); who may earn and on what terms (enrollment, tiers, conduct ladder); and how confirmed attributions become money (commission ledger, payout cycle, bank rail, tax boundary). Referral pro-day credits reward users with pro-days; influencer codes attribute batches — neither pays cash; this feature is where money-to-strangers begins, so eligibility, rates, and termination rules are specified up front instead of improvised per dispute. The future host is `affiliate-service` (port 8092, `affiliate_db`, Redis db 13); attribution stamps into billing's `orders.extras` at checkout via an internal resolve call, and money moves out only through the finance ops payout flow (batch instruction + dual control). Nothing here is built while gated.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Every credit decision reproducible from stored evidence (click id, code, timestamps, rate snapshot, order ref) |
| OBJ-02 | Fixed pre-payment attribution: the winning affiliate stamps the order at creation and is never re-pointed |
| OBJ-03 | Closed commission lifecycle: pending → confirmed → reversed, with refunds/disputes proportional and auditable |
| OBJ-04 | Enrolled, approved affiliates only; versioned tiers and terms; conduct ladder with notice + appeal |
| OBJ-05 | Boring payouts: frozen rate snapshots, monthly cycle + threshold, dual-control bank rail, statements per cycle |
| OBJ-06 | Buyer price integrity (margin-funded, never surcharged) and first-party privacy (no third-party pixels) |

## Scope

In scope (at amendment, SC-01..SC-06): referral links + cookie/code binding; attribution windows, precedence, evidence bundles; enrollment + tiers + terms; commission ledger + payout batches + statements; anti-gaming + exposure cap; tax-field collection + annual summaries.

Out of scope: referral pro-day credits (their own feature); influencer code supply (39 — consumed as codes, not redefined); agency billing (46); accounting/revenue-recognition reports; e-wallet/foreign payout rails (named future work with compliance review); sub-affiliate tiers (MLM-shaped structures explicitly excluded).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Affiliate | R own stats/earnings/statements; U own profile + payout details | Enrolls, shares links/codes, earns on referred paid orders |
| Referred buyer | none (normal checkout) | Buys at normal prices; no visible tracking friction beyond standard consent |
| Program operator | C/R/U affiliates (approve/suspend/terminate, tiers) | Runs the program; answers "where is my commission" |
| Finance | R ledger + batches; C payout batches (dual control) | Books expense off the same ledger; withholds/reports only as counsel directs |
| Dispute reviewer | R evidence bundles + ledger | Replays any attribution from stored evidence |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Attribution window (tracking Q1) | 30-day default, config-owned (`affiliate.windowDays`); counted from server-side click timestamps to the second |
| Q-02 | Click-log retention (tracking Q2) | Raw click logs follow the analytics raw-event TTL precedent (one aligned value, owned by 12's privacy section — not restated here), then aggregation-only; evidence bundles follow order retention |
| Q-03 | Launch rates and tiers (program Q1) | Finance-owned, versioned like the plan catalog (`affiliate.rates`); absence refuses activation (`rates_incomplete`) — no rate is invented here |
| Q-04 | Commission base (program Q2) | Decided once with finance (net-of-fees vs gross); the chosen basis version is stamped in the rate snapshot and applied uniformly — no per-order improvisation |
| Q-05 | Payout threshold and cycle (payout Q1) | Monthly cycle + config-owned minimum threshold (`affiliate.payout`); launch values set with finance at the amendment; sub-threshold balances roll forward indefinitely, never expire without notice |
| Q-06 | Withholding/reporting (payout Q2) | Finance/legal-owned: the platform collects required identity/tax fields at enrollment and produces annual summaries; rates, forms, and filing are never improvised in code |
| Q-07 | Sub-affiliates | Excluded in v1; affiliates are independent referrers, not employees or resellers |

## Constraints carried

Buyers are never surcharged (referred checkout totals identical to direct for the same cart); attribution is fixed pre-payment and never re-pointed; per-order rate snapshots make tier changes forward-only; integer VND throughout with rounding favoring the affiliate and logged per line; payout details encrypted at rest (vault discipline, never logged); no auto-approval code path exists; no silent clawback from bank — post-payout reversals are notified debits against future payables.
