# Payment Rails - Architecture and Tech Stack (feature 40)

Phase 2 TL · Service: `billing-service` (port 8088, `billing_db`, Redis db 10). Implements `docs/01-requirements-ba/40-payment-rails/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| The eight intakes + wallet charge endpoints; `provider_events` / `rail_exceptions` / `dispute_cases` (V7); the sweeper; exceptions + dispute ops; per-rail response contracts; probe battery; refund capability members | Guard + statuses + `psp_ref` + late-money refusal -> 34; checkout/handoff/expiry vocabulary -> 33; refund execution + revocation helper -> 38; receipts -> 37; promo (`provider='promo'`) -> 39; notification pipeline + config flips -> 32 |

No new service; no new Redis keys (`bill:rl:*` reused).

## Rail conformance matrix (the signed contract per rail)

| Rail | Verify (frozen) | Replay key | Settle path | Sweep check | 38 refund member | Disputes |
|---|---|---|---|---|---|---|
| payos | HMAC-SHA256 over the canonical field string | rail order id | webhook grant | payment-link information | manual (evidence + two-party) | n/a |
| sepay | HMAC-SHA256 required (API-key/oauth/none forbidden) | rail txn id | content-ref + amount match, then grant | hourly re-scan (48 h lookback) | manual (bank return) | n/a |
| vnpay | HMAC-SHA512 over the query | `vnp_TransactionNo` | IPN grant (return is UX) | queryDr | API, query-first | n/a |
| zalopay | HMAC-SHA256 (`key2`) over the raw `data` | `zp_trans_id` | callback grant | order status query | API (merchant refund) | n/a |
| napas | bearer token, short-lived, server-refreshed | rail txn id | content-ref + amount match, then grant | check-transaction | manual (bank return) | n/a |
| google-pay | PSP-confirmed charge + PSP webhook scheme | PSP txn id | synchronous charge; webhook confirms | PSP status query | PSP refund API | PSP dispute events |
| apple-pay | merchant session (single-use, minutes TTL) + PSP-confirmed charge | PSP txn id | synchronous charge; webhook confirms | PSP status query | PSP refund API | PSP dispute events |
| stripe | `Stripe-Signature` `t`+`v1` over exact raw bytes | `event.id` | `payment_intent.succeeded` grant | retrieve PaymentIntent | Refunds API (idempotency key) | `charge.dispute.*` |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | `com.app.billing.rails`: one `RailVerifier` per provider (`verify(raw, headers) -> VerifiedEvent`), raw bytes only, constant-time HMAC compare, no JSON parse before verification; then `ProviderEventRecorder` (insert-first) → `OrderResolver` → 34's guard | One verification shape; oracle-free; replay-proof |
| D-2 | V7: `provider_events`, `rail_exceptions`, `dispute_cases`; no rail-ref map — the order code embeds in transfer content (33's template) and rail txn ids live in `provider_events` + `psp_ref` | Consolidates the legacy `napas_order_ref` map away; one reference story |
| D-3 | `RailSweeper` `@Scheduled(fixedDelay = 900000)`, per-rail `pg_try_advisory_lock`, batches ≤ 200, per-row transactions; settles only via the guard; on-demand verify is the same adapter | Shared cadence; no second writer; idempotent |
| D-4 | Exceptions: ops-only routes; `link` re-runs the atomic grant through 34's guard with `ROLE_OPS` + reason inside grace; `refund-record` = bank return evidence + second-party confirm; `dismiss` requires reason | Late money has exactly one door; fully audited |
| D-5 | Disputes: `dispute_cases` + guard edges; `disputed` freezes grants; loss → 38's revocation helper + `order.chargeback_reversed.v1`; win → `disputed → paid` | Deadline-driven, evidence-first; revocation reuses 38 |
| D-6 | Wallet charge: serialize per order, idempotency key = orderCode at the PSP; timeout holds `authorizing` + PSP status-query recovery; never a second charge attempt | Exactly-once charge semantics |
| D-7 | Kill switch: 32's `billing.kill_switches` via 32's audited route; delists new checkouts (33) and new wallet charges; intake, sweeps, verifies, and refunds are never blocked | Money already moving always settles |
| D-8 | Response contracts: `RailResponder` maps outcome → frozen per-rail codes (TL 03); buyer copy symmetric; refusal classes internal only | Rails get contract values; buyers learn nothing exploitable |
| D-9 | Config `billing.rails` {`sweepStaleMinutes` 30, `lateTransferGraceHours` 24} via 32's `ops_config` | Tunable thresholds, audited flips |
| D-10 | Secrets/certs via vault/env per provider; rotation dual-read ≥ 24 h; quarterly drill; secrets grep gate in CI | BR-14, NFR-09 |
| D-11 | Probe battery: per-rail CI conformance suite (fixtures) + compose drills; any red blocks the release; report archived per run | The `payment-security` baseline, mechanized |
| D-12 | `order.chargeback_reversed.v1` `{orderId, disputeRef, reversedAt}` — consumers 23/24 invalidate through their refund path | Frozen shape; terminal loss revokes claims |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md` | V7 chain row expanded to the three tables; `billing.rails` config row |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | Registry: wallet charge routes, ops rails routes; event catalog row `order.chargeback_reversed.v1` |
| `docs/01-requirements-ba/23-shop-fulfillment/03-workflow-and-user-journey.md` + 24's counterpart | One-line seam: the chargeback event reuses their refund invalidation path |
| 38 | No edits: strategy members are recorded in this matrix (satisfies 38's D-4 pointer) |
