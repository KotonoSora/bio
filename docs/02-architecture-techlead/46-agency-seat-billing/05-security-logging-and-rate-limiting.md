# Agency Seat Billing - Security, Logging, and Rate Limiting (feature 46)

Phase 2 TL · BA sources: `docs/01-requirements-ba/46-agency-seat-billing/` (BR-01..BR-12, NFR-01..NFR-11). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. Money surface: every control rides the existing 32–40 machinery — nothing parallel.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: contact/owner RLS on billing reads; managers/clients have no route (by absence); ops reconciliation context only | AC-06 IDOR + RLS probes |
| S-2 | Money integrity: zero new money tables/ledgers; amounts only in existing orders/invoices/ledger rows; grants idempotent (32–40 machinery) | Diff grep + duplicate-callback drill |
| S-3 | Math integrity: one half-up VND implementation (`SeatChargeMath`); fixture parity to the VND; no fractional amounts exist | AC-02 + grep gate |
| S-4 | Evidence integrity: snapshots append-only for closed days (no UPDATE/DELETE path); corrections open-day only, reasoned + audited; invoice lines immutable | AC-04, AC-08 |
| S-5 | Price integrity: catalog-owned values only; absent → `catalog_incomplete`; version stamped per line; no defaults or discounts invented | AC-07 + config audit |
| S-6 | Provider integrity: explicit `provider` at checkout (no default rail); webhooks/grants via 33/34 verification | `provider_required` drill |
| S-7 | Transfer integrity: dual confirmation, 7-day expiry, one pending per org; single-sided attempts inert + audited; contact flip atomic with audit | AC-03 |
| S-8 | No-hostage invariant: lapse/cancel/delete freeze staff tooling (45 gates) and never touch client surfaces; enforced by 45's ownership model | AC-05 |
| S-9 | Audit: subscriptions, snapshots, true-ups, transfers, cancels, corrections append to `audit_log` (24 months; ids + amounts, never instruments) | Audit completeness drill |

## Rate limits (gateway + app; Redis db 10 `ag:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| Overview | 300/h/org | 429 `throttled` |
| Subscribe / cancel | 20/h/org | 429 |
| Transfer (initiate/confirm/cancel) | 10/h/org | 429 |
| History / invoices reads | 300/h/org | 429 |
| Ops reconciliation reads/corrections | 120/h/operator | 429 |
| Internal entitlement read | 600/h/service | 429 |

## Degrade matrix

| Failure | Behavior |
|---|---|
| 45's seats route down | Snapshot job retries; no fabricated rows; alert after 2 misses; same-day backfill on recovery |
| Provider rails down | Standard 36 dunning; entitlement unchanged until grace end |
| 33's front door down | Subscribe refuses honestly; nothing half-created exists |
| 39's credit path down | Negative true-up queues as a pending credit (retried); invoice still renders the delta line |
| Redis down | Rate limits degrade to conservative fixed windows; reads and jobs unaffected |
| Notification pipeline down | Lapse/transfer notices queue with backlog; state truth unaffected |

## Audit and observability

Audits (24-month): `agency.subscribed`, `agency.cancelled`, `agency.state_changed`, `seat.snapshot_missed`, `seat.reconciled`, `trueup.settled`, `transfer.initiated/confirmed/completed/expired`. Metrics: `seats{org}` (aggregate), `snapshot_job{result}`, `trueup_vnd`, `mismatches`, `transfers{state}`, `lapses`. Zero instrument or token labels.

## Support answers

"Why was I charged X?" → one-hop trace: line → snapshots → roster days; closed periods immutable. "Why can't staff work?" → lapse state + cure path; client pages confirmed live. "Who pays now?" → billing contact state + transfer trail (both confirmations). "Why can't I subscribe?" → catalog state (`catalog_incomplete`) with support path; no invented prices exist. "Where's my refund?" → 38's machinery status; credits applied to the next renewal per policy.
