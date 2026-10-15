# Entitlement Ledger (single source of plan truth)

PLAN ref: P3-3 (decides the open "or a `subscriptions` view if introduced" line in `plans/subscription-plans.md`). Every provider grant writes here; every gate reads here; nothing else is plan truth.

## Purpose

Define the one entitlement record all eight provider paths write and all feature gates read: a `subscriptions` ledger row per user anchoring current plan, period bounds, and status. Provider webhooks today each promise to "write the subscription/entitlement row" without that row being specified anywhere — this doc specifies it, so gates, downgrades, renewals, and audits share one consistent view instead of re-deriving plan state from scattered `orders` rows.

## Users

- Gated feature code (domain attach, realtime analytics, premium templates, AI generation): asks "what is this user's effective plan right now" and gets one answer.
- Provider grant handlers (all `monetize/*` provider webhook docs): record paid outcomes here after their atomic order transition.
- Support/ops: reads a user's plan history to resolve "I paid but I'm still free" tickets.

## Functional requirements

1. Table `subscriptions{id, user_id, plan, status, period_start, period_end, provider, order_id?, cancelled_at?}` with standard `user_id TEXT NOT NULL` + index + RLS. `plan` ∈ `free|pro`; `status` ∈ `active|grace|cancelled|expired`. Exactly one row per user is `active|grace` at any time (partial unique index); history rows persist for audit.
2. Write rule: only grant handlers (post-atomic-order-transition) and the lifecycle job (billing `subscription-lifecycle.md`) may write; product-only orders (`product_id` set, no `plan`) never touch this table. Each write appends `audit_log{actor: "system", action, target: user_id, at}`.
3. Read rule: every gated check resolves effective plan from the single current row (`active`, or `grace` within its window), never from `orders`, cookies, or client claims. `GET /api/v1/plan` returns `{plan, status, period_end, quotas}` from this row.
4. Grant mapping: a paid plan order closes any current row (status → superseded, kept for history) and opens the new row with `period_start = grant time`, `period_end` per the plan term (see lifecycle doc), `provider` + `order_id` recorded for traceability.
5. Free tier is explicit: every user holds a `free/active` row from signup (no NULL-plan states anywhere), so gates never branch on row absence.
6. AI credit ledger (`ai_generations`) keys off the same `user_id` + period window as the current row; plan changes mid-period do not retroactively alter consumed credits.

## Data / API touches

- DDL for `subscriptions` (+ partial unique index on current rows, FK `order_id → orders`). All PG via `worker/db/*`; RLS: users read only their own rows; grant/lifecycle writers use the least-privilege app role.
- Read path: `GET /api/v1/plan` (owner-scoped). No direct client writes exist — there is no `PUT /api/v1/plan`; plan changes happen only through paid grants, lifecycle transitions, and audited ops actions.
- KV fast-path (optional, mirrors AI-credit pattern): cache effective plan per user with short TTL for hot gates; PG row is the reconciled truth on every dispute.

## Acceptance criteria

- Two paid plan orders in sequence: exactly one current row, full history preserved, `GET /api/v1/plan` reflects the latest within the grant SLA.
- Product-only purchase: zero `subscriptions` writes; plan reads unchanged.
- Gate probe across all eight providers' grants: each paid plan grant is visible to every gate (domain, realtime, templates, AI) within the SLA — no provider-specific lag or second source.
- Free-from-signup: brand-new user has a `free/active` row; no gate ever encounters a missing row (NOT NULL + signup hook verified).
- IDOR probe: user A reading user B subscription rows returns 403; RLS denies cross-user rows at DB.
- Concurrent grants (double webhook + sweep racing): single current row, single audit chain — no split-brain plans.

## Out of scope

- Periods, renewals, grace, and downgrade execution (see `billing/subscription-lifecycle.md`); receipts and billing history UX (see `billing/invoices-receipts.md`).
- Provider checkout/webhook internals (their own minors); plan matrix and AI caps (`plans/subscription-plans.md` — this ledger implements its FR6); agency seats (`organizations/`); P4/P5.

## Open questions

- Q1: Plan terms in v1 — monthly only, or monthly + yearly (yearly changes `period_end` math and refund exposure)?
- Q2: Multi-seat/agency billing later — does the ledger stay per-user with org links, or gain org-owned rows in P4?
