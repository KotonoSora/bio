# Affiliate Tracking, Program, and Payouts - Contracts and Payloads (feature 49) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/49-affiliate/`. GATED: none of these routes may appear in OpenAPI, the gateway, or any image until the spec amendment (AC-01). Reuse contracts: `docs/02-architecture-techlead/39-promo-codes/` (code supply), `docs/02-architecture-techlead/40-payment-rails/` (payout discipline), `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` (events + notification enqueue).

## Endpoints (reserved, absent until the amendment)

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /r/{code}` | public | Fast redirect (cached lookup), signed cookie set, click row; deep-link preserved; p95 ≤ 100 ms |
| `POST /api/v1/affiliate/apply` | public + Turnstile | Application intake; audited; status `applied` |
| `GET /api/v1/affiliate/stats` | affiliate session | Clicks/conversions/pending/confirmed; aggregates + own referrals only |
| `GET /api/v1/affiliate/earnings` | affiliate session | Per-line states + threshold progress |
| `GET /api/v1/affiliate/statements/{id}` | affiliate session | Per-cycle statement; matches the email copy |
| `PUT /api/v1/affiliate/profile` | affiliate session + CSRF | Payout/tax fields (vault-encrypted); never rendered back in full |
| `POST /api/v1/affiliate/links/rotate` | affiliate session + CSRF | Rotates the random suffix after a leak |
| `GET /api/v1/ops/affiliates` | ops role | Queue + roster with statuses |
| `POST /api/v1/ops/affiliates/{id}/approve\|suspend\|terminate` | ops role | Ladder with notice + appeal log; audited |
| `PUT /api/v1/ops/affiliates/tiers` | ops role | Versioned config append; effective-from semantics; no retroactive rewrites |
| `GET /api/v1/ops/affiliates/{id}/evidence` | ops role | Evidence bundle (click id, code, timestamps, rate snapshot, order ref); read-only |
| `POST /api/v1/ops/payout-batches` | finance-ops role | Freeze + instruct; dual-control approval; instruction file + statements generated |
| `POST /api/v1/ops/payout-batches/{id}/confirm` | finance-ops role | Reconcile confirmations; idempotent; failures open exactly one case each |
| `POST /internal/v1/affiliate/resolve` | service JWT | Checkout resolver: signals in → `{affiliateId?, evidenceRef?, source}` out; no affiliate-writable fields exist |

```kotlin
data class ResolveRequest(val cookieRef: String?, val promoCode: String?, val buyerUserId: String)
data class ResolveView(val affiliateId: String?, val source: String, val evidenceRef: String?)
data class AffiliateStats(val clicks: Long, val conversions: Long, val pendingCount: Long, val confirmedCount: Long)
data class EarningLine(val id: String, val amountVnd: Long, val state: String, val linkedReference: String?)
data class PayoutBatchView(val id: String, val period: String, val status: String, val lineCount: Int,
  val totalVnd: Long, val failedLines: Int)
```

## Event consumption (billing.events → affiliate-service)

| Event | Use |
|---|---|
| `order.granted.v1` | Attribution `pending`; `confirmed` once past the refund window |
| Refund/dispute outcome events (38/40 catalog) | `reversed` with the linked reference; post-payout reversals become notified debits |

No new events are emitted in v1; statement emails enqueue via 32's notification contract. Consumers are idempotent by event id (baseline).

## Rules

- The resolver is the only path that credits an affiliate; checkout calls it at order creation; the winning id + evidence ref stamp `orders.extras` — fixed pre-payment, never re-pointed.
- Precedence is closed and server-enforced: code beats cookie; most recent qualifying click wins among cookies; direct is unattributed; declined consent is unattributed (checkout never blocked).
- No affiliate-writable attribution or rate field exists anywhere; rates/tiers/thresholds/caps are ops/finance config appends.
- Payout execution is dual-control; batch-line idempotency makes retries and re-confirms no-ops; failures never block the batch.
- Evidence bundles are immutable; every adverse action carries notice + appeal trail.
- No buyer PII, payout secrets, or cross-affiliate data in any payload; payout details never serialize back in full.

## Error catalog (reserved)

| Code | Status | UI Behavior |
|---|---|---|
| `rates_incomplete` | 409 | Ops: set rates before activating |
| `attribution_expired` | 200 (no credit) | None — silent by rule; evidence retained |
| `self_referral_void` | 200 (no credit) | Affiliate: self-referrals don't earn |
| `stuffer_flagged` | - | Ingest reject + terms-ladder warning |
| `tax_fields_incomplete` | 422 | Fix-it notice; earnings intact |
| `batch_frozen` | 409 | Double-instruct impossible while frozen |
| `cross_affiliate_denied` | 403/404 | "Not found." |
| `terms_reacceptance_required` | 409 | Review updated program terms |
