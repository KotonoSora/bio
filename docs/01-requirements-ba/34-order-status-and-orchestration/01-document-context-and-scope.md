# Order Status and Orchestration - Context and Scope (feature 34)

Phase 1 BA · Feature 34 of 53 — monetize 3/9. Legacy sources: `monetize/payment/{order-status-model,checkout-orchestration}.md` plus their six slice plans (folded). Realized by `docs/02-architecture-techlead/34-order-status-and-orchestration/`; kernel conventions from `docs/01-requirements-ba/32-monetize-overview/` (status vocabulary, no-default, lifecycle map) and the checkout contracts of `docs/01-requirements-ba/33-checkout-session/` are consumed, never re-decided.

## Purpose

Define the one canonical order lifecycle every rail shares — the nine-value status set, the legal-edge matrix, the actor authority per edge, and the verified-confirmation → grant step — plus the buyer-facing read side: pending poll, history, and order detail. Provider minors each map their native states onto this machine; this feature is the arbiter, so "is a late transfer dead?", "does a stuck 3DS order resurrect?", and "what can still happen to this order?" have one answer on all eight rails. It also owns order reads (including fulfillment status composed from feature 23) and the catalog delete guard read for feature 22.

## Objectives

| # | Objective |
|---|---|
| O-1 | One lifecycle: nine statuses, a closed legal-edge matrix, one authority per edge — identical on every rail. |
| O-2 | One grant path: verified confirmations only, amount-checked, idempotent, and audited inside the transition transaction. |
| O-3 | One buyer vocabulary: reads expose `{status, status_detail}` only; provider-native codes never surface. |
| O-4 | Late money is refused and logged, never granted; terminal states never resurrect. |
| O-5 | Order visibility: pending poll ≤ 200 ms p95; history/detail with fulfillment status (23) and receipt links (37); no PII crossing service lines. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Buyer (session) | R own orders (list/detail); C cancel only while `pending` (33's route) | One consistent vocabulary on every surface |
| Provider grant/sweep/ops handlers | T orders only along legal edges through the guard | Every rail shares one canonical lifecycle |
| Support/ops | R any order (32's trace + reads); C audited exception re-link (bank rails; 40's routes) | Knows precisely what can still happen to an order |
| Shop registrar (23/24/25) | R `order.granted.v1` (event) | Fulfillment/enrollment/booking starts exactly once |
| Attacker (adversarial) | none | Forged returns, replay storms, and IDOR gain nothing |

## In scope

Canonical enum + legal-edge matrix + actor authority; the `TransitionGuard` (row-locked, amount-checked, idempotent grants; audit appended in the same transaction); `order.granted.v1` emission with DLQ replay and reconciliation; order reads (owner list + detail; `merch` fulfillment composite from 23; title resolution from 22/35 with degrade); the `has-product-orders` internal read for 22's delete guard; late-money refusal + the audited bank-rail exception re-link (routes land with 40); the native-state mapping registry + conformance gate; the pending/return read contract; dispute edges + frozen UX; the killed-rail pending settlement rule (settle or expire, never auto-migrate).

## Out of scope

Quote/create/handoff/expiry sweep/cancel mechanics (33); rail verification, webhook routes, settlement sweeps, and the exception re-link routes (40); ledger rows and period math (35); receipts rendering and the guest magic-link view (37); refund execution and credit notes (38); promos (39); notification dispatch (32); earnings/analytics consumers (15 and the gated 48-53 majors read money events read-only).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 `disputed → paid` (dispute won) | The order edge re-fires `paid` and the buyer view restores; entitlement period math (restore original `period_end` vs extend by frozen duration) is 35's ledger domain — recorded as a forward seam; no finance value is invented here. |
| Q2 `partially_refunded` in v1 | No: v1 is full-refund-only (32's resolution, re-purchase path); refunds render as annotations on the original receipt (37/38); a partial status would invent finance scope. |
| Q3 `authorizing` cancel | Never in v1: the matrix allows `authorizing → paid|failed|expired` only; authorizing orders resolve by capture outcome or expiry (33's wording aligned this batch). |
| Q4 expired-state win-back copy | None: plain new-checkout CTA — restraint over conversion nudges on lapsed money. |
| Q5 dispatch/display-order sourcing | Consumed from 32 D-4 / 33 D-7: ops config; API order rendered verbatim when absent; no code favorite. |
| Q6 guest status/receipt reads | 37's signed single-order magic link; money reads stay session-scoped (33 Q3 consumed). |
