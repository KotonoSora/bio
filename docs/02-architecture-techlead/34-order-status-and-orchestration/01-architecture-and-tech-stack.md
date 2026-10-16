# Order Status and Orchestration - Architecture and Tech Stack (feature 34)

Phase 2 TL · Extension of `billing-service` (feature 32's service; conventions there). BA sources: `docs/01-requirements-ba/34-order-status-and-orchestration/`. Consumed seams: 33's `orders` row + sweep/cancel, 40's rail verification, 23's `jobs-by-order` read, 22's delete guard + `products:public` batch, 35's plan catalog (fixture until it lands). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED (platform-wide): the legacy monolith-guard verdict maps onto `billing-service`'s `status` module (`com.app.billing.status`) — guard + grant + audit commit in one transaction; no status service. The extraction doctrine applies reciprocally. | A split guard would put a network call inside the grant transaction (the double-grant window); no extraction triggers fire. |
| D-2 | The enum and legal-edge matrix are a code table (`TransitionGuard.ALLOWED`, nine values) mirrored by the V1 CHECK; the guard is the single writer of `orders.status` — 33's sweep and cancel map 1:1 onto guard calls (conditional updates, no contract change). Buyer projection: `{status, status_detail}` only. | One writer, one edge table, one audit chain; provider-native codes never leak. |
| D-3 | Grant path: 40's verified confirmation resolves the order → row lock → edge + actor + amount assertions → apply + audit + `psp_ref` first-write + (plan orders) the feature 35 ledger close/open in the caller's transaction; same plan+term renewals extend the current row instead (`max(now, period_end) + term`, 36's branch); when the order carries `proforma_id`, the same transaction flips 37's proforma to `converted` behind its status guard (first grant wins); coupon orders: 39's hook — eligibility re-check, guarded `uses`/`redeemed`, `term_extend` credits-ledger insert, `source='coupon'` + `source_ref=campaign_id` stamp, `coupon.in_flight_honored` when the campaign died mid-flight; terminal fast-path short-circuits idempotent replays before locking; mismatch → no grant + quarantine + ops alert (32's catalog; order lands `failed` via the rail sweep). | Atomic, amount-checked, replay-safe by construction; retry storms collapse at the guard. |
| D-4 | Emission: on grant, publish `order.granted.v1` to `billing.events` post-commit (Redis db 10; inventory addition), at-least-once; the same post-commit step enqueues 37's receipt email (`billing.receipt`, idempotent key `rcpt:{orderCode}`); in-process bounded retry → `billing.events.dlq` + alert; the emitter appends a `grant.emitted` audit marker, and a reconciliation re-emits recent paid grants lacking the marker (consumers are idempotent). Grants never wait on sends. | Success without send is money stranded from fulfillment; the marker makes self-heal mechanical. |
| D-5 | Reads: owner session + RLS; list is a cursor page; detail composes 23's `jobs-by-order` (status fields only; degrade inline) and resolves titles via 22's `products:public` batch / 35's in-service catalog (degrade to fallback labels); support uses 32's trace route; no sessionless reads (37's magic link reuses the projection). | One read model, three consumers (buyer, support, guest-via-37); none of them block money. |
| D-6 | Late money: refused in the guard + `transition.late` metric; audited exception re-link exists only via 40's bank-rail ops routes (`ROLE_OPS` + reason); card captures landing post-cancel route to that rail's settlement refund path. No automatic re-link path exists (asserted by absence). | Automation here would resurrect dead money; the audited exception is the only door. |
| D-7 | Native-state mapping: per-rail code tables live with 40's adapters; this feature owns the contract template + the conformance hookup. Non-terminal native states map to "transition nothing"; the 8×N matrix is green before any rail change ships. | The map is the arbiter against drift ("is a late SePay transfer linkable?"). |
| D-8 | Dispute/degrade seams: dispute edges fire from verified settlement events (40) with 38's records; entitlement freeze/revoke and period math are 35's ledger; degrade rules (23/22/DLQ/35-absent) per TL 05. | Order-side truth stays here; money-effect truth stays with its owner. |

## Component map

| Piece | Home | Note |
|---|---|---|
| `TransitionGuard` (enum, edges, authority, fast-path) | `billing-service` | single status writer; called by grant/sweep/cancel/ops |
| Grant path | `billing-service` | amount check, `psp_ref`, audit in-transaction; wired to 40's verifiers |
| Order reads (list/detail/projection) | `billing-service` | owner + RLS; `{status, status_detail}`; degradation inline |
| Fulfillment composite | `billing-service` → shop 23 | status fields only; timeout → `degraded` |
| Delete guard read | `billing-service` | `has-product-orders`; bounded; service JWT |
| Event emitter + DLQ + reconciliation | `billing-service` | `billing.events` / `billing.events.dlq`; marker-driven re-emit |
| Mapping registry + conformance hookup | `billing-service` (CI job) | template + per-rail rows (40); artifact per release |

## Boundaries

Owns: statuses, legal edges, actor authority, guard, grant step, `order.granted.v1` emission, order reads (list/detail/composite/delete guard), late-money refusal, mapping contract + conformance gate, dispute edges on the order side. Does not own: creation/quote/handoff/expiry sweep/cancel mechanics (33); rail verification, webhook routes, settlement sweeps, exception re-link routes (40); ledger rows/period math (35); receipts/magic link (37); refund execution (38); promos (39); notification dispatch (32).

## Consumers

| Consumer | Contract |
|---|---|
| FE pending/history/detail | order reads + the BA 04 state map (polling contract from 33) |
| shop 23/24/25 | `order.granted.v1` (frozen shape; dedupe on consumer keys) |
| shop 22 | `GET /internal/v1/orders/has-product-orders/{productId}` delete guard |
| support/ops | reads + 32's trace route; exception re-link via 40 |
| feature 37 | reuses the `{status, status_detail}` projection for the guest magic link |
| feature 15 | orders read APIs (earnings traces, read-only) |
