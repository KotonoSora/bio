# Stripe Webhook (signature verification + idempotent grant)

PLAN ref: P3-2 adapted. Deviation flagged: PLAN specifies a PayOS webhook; this feature verifies Stripe's `Stripe-Signature` header and grants on `payment_intent.succeeded`. Order creation in `monetize/stripe/checkout-orders.md`; pending/review repair in `monetize/stripe/pending-refund.md`.

## Purpose

Define how the platform trusts Stripe: every event's signature is recomputed over the exact raw request bytes with the endpoint secret (WebCrypto, timestamp-tolerance enforced) before anything is read from the payload; only then does `payment_intent.succeeded` resolve an order and grant exactly once. Forgery, replay, wrong amounts, and already-paid races can never create a double grant or a free upgrade.

## Users

- Stripe server: *As a Stripe server, I want to post signed event JSON to the registered endpoint (successes, failure, refund, disputes) so that the platform trusts Stripe as defined here.* posts signed event JSON to the registered endpoint (successes, failures, refunds, disputes).
- Buyer: *As a Buyer, I want to receive entitlement (plan upgrade or product claim) after a genuine succeeded event so that the platform trusts Stripe as defined here.* receives entitlement (plan upgrade or product claim) after a genuine succeeded event.
- Ops: *As an Ops, I want to rely on idempotency during Stripe retries, CLI replays, and incident recovery so that the platform trusts Stripe as defined here.* relies on idempotency during Stripe retries, CLI replays, and incident recovery.

## Functional requirements

1. Webhook handler `POST /api/billing/webhook-stripe` (public, WAF + strict rate limits, no session, HTTPS only, CSRF-exempt route): reads the RAW body bytes first and verifies the `Stripe-Signature` header (`t=` timestamp + `v1=` HMAC-SHA256, endpoint secret `whsec_…`) via WebCrypto with clock-tolerance enforced. Critical Workers gotcha, stated normatively: the body must be verified before any JSON parse — parse-then-reserialize changes bytes and breaks valid signatures. Mismatch/expired timestamp → 400, nothing written, nothing parsed.
2. On verified events, switch by type: `payment_intent.succeeded` → resolve `metadata.orderCode` to a `provider='stripe'` order and run the atomic grant (3); `payment_intent.payment_failed` → order `failed` with a buyer-safe reason; `charge.refunded` → refunded path (settlement doc); `charge.dispute.*` → dispute path (settlement doc); unknown types → 200 acknowledged and logged (forward-compatible, never error-retry on unknown).
3. Atomic grant (single transaction): (a) `orderCode` must resolve or respond 200-with-rejection-log, no leak of valid refs; (b) event `amount` (zero-decimal VND, as-is) must equal stored `amount_vnd` or reject + alert, no grant; (c) `pending|authorizing → paid` transition + `stripe_pi_id` write + entitlement write + `audit_log{actor, action, target, at}` append; (d) Stripe `event.id` persisted under UNIQUE — a seen event id is a durable no-op, which is what makes Stripe's automatic retries and CLI replays safe.
4. Always respond 200 after durable handling (Stripe retries non-200s with backoff); reserve non-200s for transport/DB failures only. Validation outcomes are final and logged, never retried into.
5. Endpoint registration: one endpoint per environment in the Stripe dashboard (TEST vs LIVE secrets strictly separated), registered/confirmed after every deploy that changes the URL. Local development: Stripe CLI `listen`/`trigger` through the public tunnel (`cloudflared tunnel → wrangler dev`) — documented runbook, exercised per milestone.
6. Monitoring: structured logs per event (orderCode, event id/type, result, latency) with signatures/secrets redacted; alert on consecutive verification failures or a spike in duplicate-event hits.

## Data / API touches

- Mutates `orders.status` (`pending|authorizing → paid|failed|cancelled|expired`) keyed by `metadata.orderCode`, recording `stripe_pi_id` + seen `event.id` (UNIQUE). Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; webhook resolves the owner from the stored order, never from request identity; RLS preserved.
- Cryptography: HMAC-SHA256 over raw bytes via WebCrypto only (no Node `crypto` import; no parse-before-verify). Secret from `wrangler secret STRIPE_WEBHOOK_SECRET`.
- The client-side confirm result and any synchronous return-page hit never grant — grant authority belongs to this webhook plus the settlement doc's retrieve path only.

## Acceptance criteria

- TEST end-to-end (`stripe trigger payment_intent.succeeded` + real test-card payments): `paid` + entitlement within the SLA; dashboard event log and local ledger agree.
  *GWT: Given TEST end-to-end (`stripe trigger payment_intent.succeeded` + real test-card payments): `paid` + entitlement within the SLA; When the next step executes; Then dashboard event log and local ledger agree.*
- Replay test: same `event.id` delivered twice → single transition, single entitlement, single audit entry; repeat is a durable no-op.
  *GWT: Given Replay test: same `event.id` delivered twice; When the transition executes; Then single transition, single entitlement, single audit entry; repeat is a durable no-op.*
- Tamper tests: bad signature → 400, nothing parsed; stale timestamp → rejected; altered amount → no grant; unknown `orderCode` → no leak.
  *GWT: Given Tamper tests: bad signature; When the transition executes; Then 400, nothing parsed; stale timestamp → rejected; altered amount → no grant; unknown `orderCode` → no leak.*
- Page-closed-mid-payment drill (no client callback ever arrives): webhook alone drives the grant — buyer sees `paid` on next status poll.
  *GWT: Given Page-closed-mid-payment drill (no client callback ever arrives) context; When the checked action occurs; Then webhook alone drives the grant — buyer sees `paid` on next status poll.*
- Burst of bogus webhooks throttled by WAF/rate rules; valid traffic unaffected.
  *GWT: Given Burst of bogus webhooks throttled by WAF/rate rules; When the next step executes; Then valid traffic unaffected.*

## Out of scope

- PaymentIntent creation and client confirmation (see `monetize/stripe/checkout-orders.md`); pending/3DS states, retrieve sweep, refunds, disputes (see `monetize/stripe/pending-refund.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — shared as-is); shop delivery; P4/P5. All other provider paths untouched.

## Open questions

- Q1: Synchronous-grant SLA vs webhook lag — does the buyer see "paid" on client confirm, or "confirming" until webhook/retrieve (shared with the plans SLA question)?
- Q2: Which further event types (e.g. `review` / radar holds) do we handle in v1 vs log-and-alert?
