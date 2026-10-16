# Payment Rails - User Stories and Business Rules (feature 40)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/40-payment-rails/`. Scope: `docs/01-requirements-ba/40-payment-rails/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Rail provider | post signed notifications that are verified and applied exactly once | trust is mechanical, never assumed | High |
| US-02 | Buyer | see honest confirming/paid states on every rail | no silent limbo, no double charge | High |
| US-03 | Support/ops | resolve unmatched or mismatched transfers from one queue | real money is never lost to a typo | High |
| US-04 | Support/ops | verify any rail order on demand and act on disputes before deadlines | recovery and defense without improvisation | High |
| US-05 | Finance | see sweeps, exceptions, and disputes as reconcilable records | provider truth is auditable end to end | High |
| US-06 | Engineer | run one probe battery per rail before deploy | uniform security, no per-rail drift | Medium |

## Business rules

- **BR-01 Verification by rail.** payos: HMAC-SHA256 checksum over the canonical field string; sepay: HMAC-SHA256 delivery required (API-key/OAuth/none forbidden); vnpay: HMAC-SHA512 over the return/IPN query (return is UX-only; IPN alone grants); zalopay: HMAC-SHA256 (key2) over the raw `data` string (no re-serialization); napas: short-lived bearer token (server-side refresh); stripe: `Stripe-Signature` HMAC over exact raw bytes, timestamp tolerance enforced before JSON parse; wallets: PSP-confirmed charge + PSP webhook scheme. Every compare is constant-time; every mismatch is a metric, never a grant.
- **BR-02 Anti-replay first.** No notification touches an order before `UNIQUE (provider, event_ref)` persists; a conflicting delivery reads the original outcome and returns the same response with no side effects.
- **BR-03 Amount + order binding.** Verification resolves the order from the stored reference and asserts provider, amount, and currency equality; mismatch → refusal response, quarantine metric, no grant, no state change.
- **BR-04 Single writer.** Rail adapters never write `orders.status`; every settle/grant/dispute edge goes through 34's `TransitionGuard` with actor, amount assertions, and audit.
- **BR-05 Response contracts are frozen per rail** (table in TL 03): success codes after durable handling only; validation-final refusals never leak why (generic copy + metric); transient failures invite redelivery; a redelivery of a settled event is always a no-op.
- **BR-06 Sweeps.** A 15-minute sweeper (per-rail advisory lock, batches ≤ 200, idempotent) settles stale pendings: payos link status, vnpay queryDr, zalopay order query, stripe PaymentIntent retrieve, napas check-transaction, wallet PSP status; bank pulls re-scan the last 48 h. Sweeps never grant late (Q-04); they grant only verified in-window payments.
- **BR-07 Exceptions queue.** Bank-rail deliveries that fail binding (unknown ref, mismatch, cancelled, expired, duplicate ref, late) open a `rail_exceptions` row; only exact+exact+in-window auto-settles.
- **BR-08 Mismatch holds.** Overpay, underpay, and edited-content transfers never auto-grant; ops resolves each with link (grant through the guard), refund, or dismiss — always with reason + audit.
- **BR-09 Late grace.** A verified payment for an expired order is linkable by ops for 24 h after expiry (`billing.rails.lateTransferGraceHours`); beyond that the queue only allows refund/dismiss. Late money never auto-grants.
- **BR-10 Dispute cases (card/wallet rails).** A provider dispute event opens a `dispute_cases` row, moves the order to `disputed` via the guard, and freezes further grants on it; access continues until loss; deadline is mandatory while open.
- **BR-11 Dispute won.** `disputed → paid` via the guard, case closed; metrics record the win; no entitlement change.
- **BR-12 Dispute lost.** `disputed → chargeback_reversed` via the guard; plan entitlements revoke through 38's revocation helper (audit `plan.chargeback_reversed`); claims invalidate via the `order.chargeback_reversed.v1` event; loss amounts recorded on the case.
- **BR-13 Kill switches delist only.** A flipped rail disappears from checkout methods ≤ 5 s (32's config) and refuses new wallet charge calls; intake, sweeps, verifies, and refunds continue — moving money is always settled.
- **BR-14 Secrets discipline.** All rail secrets/certs via vault/env only, never logged, never echoed in responses; quarterly rotation joins the drill; a leaked secret triggers rotation + reconcile, not silence.
- **BR-15 Probe battery.** Per rail, pre-deploy: IDOR probes, good/bad-signature, tampered amount, unknown reference, replay, double-submit, cancel/expiry race, kill-switch drill, secrets grep. Any red blocks the deploy.
- **BR-16 No default rail.** Providers are explicit everywhere; no fallback rail exists on any surface (standing platform rule, realized here for intake and handoff).
- **BR-17 Wallet charge discipline (google-pay, apple-pay).** Token intake → PSP charge call (idempotency key = orderCode) → PSP-confirmed verdict grants synchronously; timeout → recover via PSP status query, never a second charge; apple merchant sessions are single-use with minutes TTL.
- **BR-18 Per-order serialization.** Charge calls and settles serialize per order (row/advisory lock); parallel deliveries of the same economic event collapse to one grant.
- **BR-19 On-demand verify.** Ops (and only ops) can trigger a rail verify for one order; same adapter, same guard, audited; throttled per order.
- **BR-20 Provider references.** Every settled order records the rail's transaction reference into `psp_ref` (first-write-wins via 34's guard); provider event ids live in `provider_events`.
- **BR-21 Ownership resolution.** The buyer/user is always resolved from the stored order, never from request headers or payload identity claims.
- **BR-22 PII + material hygiene.** No PAN/token/cryptogram/secret material in logs, audits, or metrics; payloads persist as hashes + references only; masked PII at most.
- **BR-23 Refund capability members.** Each rail's refund posture (API with idempotency, API query-first, or manual evidence) is recorded per rail so 38's strategies have concrete members; execution stays 38's.
- **BR-24 Failure copy symmetry.** Buyer-facing refusal copy is identical across validation classes and rails; internal metrics distinguish classes; rails get rail-contract codes only.
