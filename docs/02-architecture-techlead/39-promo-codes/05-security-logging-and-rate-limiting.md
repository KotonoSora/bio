# Promo Codes - Security, Logging, and Rate Limiting (feature 39)

Phase 2 TL · BA sources: `docs/01-requirements-ba/39-promo-codes/` (NFR-10..NFR-18, BR-09..BR-24). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: ops routes re-check the restricted role per request; credits owner-read via RLS; campaigns/coupons/batches have no buyer route at all (absence is the control); validate is endpoint-mediated and never enumerable (no counts, no hints) | AC-13; RLS probes |
| S-2 | Oracle resistance: one evaluation path, constant-work hash comparison, no early exits; copy identical across non-expired classes; timing spread <= 50 ms measured in CI | AC-06; timing matrix |
| S-3 | Code secrecy: storage = HMAC-SHA256 with `COUPON_HASH_KEY` (env/vault); plaintext exists once in the batch 201 and in the export ack audit metadata (count only); logs never contain codes or prefixes+codes together | AC-08; secret + log audits |
| S-4 | Exposure integrity: guarded decrement + `chk_campaign_cap` + advisory-lock per-user serialization + restore-once compensation; no code path writes counters outside `promo` | AC-02, AC-14; drill |
| S-5 | Money integrity: pricing service is the only discount authority; `discount_vnd` stored at create makes compensation immune to catalog drift; 0-VND orders grant through the canonical checked path; receipt renders truth | AC-04, AC-05; grep + drill |
| S-6 | Abuse containment: velocity signals (codes per identity, identities per device) are counts only; auto-pause pauses the campaign (never bans users); self-referral voiding reserved for the gated referral surface | AC-13 |
| S-7 | No enumeration: buyers can never list campaigns/codes/batches, read redemption counts, or learn active prefixes; partner-facing stats are aggregates | Absence + aggregate audit |
| S-8 | Credits integrity: issuance is ops-audited within config bounds; application is job-side, guarded `consumed`, idempotent; credits are days — no cash formatter exists | AC-10; drill |
| S-9 | Config protect: `billing.promo` flips audited + rate-limited via 32's ops path; absent config refuses issuance (no invented defaults) | Config grep + 409 path |
| S-10 | Reserved literal: `provider='promo'` minted only server-side; client sends are `unknown_provider`; refund/rail code treats `promo` as non-refundable-by-rail (no rail exists; 38's strategy table excludes it with an explicit reject) | Grep gate; AC-04 |

## Rate limits (gateway rules; Redis-backed counters)

| Route | Limit | On breach |
|---|---|---|
| `POST /api/v1/billing/promo/validate` | shared checkout budget: 20/h/IP + 10/h/email; 5/h per session | 429 `throttled` + `Retry-After`; timing stays normalized |
| `GET /api/v1/billing/credits` | 60/h/user | 429 `throttled` |
| `POST /api/v1/ops/promo-campaigns*` (writes) | 60/h/operator | 429 `throttled` |
| `POST /api/v1/ops/coupon-batches*` | 10/h/operator + 3/day per campaign | 429 `throttled` |
| `POST /api/v1/ops/credits/goodwill` | 30/h/operator | 429 `throttled` |
| Ops reads | 120/h/operator | 429 `throttled` |

## Degrade matrix

| Failure | Behavior |
|---|---|
| Redis down | Throttles degrade to conservative fixed windows (analogous to 32's degrade); validation stays correct; no stale campaign status exists (fresh reads) |
| Job (36's host) down | Pauses still block instantly (fresh reads); credit days and coupon expiries wait for the next run; no fork exists |
| Notification pipeline down | Campaign alerts + credit-application metrics queue with backlog; buyer flows unaffected |
| `billing-service` down | Checkout shows full price with a "codes unavailable" notice (never a wrong discount); no partial repricing exists |
| Coupon hash key rotated | Rotation runbook: dual-read window (old+new key) per 32's key-rotation posture; codes remain valid during rotation |
| Coupon key lost | Codes unrecoverable (hashes only) — documented as designed; campaigns pause, affected batches void |

## Audit and observability

Audits (24-month spine): `campaign.created/launched/paused/resumed/ended/auto_paused`, `batch.generated/export_acked/voided`, `coupon.redeemed` (order + campaign), `coupon.in_flight_honored`, `coupon.expired` (summary per run), `credit.issued` (goodwill/referral), `plan.credits_applied`. Metrics: `promo_validate{outcome}` (outcome classes only, never codes), `promo_redemptions{campaign,channel}`, `promo_burn_pct`, `promo_in_flight_honored`, `promo_voided`, `credits_applied_days`, `campaign_auto_pauses`. Zero PII labels; partner aggregates contain no buyer identifiers.

## Support answers

"Why was this code refused?" -> validation class internally (ops trace), always-neutral buyer copy; expiry may state itself. "Why didn't my discount apply to the renewal?" -> one-time discount copy + campaign window. "Where are my bonus days?" -> ledger rows + applied subscription id + audit `plan.credits_applied`; unapplied days shown as waiting. "Why did the campaign stop?" -> auto-pause audit (cap reached) or ops pause with reason.
