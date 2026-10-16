# Payment Rails - Document Context and Scope (feature 40)

Phase 1 BA · Feature 40 of 53 — monetize 9/9: one conformance contract every rail shares — intake verification, anti-replay, settlement sweeps, the exceptions queue, disputes, and kill-switch discipline. Sources: `monetize/{payos,sepay,vnpay,zalopay,napas,google-pay,apple-pay,stripe}/*`, `monetize/payment/payment-security.md`. Implemented by `docs/02-architecture-techlead/40-payment-rails/`.

## Context

Money arrives through eight rails with different notification shapes: signed webhooks (payos, sepay, stripe), a return+IPN pair (vnpay), a callback with `mac` (zalopay), a bearer-authenticated sync push (napas), and PSP-confirmed synchronous charges behind wallet sheets (google-pay, apple-pay). This feature owns the intake, verification, anti-replay, settlement, and exception contracts for all of them — the last mile that turns a provider notification into exactly one guarded grant through feature 34. Every legacy per-rail "PLAN adaptation" flag lands as the platform's standing rule: eight equal rails, explicit `provider`, no default anywhere. The security baseline (`payment-security.md`) migrates as this feature's probe battery, enforced per provider on every deploy.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | One intake contract per rail: signature/token verification, raw-byte discipline, constant-time compares, exact response codes |
| OBJ-02 | Anti-replay by construction: every provider notification persists a UNIQUE `(provider, event_ref)` before it can touch an order |
| OBJ-03 | Every grant path amount-checks and routes through feature 34's single-writer guard (no rail writes status itself) |
| OBJ-04 | No real payment stays ungranted: sweeps settle stale pendings and pull missed bank transfers on a 15-minute cadence |
| OBJ-05 | No ambiguous money auto-grants: unmatched/mismatched/late transfers land in a reviewable exceptions queue with defined resolutions |
| OBJ-06 | Card-rail disputes run a deadline-driven case: freeze, evidence, win restores / loss reverses with entitlement revocation |
| OBJ-07 | Kill switches stop new checkouts within 5 s without ever blocking verifies, grants, or refunds; incident runbook binds rotation + reconcile |
| OBJ-08 | The pre-deploy probe battery (IDOR, tamper, replay, double-submit, races, secrets grep) gates every rail on every deploy |

## Scope

In scope (SC-01..SC-09): the eight intake endpoints (`/webhooks/{rail}`) + wallet charge endpoints; provider_events anti-replay; settlement sweeps per rail; the bank-rail exceptions queue + ops link/refund/dismiss; dispute cases (stripe + wallet PSPs) with evidence + win/loss edges; kill-switch behavior contract; late-transfer grace; per-rail response contracts; the security probe battery; ops verify actions.

Out of scope: checkout creation, repricing, handoff payloads (33); order statuses/edges/guard internals (34); refund execution + credit notes (38 — this feature records each rail's refund capability so 38's strategies have members); promo logic (39); entitlement math (35/36); notification dispatch (32); PSP contract negotiation and merchant onboarding (ops/legal); accounting; P4/P5.

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Rail provider (system) | C: intake deliveries only | Posts signed notifications; never reads data |
| Buyer | R: order status + confirming states | Sees honest states; polls; never mutates via rail surfaces |
| Support/ops (restricted role) | R: queues + cases; C: verify, link, refund-record, dismiss, evidence | Resolves exceptions and disputes with audit |
| Finance | R: sweeps, exceptions, disputes, kill-switch log | Reconciles rail truth against the ledger |
| System (sweeper) | U: settlement states via the guard | Bounded, idempotent repair |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Sweep cadence + stale threshold (all rails Q1) | Sweeper every 15 min; stale-pending threshold 30 min (config `billing.rails.sweepStaleMinutes`); 48 h lookback on bank pulls |
| Q-02 | Overpay tolerance (sepay Q1) | Hold every mismatch: overpays are NOT auto-granted; ops links or refunds from the queue (conservative, uniform) |
| Q-03 | Late-transfer grace (sepay/napas Q3) | 24 h linkable after expiry (`billing.rails.lateTransferGraceHours`); beyond → refund path; late money never auto-grants (34's guard refuses; ops re-link is the only door) |
| Q-04 | Expired-but-paid edges (payos/vnpay/zalopay/napas Q2) | Same rule everywhere: while pending → sweep/verify may grant; after expiry → grace window per Q-03; never silent auto-grant on terminal orders |
| Q-05 | Synchronous vs confirming UX (stripe/google/apple Q1) | Wallet rails: grant on the PSP-confirmed charge (buyer sees paid on the authorize verdict); webhook stays confirmation/audit. Redirect/bank rails: confirming + polling until verify lands |
| Q-06 | Partial refunds (vnpay/zalopay/stripe Q2) | Tree-wide full-only (38's Q-01); per-rail refund capabilities recorded for 38's strategy members |
| Q-07 | Buyer self-service refunds (stripe Q2) | Request-only (38's Q-02); no buyer-initiated rail calls exist |
| Q-08 | Multi-currency / Adaptive Pricing (stripe Q3) | VND-only at launch (platform-wide); multi-currency deferred (recorded) |
| Q-09 | Fraud tooling beyond velocity (payment-security Q2) | Velocity + existing Turnstile/WAF only; device/Radar-class signals on first incident (recorded); no auto-bans ever |
| Q-10 | Rate limits (payment-security Q1) | Explicit budgets now (defined in TL 05), not WAF defaults; tuned from abuse data |
| Q-11 | IP allowlisting (sepay Q2) | HMAC is mandatory; IP allowlist is optional defense-in-depth, added only on observed abuse (recorded) |
| Q-12 | Apple certificate mechanics (apple Q2) | Merchant Identity Certificate via vault/env; quarterly rotation joins the secrets drill; URL/domain re-confirmation after every cert renewal |
| Q-13 | `onPaymentAuthorized` pre-check (google Q2) | Enforced for all wallet checkouts (uniform, no amount threshold) |

## Constraints carried

No rail code writes `orders.status` — only 34's guard does; every provider path resolves the owner from the stored order, never from request identity; secrets via vault/env only, rotated quarterly; raw PAN/token/cryptogram material never logged or stored beyond PSP references; kill switches delist but never block settlement truth; the event catalog addition (`order.chargeback_reversed.v1`) follows 32's frozen-shape discipline.
