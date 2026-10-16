# Consultations and Tips - Security, Logging, and Rate Limiting (feature 25)

Phase 2 TL · BA sources: `docs/01-requirements-ba/25-shop-consultations-and-tips/` (BR-09..BR-20, NFR-09..NFR-12). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | IDOR + RLS: creator by `user_id`, buyer by `buyer_user_id`, svc for token flows/computations; foreign ids → 403 + zero rows | AC-05; direct-PG probes |
| S-2 | Double-booking: partial unique index is the arbiter; CAS transitions prevent resurrecting terminal rows | AC-01; race drill at DB + API layers |
| S-3 | Tokens: HMAC-signed, action-scoped, exp = slot + 24 h; not a session; tampered/expired → 401; no token table to leak | AC-04; forgery + replay probes |
| S-4 | Guest tips: Turnstile + 30/h per IP + bounds enforced pre-money; email stored as salted hash only; raw email lives in the 32-40 receipt pipeline | AC-06, AC-07 |
| S-5 | Message safety: plain-text sanitization (links/markup stripped) on write; pending invisible publicly; moderation CAS; wall reads filtered in SQL | AC-08 |
| S-6 | Meeting links: `https:` allowlist; re-validated at confirmation; no platform-hosted call media; no redirect parameters | AC-04; scheme matrix |
| S-7 | Money integrity: zero amounts stored here; price-at-booking snapshot is the order's property; refund reasons fixed strings; courtesy-refunds audited | AC-03, AC-09; fee-literal grep |
| S-8 | Reminders: transactional class enforced in payload construction (no marketing flags, no unsubscribe tokens); ≤ 3 sends per booking | AC-11 |
| S-9 | Purge/scrub semantics verified (D-9): seller scope + buyer/guest scope; anonymous pins stay anonymous; names null on scrub | AC-12 |
| S-10 | Secrets: HMAC keys + Turnstile keys env/vault only; no `.env`; tips never touch card data (rails own it) | secrets audit |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Holds | 20/h per buyer | 429 + `Retry-After` |
| Reschedules / cancels | 10/h / 20/h | 429 |
| Slots query | 120/h per IP | 429 |
| Tip checkout | 30/h per IP + Turnstile | 429 |
| Moderation | 200/h per owner | 429 |
| Availability saves | 60/h per owner | 429 |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| Checkout/time services down (32-40) | Holds still create; checkout unavailable → hold expires naturally; no fake confirmations |
| Notification pipeline down | Reminders queue; sweeps retry idempotently (sent-flags); bookings unaffected |
| shop_db down | Slots/holds/confirmations 503; existing bookings unchanged |
| public-site down | Wall unavailable; tip checkout unaffected |
| Turnstile down | Guest tips fail closed (422/503); session tips unaffected |
| Sweeps lagging | Holds expire up to 60 s late; reminders up to 5 min late; never early or duplicated |

## Threat checklist

Slot-squatting via holds (TTL + rate caps), token forgery/replay (HMAC + exp + action scope), policy bypass via direct API (server-side matrix only), guest-tip abuse (Turnstile + throttle + bounds), message injection (plain text), wall leakage (SQL-filtered approved rows), IDOR on bookings/availability, refund social engineering (reason strings + audit + policy citation), PII in logs (email hashes only).

## Gates

- G4: OpenAPI diff additive; codes registered; supporters internal row + wall purge present.
- G5: S-2 race drill, S-3 forgery probes, S-4 guest matrix, S-5 moderation drill.
- G7: AC-01..AC-12 attach to the tag; container drill (Docker + Podman) per baseline release gates.
- Greps: zero fee literals in this directory; no raw emails stored; secrets clean.
