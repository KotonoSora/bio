# Entitlement and Plans - Document Context and Scope (feature 35)

Phase 1 BA · Feature 35 of the monetize major (32-40), after checkout (33) and order orchestration (34). Sources: `monetize/billing/entitlement-ledger*.md`, `monetize/subscription/plan-catalog.md`, `monetize/plans/subscription-plans.md`, `monetize/plans/plan-purchase-options.md`. Implemented by `docs/02-architecture-techlead/35-entitlement-and-plans/`; checkout and orchestration price and grant against it.

## Context

A verified payment must become durable, single plan truth, and every gated surface must read one consistent answer. Checkout (33) quotes plan intents and order orchestration (34) records payment, but plan state itself lives nowhere yet: this feature adds the entitlement ledger (one current subscription row per user), makes plan truth versioned catalog data (`plans`, `plan_terms`) that ops can change without deploys and without breaking in-flight orders, projects the effective plan into the feature 01 entitlement snapshot so every gate sees it within the 30 s bound, and operates the AI JD-tailor credit wallet the plan matrix specifies.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | One plan truth: exactly one current subscription row per user (`active`/`grace`); grants close/open rows atomically inside 34's grant transaction |
| OBJ-02 | Free floor is structural: every user resolves to a plan row; gates never branch on absence or read orders/cookies for plan state |
| OBJ-03 | Versioned catalog: plan, term, price, and AI-quota changes are immutable rows; publication flips never rewrite stamped orders |
| OBJ-04 | Gates see the effective plan through the feature 01 snapshot within the `<= 30 s` consumer bound; billing unreachable fails closed to free |
| OBJ-05 | AI credits: per-period kind-keyed counters; atomic, keyed-idempotent debit; return on failed generation; no rollover |
| OBJ-06 | Ops changes prices/quotas with audit and without code deploys (flips effective `<= 5 s`) |
| OBJ-07 | Support answers "why does this user have pro" from the ledger + audit chain (32's order trace) |
| OBJ-08 | Zero finance values invented: prices, discount depth, and quotas are ops/finance-authored data |

## Scope

In scope (SC-01..SC-10): subscription ledger + writer + resolver; free-floor materialization; `plans`/`plan_terms` catalog incl. ops draft/activate/retire lifecycle; public pricing read (feature 03's frozen shape); plan-term pricing wired into 33's quote/create; entitlement projection for the 01 snapshot; AI credit wallet (counters + events + debit/return internal contract); the AI JD-tailor surface (input, gating, quota, output contract), executed in resume-service; plan status read; cross-edits in 33/34.

Out of scope: checkout mechanics/handoff/expiry (33); order status/grant step (34); grace, renewals, cancel execution, reminders, downgrade execution at period end (36 — consumes this ledger); receipts (37); refunds (38); promos/coupons (39); rail webhooks/PSP verification (40); design-assist build (gated feature 16 — its `design-assist` credit kind is reserved here); org seats (agency major 45-47); preference center (gated 48-53).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Free user | R: own plan status, quotas | Default floor; gates prompt upgrade |
| Pro subscriber | R: own plan status, period, quotas; U: cancel (36) | Purchased plan; gates open |
| Ops operator | C/R: term drafts; U: retire; R: catalog state | Drafts and retires; cannot activate |
| Finance operator | U: activate terms (reason mandatory) | Only role that puts a price live |
| Support agent | R: plan status + trace (32's route) | Answers plan questions read-only |
| Gated feature code | R: effective plan via the 01 snapshot only | Never reads ledger tables directly |
| Resume-service tailor | C: credit debits/returns (internal calls) | Owns no credit state; billing is the wallet |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Ledger: monthly + yearly only? | Both are real terms; no other terms in v1; new plans/terms are spec amendments, never config |
| Q-02 | Multi-seat/org rows? | Per-user rows in v1; org seats belong to the agency major (45-47); no org columns stubbed |
| Q-03 | Catalog: yearly discount model | Config-owned depth (`yearlyDiscount`, validated `<= 44%`), applied when a yearly term is drafted; stored in the immutable yearly row; no per-campaign override in v1 |
| Q-04 | Catalog: rounding/currency | VND integers only; derivation to the VND (half-up); zero floats anywhere |
| Q-05 | Purchase: Stripe native recurring? | Off in v1 (32's resolution): all eight rails are one-shot; renewal is a fresh checkout |
| Q-06 | Purchase: yearly on wallet rails | Identical offer on all eight rails; ops eligibility config may disable cells; never a code path |
| Q-07 | Purchase: per-rail renewal reminders | 36's lifecycle owns reminders and their then-active price reads |
| Q-08 | Plans: AI output shape | Suggestions only in v1 (no auto-apply into the resume); apply is a later editing feature |
| Q-09 | Plans: downgrade with excess data | Lossless: nothing deleted or hidden; creates blocked by the limit; pro-only serving stops per owning feature (03 BR-07) |
| Q-10 | Plan purchase intent shape | `plan` + explicit `term`; no defaults; `free` is never purchasable |

## Constraints carried

SSO-only sessions; deny-by-default + RLS on every ledger table; VND integers; money state transitions server-side only (34's guard); audit spine 24 mo; no finance values in code (grep gate); prices/quotas change only by new immutable rows; secrets via environment/vault.
