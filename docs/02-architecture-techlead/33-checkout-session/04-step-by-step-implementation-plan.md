# Checkout Session - Step-by-Step Implementation Plan (feature 33)

Phase 2 TL · Acceptance goals cite `docs/01-requirements-ba/33-checkout-session/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V1__billing_orders.sql`: orders + audit_log + ops_config, indexes, RLS, grants, seeds | Feature 32 T-01 | TL 02 drills green |
| T-02 | BE | Pure quote: plan/product pricing, coupon validation, fee preview, `quoteToken` HMAC, zero-write tests | T-01; 22/26 seams | AC-04 (identity half) |
| T-03 | BE | Create pipeline: validation ladder, price freeze, fee snapshot, single-txn insert, handoff stub adapter | T-02 | AC-04, AC-06 |
| T-04 | BE | Idempotency: key UNIQUE + fingerprint compare + 90 s keyless window; replay returns same handoff | T-03 | AC-05 |
| T-05 | BE | Expiry sweep (conditional update, ≤ 5 min) + late-money refusal handoff to 34's guard | T-01 | AC-07 |
| T-06 | BE | Cancel route: conditional update, idempotent already-cancelled, `cancel_conflict` | T-01 | AC-08 |
| T-07 | BE | Selection: eligible computation + capability filter + `payment-methods` DTO + display order | T-01 | AC-02, AC-03 |
| T-08 | BE | Guest path: `guest:` minting, email validation, RLS probes; internal create for shop 25 | T-03 | AC-10, AC-11 |
| T-09 | BE | Lead opt-in writer (post-commit, bounded retry) + audit wiring for create/handoff/expire/cancel | T-03 | BR-13, BR-15 |
| T-10 | FE | Checkout + pending pages per BA 04 (reuses 32 components; no new shared widgets) | T-07 | BA 04 states render |
| T-11 | DevOps/QA | Drills: matrix, identity, double-submit, validation, expiry, cancel race, return forgery, IDOR/guest, container pair, log greps | T-04..T-10 | AC-01..AC-12 |

## Notes

- Plan intents run against the plan catalog owned by feature 35 — realized: active `plan_terms` rows with explicit `term`; fixtures remain test-database-only and the create path is unchanged.
- The stub provider adapter exists for drills only; production rails are 40's adapters behind the same `next{kind,payload}` contract — do not special-case rails here.
- The sweep and verified grant are the only transition authorities (BR-16); the return page and webhook routes never write.
- Guest rows are created through the same pipeline — no forked code path; the only branches are id minting and email validation.
- Logging must be greppable: no email, no ship-to, no full URLs beyond host in logs (S-9/S-10).
