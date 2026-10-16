# Lead Forms - Security, Logging, and Rate Limiting (feature 27)

Phase 2 TL · BA sources: `docs/01-requirements-ba/27-lead-forms/` (BR-10..BR-16, NFR-09..NFR-12). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Anti-oracle: identical 202 body + timing for accepted/duplicate/unknown; enumeration analysis shows no distinguishable request; malformed email is the sole 422 | AC-06; timed-probe matrix |
| S-2 | IDOR + RLS: owner by `user_id`; `svc` context only on the capture path; cross-creator reads 403 + zero rows | AC-05; direct-PG probes |
| S-3 | Queue-mediated writes: the public handler can only insert outbox rows; consumer applies sanitize/re-check before any lead write; a poisoned payload can never reach `leads` unsanitized | AC-04 (script half); injection probes |
| S-4 | Token safety: HMAC confirm tokens single-purpose, 48 h; GET read-only (scanner drill); POST idempotent; tamper → `confirm_invalid` with zero state change | AC-03, AC-08 |
| S-5 | Consent integrity: marketing only on a checked submit; no grant endpoint exists (surface review + grep); upgrades only by explicit checked action | AC-02, AC-12 |
| S-6 | Abuse economics: gateway Turnstile-class + WAF/rate; per-block 100/min/source → 15-min pause + notice; re-request 3/h per address; signals TTL 24 h; IPs salted-hashed only | AC-04; burst drills |
| S-7 | Sanitization/no reflection: script/iframe/handler stripping before storage; success states render creator copy only; CSP on the capture surface | AC-04; payload probes |
| S-8 | Strictest-wins enforcement: rank guard on every writer (consumer + internal API); downgrade attempts 409 `rank_conflict` | AC-07; rank matrix |
| S-9 | Audit: event appends actor-stamped, 24-month retention; UPDATE/DELETE alert on event tables | BR-14; alert drill |
| S-10 | Secrets/PII: challenge + token keys env/vault only; email is the only mandatory PII; logs carry ids/hashes, never raw emails | secrets audit + log grep |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Lead submit | 100/min per block per source → 15-min pause; gateway per-IP 120/min burst 240 | 429 `submit_paused` / `throttled` |
| Confirm GET/POST | 30/min per IP | 429 |
| Re-request | 3/h per address | 429 |
| Inbox feed | standard session limits | 429 |
| Internal seams | service budget | 429 + trace |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| Redis (db 4) down | Caps degrade to gateway-only limits (still enforced); pause state resets — acceptable, signals are advisory; capture unaffected |
| Consumer down | Submissions queue in the outbox (durable); landing delays beyond p95; backlog alert at 1 000 queued |
| Notification pipeline down | Confirm/welcome queue; pending rows stay ineligible (safe direction) |
| `pg_trgm`/28 surface down | Inbox feed unaffected (btree); search unavailable (28's degrade) |
| Turnstile provider down | Submit fails closed at the gateway challenge; creators see no silent bypass |
| DB read failure | 503; the submit handler never falls back to direct lead writes |

## Threat checklist

Enumeration via submit responses, mailbox probing via confirm GETs, token tampering/replay, outbox poisoning, consent forgery, script payloads in name/note, burst flooding, disposable-domain farming (flag-accept), cross-tenant reads, PII in logs, redirect open-redirect (own page or link-block target only).

## Gates

- G4: OpenAPI diff additive; codes registered; submit route public-allowlisted under `/api/t/leads/*` with the challenge wiring.
- G5: S-1 timing matrix, S-3 injection probes, S-4 scanner drill, S-8 rank matrix.
- G7: AC-01..AC-12 attach to the tag; container drill (Docker + Podman) per baseline release gates.
- Greps: zero consent-grant endpoints; zero third-party script tags on the capture surface; secrets clean; raw emails absent from logs.
