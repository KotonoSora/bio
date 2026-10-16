# Monetize Overview - Context and Scope (feature 32)

Phase 1 BA · Feature 32 of 53 — monetize 1/9. Legacy sources: `monetize/overview/*` plus the `monetize-foundation` delivery plan (folded; deleted with this batch). Realized by `docs/02-architecture-techlead/32-monetize-overview/`, which registers `billing-service` — the one service hosting features 32-40 (same-change rule, baseline `01-architecture-and-service-topology`).

## Purpose

Give implementers, reviewers, and support one map of the money domain: which providers exist (eight, closed), which shared minors own the cross-provider contracts, and the no-default rule that binds every rail. Provider mechanics belong to the rails feature (40); the lifecycle every rail shares (selection, session, statuses, security, ledger, plans, receipts, refunds, promos) is owned by the features ahead. No flow may assume a provider when none was chosen.

## Objectives

| # | Objective |
|---|---|
| O-1 | One registry, eight equal rails: `payos`, `sepay`, `vnpay`, `zalopay`, `napas`, `google-pay`, `apple-pay`, `stripe` — no rail is a default, a fallback, or a favorite anywhere. |
| O-2 | One uniform money lifecycle for every rail: quote → create → handoff → verified confirmation → entitlement → receipt → lifecycle → refund via the originating rail only. |
| O-3 | Conformance before breadth: a rail ships only with its full row of the cross-rail conformance matrix green — all eight behave identically on the shared contract. |
| O-4 | Ops control without deploys: eligibility matrix, display order, and per-rail kill switches flip in seconds, audited, never in code. |
| O-5 | Evidence by `orderCode`: support, ops, and finance trace any order across provider references, ledger rows, receipts, and audit — replayable end to end. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Buyer | C own quotes/checkouts; R own orders/plan; C own refund requests, cancel | one consistent experience on any eligible rail |
| Ops | C config flips (eligibility/display/kill switches, reason required); R audit | operates rails without deploys, fully audited |
| Support/finance | R orders/ledger/receipts/audit; C refund approve/execute (38) | traces any `orderCode` end to end |
| Implementer (indirect) | none | finds the owning feature for any money question — no guessing |

## In scope

The closed eight-rail registry (code-owned); the no-default contract (create requires `provider`; refunds read the stored one; display order from ops config only); the uniform lifecycle map; canonical status vocabulary (legal edges owned by feature 34); config surfaces (eligibility matrix, display-order profile, per-rail kill switches — versioned, audited, ≤ 5 s effective); the conformance harness (all eight rails × the shared drill row); the money API shape (`orderCode` int64, RFC 9457 codes, additive-only); the audit/evidence spine; secrets posture; the notification pipeline (transactional class — shared by leads confirms 27, booking notices 25, lifecycle notices 36, receipts 37).

## Out of scope

Rail handoff/verify/sweep mechanics (feature 40); checkout session and selection internals (33); status transitions and dispatch (34); ledger, plans, catalog (35); lifecycle jobs (36); receipts and invoices (37); refunds execution (38); promo codes (39). Growth majors (marketing, affiliate, advertising, sales, data-analysis, user-behavior) are gated backlog 48-53 — they consume money events, never move money. Org skeleton/teams: agency major (45-47). Shop, publish, automate surfaces stay with their features.

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 Stripe-native recurring | One-shot everywhere in v1: every renewal is an explicit re-purchase through the uniform lifecycle; Stripe native recurring stays deferred (feature 36 may revisit by spec amendment once international demand is proven). Consistency across rails beats per-provider automagic. |
| Q2 display-order sourcing | Static ops config only, audited flips; API order is rendered verbatim when config is absent. Experiment-driven ordering is deferred (belongs to the gated data-analysis major). |
| Q3 partial refunds | Full-only at launch + re-purchase; partial ledger entries deferred to a refunds (38) amendment if support data demands. |
| Q4 `recommended` labeling | Position-only in v1: no badges, colors, or labels beyond list order unless the server payload supplies them; client-side favoritism is a grep-verified violation. |
| Q5 pending poll cadence | 3 s with error backoff, paused on `document.hidden`; adaptive cadence deferred to scale work. |
| Q6 pending TTL | Per-rail value with a global default 30 min; bank-transfer-class rails (`sepay`, `napas`) configured longer (24 h) in the same config surface — no code branch per rail. |
| Q7 keyless fingerprint dedupe window | 90 s (keyed idempotency never expires; the window only catches keyless double-clicks). |
