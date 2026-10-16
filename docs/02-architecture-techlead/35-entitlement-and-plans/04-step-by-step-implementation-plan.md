# Entitlement and Plans - Step-by-Step Implementation Plan (feature 35)

Phase 2 TL · Ordered build for `docs/02-architecture-techlead/35-entitlement-and-plans/`. Follow T-01..T-11 in order; acceptance goals cite `docs/01-requirements-ba/35-entitlement-and-plans/06-acceptance-criteria.md` and the drills in `docs/02-architecture-techlead/35-entitlement-and-plans/02-database-schema-design.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE (billing) | Apply `V2__entitlement_ledger.sql` (five tables + `orders.plan_term_version` + RLS + grants + seeds); run the fresh-apply and seed drills | - | Drills: fresh apply, zero paid price, single DDL owner |
| T-02 | BE (billing) | Ledger writer + resolver + free-floor materialization + audit rows (`plan.granted`, `plan.superseded`) in `com.app.billing.ledger`; current-row concurrency and resolver-matrix drills | T-01 | AC-01, 03, 04, 05 |
| T-03 | BE (billing) | Catalog repo + yearly derivation + public `GET /api/v1/plans` serving 03's frozen shape + `billing.catalog` config read (<= 44% validation) in `com.app.billing.catalog` | T-01 | AC-06, 08, 10 (catalog side) |
| T-04 | BE (billing) | Ops routes (draft/activate/retire/list) + role checks + reason + audit + guards (partial-unique, pendings drain) | T-03 | AC-07, 13 (ops side) |
| T-05 | BE (billing) | Credits kernel: counter/event writes, lazy window materialization, conditional debit, one-return rule, internal debit/return routes in `com.app.billing.credits`; debit-race and return-bounds drills | T-01, T-02 | AC-09, 10 |
| T-06 | BE (billing) | `GET /internal/v1/billing/plan/{userId}` (fresh resolve; in-service `<= 5 s` cache cleared on writes) + `current-prices`; land the 01 cross-edit | T-02 | AC-12 |
| T-07 | BE (resume) | Tailor endpoint + AI adapter + debit/return calls + sanitization (fixed prompt, JD cap, escaped suggestions, 30 s budget) in `com.app.resume.ai` | T-05 | AC-11, 14 |
| T-08 | BE (33/34 wiring) | `term` on plan quote/create + `orders.plan_term_version` stamp (33) + grant transaction calls `grantPaidPlan` (34); land those cross-edits | T-02, T-03 | AC-01, 06 |
| T-09 | FE | Plan page + `PlanBadge` states + quota meter + tailor workspace + upgrade prompts + ops catalog console; SPA types mirror the DTOs | T-03..T-07 | AC-02, 03, 11 |
| T-10 | QA/DevOps | Run all TL 02 + TL 05 drills; metrics (`ledger.grant`, `plan.missing_row`, `ai.debit`, `ai.return`, `tailor.latency`); labeled fixtures for tests; conformance rows for the eight-rail grant visibility | T-01..T-09 | AC-01..14 |
| T-11 | Docs | Cross-edits (01/03/26/32/33/34 files, baseline 01, `MIGRATION.md` rows) + legacy source deletion at the batch tail + full validation | T-01..T-10 | Validation green (word caps + path resolution) |

## Build notes

- T-02's writer is the only path that opens/closes rows; direct `subscriptions` writes are grep-verifiable defects (CI gate, same class as the single-DDL-owner drill).
- T-03/T-04 run under the ops context; owner paths under `app.user_id`; the grant and lifecycle actors use the ops context (33's precedent).
- T-07 keys every debit by the request id (33's idempotency posture); the return call is attempted once per failed generation and its own failure is a metric, never a retry loop.
- T-10's fixtures are labeled and test-database-only: no seed ships a price (BR-21).
