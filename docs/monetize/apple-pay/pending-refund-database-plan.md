# Apple Pay Pending Refund Database Plan (sweep + reversal evidence — no new tables)

PLAN ref: P3-1/P3-2 gap-fill (card realities: pending 3DS/review, refunds, chargebacks) + settlement audit. BA source: `docs/monetize/apple-pay/pending-refund.md`. Consumed by the pending-refund backend plan (sweep, verify, refund, dispute; splits next to this file). Order/entitlement/audit rows live in the session/ledger/platform-identity plans (owned there, never here); refund-ledger rows (partial entries) live in the refunds/refund-processing database plan (next in lifecycle queue — single definition; this file references the rule, never the DDL). No migration — this slice creates zero tables.

## Purpose

Define the settlement data contract without owning storage: the confirming sub-state convention, sweep ownership boundaries, refund recording rules (full vs partial), dispute evidence requirements, and the fraud-signal feed. No `CREATE TABLE` here by design — settlement mutates rows owned elsewhere through guarded transitions.

## Users

- Backend developer: sweeps, verifies, refunds, and contests through owned rows + guarded edges.
- QA: proves no-limbo, exactly-once verify, revocation-on-refund, and freeze-on-dispute from row state.
- Support/ops: looks up live charge status, issues refunds, assembles evidence from audit + PSP refs.

## Functional requirements

1. Confirming sub-state convention: buyer-visible `confirming` = stored `authorizing` + `confirming_reason` (3DS-required | issuer-review | webhook-delayed) with a TTL (reason field owned by the session `orders` row extension — column defined once in the checkout-session database plan; this file defines the convention, never the column). TTL lapse without resolution routes to sweep (limbo has an expiry date by construction).
2. Sheet-closed-without-payment: `completePayment` never reached → order stays `pending`, reusable for a fresh sheet (no cleanup row, no tombstone — pending-until-expiry is the complete story).
3. Sweep ownership: scheduled job scans `authorizing` + stale-`pending` (threshold per Q1), status-queries each via the PSP adapter, settles (`paid` on captured with idempotent grant; `expired`/`failed` otherwise; already-`paid` skipped). Sweep writes through the same guarded transitions as charge/webhook paths (one edge table, three callers — uniformity asserted by shared-guard tests, not by parallel implementations).
4. Refund recording: full refund → status `refunded` + gateway refund reference on the order row + entitlement revoked (plan downgrade per plans rule; product claim invalidated in shop) + audit; partial refund → status stays `paid` + partial-ledger entry (rows owned by the refunds database plan — referenced here, defined there) + proportional entitlement adjustment. Every refund appends `audit_log` (actor may be buyer-per-policy-Q2 or ops — policy decides WHO, this contract decides WHAT gets recorded).
5. Dispute evidence contract: dispute webhook → `disputed` (grant frozen — no new entitlements; existing access flagged per plans/shop dispute rules) + evidence task `{deadline, required_pack}` opened (task row owned by... no new table: evidence tasks ride the audit trail + ops queue owned by the agency/opsCSE? Simplest exact-once: dispute state on the order row (`disputed`) + evidence assembled from audit + PSP refs + delivery proofs at contest time (no task table — deadline tracked by PSP webhook payload dates + ops metric, contest action is the ops endpoint). Win → restore prior state; loss → `chargeback_reversed` + entitlement revoked + fraud-review log entry. Repeat-offender signals (same buyer/device) feed a human-approved blocklist proposal (proposal artifact owned by ops tooling; auto-ban explicitly absent — no auto-ban code exists to audit).
6. Fraud hygiene proportionate to cards: velocity limits (per-IP/email on checkout + validate + authorize, shared with Turnstile/WAF baseline); 3DS outcomes honored (never bypass step-up — bypass code would be a finding, asserted by absence); device/session anomalies (sheet opened, token from different session) logged as review rows (log fields, not a table — review queue owned by ops tooling).
7. Alerting fields: sweep-settled counts (metrics), every refund/dispute to the ops channel (event, not a table), consecutive PSP auth failures → page (threshold owned by ops config).

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for settlement state anywhere under `docs/monetize/apple-pay/` for this slice (review greps it). Consumed rows: `orders` (status/psp refs/`confirming_reason`), entitlement/claim rows (sibling slices), partial-ledger rows (refunds slice), `audit_log` (platform identity). PSP APIs server-side only (charge owned by authorization slice; status/refund/dispute here).

## Acceptance criteria

- Limbo drill: 3DS-abandon parks in confirming with TTL; on-demand verify returns non-terminal; sweep expires it later; sheet-closed stays reusable `pending` — zero grants anywhere in the matrix.
- Verify drill: suppressed webhook + status query grants exactly once; redelivery no-ops.
- Refund drill: full + partial with references recorded, entitlements revoked per rule, re-grant impossible.
- Dispute drill: freeze on webhook; evidence submitted pre-deadline; win/loss transitions exact; blocklist proposal human-gated.
- Absence drill: no auto-ban code, no step-up bypass, no new tables (grep + review).

## Out of scope

- Sweep/verify/refund/dispute implementation (backend plan); confirming/refund/dispute screens (frontend plan); validation/charge/grant (authorization plans); session config/intake (checkout plans); `orders`/entitlement/refund-ledger DDL (their database plans); plan downgrade UX (plans slices); bank-rail exceptions (their minors); accounting reports; P4/P5.

## Open questions

- Q1: Sweep cadence + stale threshold (BA Q1 — 15min/30min default, or tighter for cards)?
- Q2: Buyer self-service refunds vs ops-only; partials at launch (BA Q2)?
- Q3: Evidence pack template now vs first-dispute-driven (BA Q3)?
