# Order Status and Orchestration - Database Schema Design (feature 34)

Phase 2 TL · Zero-DDL feature: `orders` + `audit_log` + `ops_config` are owned by `docs/02-architecture-techlead/33-checkout-session/02-database-schema-design.md` (V1); anti-replay `provider_events` is owned by feature 40 (V7 — chain map in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`). This file owns the transition semantics over those rows. BA sources: `docs/01-requirements-ba/34-order-status-and-orchestration/` (BR-01..BR-05, BR-13).

## Transition contract (over `orders`)

- Enum parity: `TransitionGuard.ALLOWED` (nine values) must equal the V1 CHECK alphabet — a parity test fails the build on drift; `psp_ref` is first-write-wins (`COALESCE(psp_ref, :ref)`).
- Atomic write: `SELECT … FOR UPDATE` on `order_code` → assert edge ∈ `ALLOWED[from]`, actor authorized, amount checked → `UPDATE orders SET status=:to, updated_at=now() WHERE order_code=:id AND status=:from` — rowcount 1 or the caller loses to a concurrent terminal outcome (never a blind write).
- Terminal fast-path: replays of terminal outcomes return success-without-effect without taking the lock, before any assertion (retry storms collapse here, not in rail code).
- Audit contract: every transition appends `audit_log{actor, action='order.transition', target=orderCode, detail={from,to,eventId,rail}, at}` in the same transaction; the grant append is the canonical grant timestamp for receipts (37). Plan orders additionally call the feature 35 ledger writer (`plan.granted`/`plan.superseded` audits, or `plan.renewed` when 36's extension branch matches; plus `proforma.converted` when the order carries `proforma_id`; coupon-bearing orders run 39's confirmation hook — `coupon.redeemed` / `coupon.in_flight_honored` audits) in the same transaction. The emitter appends `action='grant.emitted'` post-publish; the reconciliation predicate is "paid transition rows with no `grant.emitted` row for the same target within 24 h".
- Single writer: no module outside the guard writes `orders.status` in production code (CI grep + review); 33's sweep/cancel are guard callers — their landed conditional updates map 1:1, no contract change.

## Reads and indexes

| Read | Serving plan |
|---|---|
| `GET orders` history | `idx_orders_user (user_id, created_at DESC)` from V1; cursor on `(created_at, order_code)` |
| `GET orders/{orderCode}` | PK lookup; RLS owner or ops context |
| Guard lookups | PK + unique provider ref (`psp_ref` resolves via rail intake; V6 owns the unique event keys) |
| Expiry/settlement sweeps | `idx_orders_expiry` (V1, partial on `pending|authorizing`) |
| `has-product-orders` | scans `product_id` (unindexed by design at v1): delete-path rarity + 5 s statement timeout; scale trigger recorded — if p95 > 200 ms or `orders` > 10 M rows, a `product_id` index lands with the next migration window (baseline 07) |

No new config surfaces: eligibility/display/kill-switch reads stay on 32's `ops_config` surfaces; this feature adds only the Redis stream inventory (`billing.events`, `billing.events.dlq` — TL 05).

## Assertions

| Assertion | Check |
|---|---|
| Zero-DDL | `grep -rn "CREATE TABLE" docs/02-architecture-techlead/34-order-status-and-orchestration/ docs/01-requirements-ba/34-order-status-and-orchestration/` returns nothing |
| Single DDL owner | `CREATE TABLE orders` appears exactly once (33's V1); `provider_events` exactly once (40's V6) |
| Enum parity | guard alphabet == CHECK alphabet (test) |
| Audit coverage | no status write without an audit row (test) |

## Drills

| Drill | Asserts |
|---|---|
| Edge matrix | full cross-product × 8 rail mappings: legal edges fire, illegal refuse |
| Race | cancel vs confirm, double webhook, webhook + sweep → one terminal outcome, one chain |
| Fast-path | redelivery needs no lock; fast-path replay p95 ≤ 5 ms |
| Late refusal | zero grants on `cancelled|expired`, all logged with event ids |
| RLS | foreign session reads zero rows; ops context reads for support; guest rows invisible |
| Delete guard | exists/absent paths + timeout 503 semantics with 22 |
| Reconciliation | marker-less paid grants re-emit once; consumers dedupe |

## Retention and deletion

Audit rows 24 months (32's spine). This feature adds no tables → no new purge duty; the billing deletion row in `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md` (landed with 33) already covers `orders`/`audit_log` scrubbing.
