# Affiliate Tracking, Program, and Payouts - Architecture and Tech Stack (feature 49) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/49-affiliate/` (7 files, gated). GATED: design-complete only — no component below exists in any image, bundle, or migration until a spec amendment approves it (BR-01, AC-01). Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`. Money posture: attribution stamps into billing's `orders.extras` (additive, 32's contract); outbound money moves only through the finance ops payout flow (batch instruction + dual control, same discipline as manual bank refunds in `docs/02-architecture-techlead/40-payment-rails/`).

## Service boundary (future): `affiliate-service`

When built: new service `affiliate-service` (port 8092, `affiliate_db`, Redis db 13) — clicks, attributions, enrollment, commission ledger, and payout batches are affiliate's own data, kept out of the money kernel's database. Billing's checkout calls the resolver at order creation; billing's events drive the ledger; the service never touches orders, refunds, or grants directly.

| Component (at amendment) | Change | Notes |
|---|---|---|
| `RedirectHandler` (new) | `GET /r/{code}` → fast redirect + signed cookie + click row | Cached affiliate lookup; deep-links preserved; p95 ≤ 100 ms |
| `AttributionResolver` (new) | Internal resolve call from checkout: code beats cookie, most recent click wins, direct unattributed | Winning id + evidence ref stamped at order creation; fixed pre-payment |
| `ClickIngest` (new) | Click rows with ip/ua hashes; stuffing signals rejected; rate budgets | Never the record of truth — evidence only |
| `EnrollmentService` (new) | Application intake, manual approval, status ladder, terms versions | No auto-approval code path exists |
| `CommissionLedger` (new) | pending → confirmed → payable → reversed transitions from billing events | Rate snapshot per order; integer VND; rounding favors affiliate + logged |
| `PayoutBatchService` (new) | Monthly freeze → instruct → execute → reconcile; statements | Dual control; batch-line idempotency; failures open exactly one case |
| `EvidenceStore` (new) | Immutable evidence bundles per attribution | Dispute replay in one view |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | New `affiliate-service`; `affiliate_db`; Redis db 13 for redirect caches + rate state | Money-to-strangers compliance stays separable; money kernel untouched |
| D-2 | Attribution fixed at order creation via the internal resolver; `orders.extras` carries `{affiliateId, evidenceRef}` | 32's extras is additive; no order row is ever re-pointed |
| D-3 | Cookie is a pointer; server-side click rows are the evidence; windows counted to the second server-side | Clearing cookies never breaks disputes, and never auto-credits either |
| D-4 | Rates/tiers/thresholds/caps are versioned config (`affiliate.rates`, `affiliate.payout`), finance-owned; absence refuses activation (`rates_incomplete`) | No invented finance values; per-order rate snapshots make changes forward-only |
| D-5 | Commission ledger derives from billing's `order.granted.v1` + refund/dispute outcomes; no direct reads of billing tables | Events, not cross-database access (baseline doctrine) |
| D-6 | Payouts execute via the finance ops flow: batch instruction file + dual-control approval + reconciliation; batch-line idempotency | Same discipline as manual bank refunds; no new rail integration in v1 |
| D-7 | Payout details vault-encrypted; tax fields collected at enrollment; annual summaries produced, rates/forms finance/legal-owned | Never improvise tax treatment in code |
| D-8 | Terms versioned with acceptance timestamps; adverse material changes trigger re-acceptance; conduct ladder with notice + appeal | Due process is specified before the first dispute, not after |

## Cross-edits (all at amendment, not now)

| Target | Edit (at amendment) |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | new `affiliate-service` row (8092, `affiliate_db`, Redis 13) |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | checkout resolver seam + event consumption rows (extras field documented additive) |
| `docs/02-architecture-techlead/40-payment-rails/` | payout discipline citation (manual bank refund flow reused) |
| `MIGRATION.md` | feature 49 row (doc migration landed with this batch; build at amendment) |
