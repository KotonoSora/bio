# Checkout Session - Security, Logging, and Rate Limiting (feature 33)

Phase 2 TL · BA sources: `docs/01-requirements-ba/33-checkout-session/` (BR-05..BR-16, NFR-09..NFR-15). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`; kernel controls in `docs/02-architecture-techlead/32-monetize-overview/05-security-logging-and-rate-limiting.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: session + RLS on create/read/cancel; cross-user 403 + RLS deny; guest rows carry `guest:` ids and are invisible to sessions by construction; the internal create path verifies caller service scope | AC-10 |
| S-2 | Rendering safety: quote/pending/error surfaces XSS-stripped with CSP `default-src 'self'`; `displayName` and error details rendered as text only | AC-12 |
| S-3 | Amount integrity: server re-prices at quote and create; client amounts ignored; `quoteToken` refuses drift (409/410); the fee snapshot is pinned at insert and immutable | AC-04 |
| S-4 | Anti-duplication: keyed idempotency is PG-UNIQUE (survives restarts); fingerprint compare blocks key reuse across intents; keyless window bounded to 90 s | AC-05 |
| S-5 | Abuse: Turnstile on anonymous checkout/quote surfaces; per-IP/session envelopes; burst spikes produce `throttled` 429 without touching rows | AC-12 |
| S-6 | Secrets: quote-token HMAC secret and guest pepper via env/vault only; CI secrets grep; rotation documented with no-downtime drill | rotation drill |
| S-7 | Guest privacy: email stored RLS-protected, masked in responses and logs, HMAC-minted id only; SCRUB on deletion (PII nulled, row kept) | logs grep; purge drill |
| S-8 | Audit: create/handoff/expire/cancel/guest-mint append `audit_log`; append-only grants; support trace by `orderCode` | AC-10 half; audit sample |
| S-9 | Data minimization: `ship_to` stored for `merch` only, never logged or listed; `extras` bounded scalar map; no PII in non-`merch` flows | log greps; list-projection checks |
| S-10 | Transition authority: only the sweep (this feature) and verified grant (34) write statuses; cancel is a guarded conditional update; races settle to one terminal outcome | AC-07, AC-08, AC-09 |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Quote | 30/min per session; 10/min per IP (anonymous) | 429 |
| Create | 10/min per session; 5/min per IP; 3/min per email (guest) | 429 |
| Cancel | 10/min per order | 429 |
| Payment-methods | 60/min per session | 429 |
| Internal create | 600/min per caller service | 429 |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Shop `checkout-context` down | product checkout 503 (`product_absent` not fabricated); plan checkout unaffected |
| Fees service down | create blocked with 409 `schedule_not_published` passthrough; quote shows unavailable; no partial rows |
| Redis down | keyless collapse unavailable (duplicate pending orders possible — each requires its own handoff/payment, so no double-charge path); rate tokens degrade to session-only; alert |
| Push/Sweep job down | lapsed orders stay `pending`; grant guard still refuses late money; ops alert; expiry honesty via `expiresAt` in responses |
| `billing_db` down | money routes 503; nothing partial (single-txn create) |
| Notification pipeline down | receipts backlog; checkout unaffected |

## Gates

- G4: OpenAPI diff additive (new routes only); ops/internal routes role/service-guarded.
- G5: IDOR/guest-invisibility probes, XSS, Turnstile/abuse, secrets grep.
- G7: AC-01..AC-12; matrix + identity + double-submit + races attach to the tag; container pair boots; log greps (no email/ship-to/URL leakage).
