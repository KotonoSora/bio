# Promo Codes - Document Context and Scope (feature 39)

Phase 1 BA · Feature 39 of 53 — monetize 8/9: coupon codes, promo campaigns, batches, and the credited-days ledger — every free or discounted day flows through the same ledger, gates, and audit trail as paid time. Sources: `monetize/promo-code/{campaign-management,code-generation,redemption-rules}.md`, `monetize/subscription/trials-promotions.md` (coupon + credit slices; the trials slice is feature 36's). Implemented by `docs/02-architecture-techlead/39-promo-codes/`.

## Context

Marketing intent becomes enforceable server-side guardrails here: every code in the wild traces to exactly one campaign with a spend ceiling and an off switch. Three coupon kinds exist (`percent`, `fixed_vnd`, `term_extend`); a fully-discounted (0 VND) order skips provider handoff entirely and grants directly through the canonical path with the reserved `provider='promo'` literal. The redemption contract is closed: exactly one coupon per order, an explicit stacking matrix, and oracle-free rejections. Term-extend days, referral awards, and goodwill land in one credited-days ledger applied by feature 36's lifecycle job, so support can always explain "why is this user pro" from ledger + audit alone.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Every code unique, scoped to one campaign, traced to a channel; unguessable format with ≥ 64 bits of entropy |
| OBJ-02 | Campaigns are budgeted, windowed, targeted, pausable; unbounded campaigns cannot exist |
| OBJ-03 | Redemptions decrement budgets atomically; exposure never exceeds the cap (DB-enforced); 80% alert, 100% auto-pause |
| OBJ-04 | Zero real charge surprises: 0-VND orders grant without touching any rail; every amount is recomputed server-side |
| OBJ-05 | Oracle-free validation: indistinguishable timing/copy except the one safe expiry hint |
| OBJ-06 | Credits (term-extend, referral seam, goodwill) are pro-days only — never cash, never transferable |
| OBJ-07 | Full attribution: campaign + channel stamped on orders; subscription `source` answers provenance |
| OBJ-08 | Leak response: one pause + bulk-void; redeemed discounts always stand |

## Scope

In scope (SC-01..SC-09): coupon validation + checkout repricing, the 0-VND promo path, campaign CRUD + lifecycle + burndown, batch generation + one-time export + void, the credited-days ledger + job application, goodwill ops issuance, attribution stamping, fraud signals + campaign auto-pause, the config-gated issuer caps.

Out of scope: the trial flow (36); referral invitation surfaces and vesting triggers (gated marketing major — the ledger's `referral` source is reserved and recorded here); partner payout/revenue-share (26's domain, not floating); receipt rendering (37 renders promo orders unchanged); accounting reports; P4/P5.

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Buyer | R: validation quotes, own credit balance; C: apply a code at checkout | A code simply works or cleanly doesn't; internals never leak |
| Marketing/ops (restricted role) | C/R/U: campaigns, batches, goodwill | Launches, watches burn-down, pauses leaks, exports once |
| Finance | R: burndown + redemption aggregates | Committed vs redeemed exposure visible before it matters |
| Fraud reviewer | R: signals + counts | Works from aggregates, never raw logs |
| System (job, auto-pause) | U: campaign statuses, credit application | Bounded automation, audited |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Campaign approval workflow (campaign Q1) | Single restricted ops role creates/launches in v1 (every action audited); finance sign-off above a threshold is deferred (recorded — no invented threshold) |
| Q-02 | Win-back definition (campaign Q2) | `winback` = the resolver says free at evaluation time (any lapsed/none account qualifies); no time-window variant in v1 (recorded) |
| Q-03 | Coupon kinds at launch (trials Q2) | All three kinds ship: `percent` (1..100), `fixed_vnd` (≥ 1, never exceeding the amount), `term_extend` (days via the credits ledger) |
| Q-04 | Issuer caps (trials FR3) | Goodwill cap + referral days live in `billing.promo` config with no invented defaults; absent config -> 409 `promo_config_absent` on those paths |
| Q-05 | Prefix registry (code-gen Q1) | Prefixes match `[A-Z2-9]{2,10}` and must be unique among non-ended campaigns; a global reservation registry is deferred (recorded) |
| Q-06 | QR/deep-link distribution (code-gen Q2) | Plain-code entry only in v1; deep links deferred (recorded) |
| Q-07 | Safe-hint boundary (redemption Q1) | Only expiry states specifics; `invalid|exhausted|not_eligible|already_redeemed` render one neutral message; timing normalized |
| Q-08 | Plan-page quote preview (redemption Q2) | Checkout-only in v1 (smaller oracle surface); the plan-page preview is deferred (recorded) |

## Constraints carried

Validate/create re-check eligibility server-side at every step; stored codes are salted hashes (plaintext exists once, at export); rejects never reveal which check failed (except expiry); no client-settable campaign, coupon, credit, or trial state exists; credits are days, never balances; 0-VND orders carry `provider='promo'` minted server-side only (clients sending it get `unknown_provider`); all money = integer VND.
