# Checkout Session - Architecture and Tech Stack (feature 33)

Phase 2 TL · Extension of `billing-service` (feature 32's service; conventions there). BA sources: `docs/01-requirements-ba/33-checkout-session/`. Consumed seams: shop 22 `checkout-context` (price freeze), shop 26 `fees/quote` (snapshot), leads 27 internal write (opt-in), shop 25 (guest tips + `bookingId` extras). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED (platform-wide): the legacy checkout/session verdict maps onto `billing-service` — session state is the `orders` row only; no session table, no Workers/KV store. Quote is stateless and writes nothing. | One row per intent; quote purity preserved structurally. |
| D-2 | Quote is a pure read: plan intents price from the in-service plan catalog (35 - active `plan_terms` rows, explicit `term`); product intents from shop's `checkout-context` (22) with the fee preview from `fees/quote` (26); coupons validated (39) as part of pricing. It returns a stateless `quoteToken` (HMAC-SHA256 over intent key, amount, catalog version, issued-at, 15-min expiry) — no rows, no cache entries. | Zero side effects with a checkable identity guarantee. |
| D-3 | Create is one pipeline in one transaction: validation ladder → `checkout-context` (price freeze per 22) → `fees/quote` (snapshot `{grossVnd, feeLines[], netVnd, rateVersion}`) → single `orders` insert → commit. After commit: handoff via the provider-adapter interface, audit append, optional lead opt-in write. A handoff build failure keeps the order and returns 502 (retry with the same key rebuilds it). | No partial rows; provider handoff never blocks truth. |
| D-4 | Idempotency: `Idempotency-Key` (optional header) is UNIQUE in PG; the stored `fingerprint_hash` (sha256 over user, kind, intent, provider, amount, return/cancel URLs) distinguishes same-intent replays from key reuse (`422 idempotency_key_reuse`). Keyless double-clicks collapse via Redis `bill:fp:{fingerprint}` (TTL 90 s → order_code). | Keyed idempotency survives restarts; the window only smooths double-clicks. |
| D-5 | Expiry: `expires_at` is set at create from per-rail config (32 D-3); a sweep (≤ 5 min cadence) flips `pending|authorizing → expired` with a single conditional `UPDATE` (rerun = 0 rows). Late money against `expired|cancelled` is refused by 34's grant guard — the sweep never grants. | Exactly-once flip; grant authority stays with 34. |
| D-6 | Cancel: owner-scoped conditional update (`status = 'pending' → 'cancelled'`, via 34's guard); already cancelled returns 200 idempotent; any other state returns 409 `cancel_conflict`; the race with grant resolves on row-level conditionals per 34's legal edges. | One winner, no resurrection, no distributed lock. |
| D-7 | Selection: the eligible set is computed per request as registry ∩ `billing.eligibility` ∩ kill-switch survivors ∩ capability filter (server-side UA class + `Sec-CH-UA` hints; hints advisory). Display order comes from `billing.display_order`, else the stable registry order rendered verbatim. | No favorites in code (grep gate from 32 D-4 covers ordering constants). |
| D-8 | Guest policy: plans login-only; products (incl. tips) accept a mandatory `buyerEmail`. Guest rows carry `user_id = 'guest:' + HMAC-SHA256(pepper, lower(trim(email)))[:32]`; the email is stored RLS-protected, masked in logs, scrubbed on deletion. No account, no claim/transfer; the magic-link receipt read is 37's. | 25's precedent extended without claim machinery; no null-owner rows. |
| D-9 | Seams: `POST /internal/v1/billing/checkout` (service JWT) lets shop 25 create tip orders for session or guest buyers (Turnstile already enforced at 25; same validation ladder). Lead opt-in (`leadOptIn=true` from the explicit checkbox) writes `POST /internal/v1/leads` (`source=checkout`, `sourceRef=orderCode`) post-commit, best-effort with bounded retry. | Guests and internal callers share one creation path; opt-in never blocks money. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Quote function (pure) | `billing-service` | plan/product pricing + coupon + fee preview |
| Create pipeline + idempotency | `billing-service` | single txn; fingerprint compare |
| Handoff builder (stub adapter now, rails 40) | `billing-service` | `next{kind, payload}` frozen in TL 03 |
| Expiry sweep | `billing-service` (scheduled) | conditional update; ≤ 5 min cadence |
| Cancel route | `billing-service` | owner-scoped, race-safe |
| Selection service | `billing-service` + Redis cache | eligible list + capability filter |
| Guest minting | `billing-service` | `guest:` id + RLS invariants |
| Lead opt-in writer | `billing-service` → profile-service | post-commit, bounded retry |

## Boundaries

Owns: quote, create, expiry, cancel, return-page contract, selection surface, guest policy, internal create, lead opt-in capture. Does not own: statuses/dispatch/grant/order reads (34); handoff mechanics per rail (40); plan catalog values (35 — consumed); coupon rules (39 — consumed as validation); receipts (37); refunds (38); transcripts/dispatch of notifications (32's pipeline — 37 enqueues).

## Consumers

| Consumer | Contract |
|---|---|
| FE checkout/pending | quote, checkout, payment-methods, cancel, polling (34) |
| shop 25 | `POST /internal/v1/billing/checkout` for tips (`kind=tip`, guest email) |
| shop 23/24/25 | `order.granted.v1` on grant (34) — ship-to/extras carried (23/25) |
| leads 27 | opt-in writes (`source=checkout`) |
| support/ops | order lookup + cancel via 34's reads and this feature's cancel |
