# Verification Badge and Follower Display - Security, Logging, and Rate Limiting (feature 07)

Phase 2 TL · Implements `BR-01`, `BR-03`, `BR-05`, `BR-08`, `BR-10`..`BR-12` and the security rows of `docs/01-requirements-ba/07-verification-and-followers/07-non-functional-requirements.md`. Extends the security baseline of `docs/02-architecture-techlead/06-public-profile/05-security-logging-and-rate-limiting.md` (auth model unchanged).

## Authentication and authorization

- Creator endpoints: session JWT via gateway (unchanged from feature 06); ownership checks + RLS on both new tables.
- Ops endpoints: `ROLE_OPS` re-verified per request; every decision writes an append-only audit row with actor + reason; reviewer identity is never exposed publicly.
- Evidence path: presign endpoint issues short-TTL (10 min) upload URLs for random keys only; the viewer URL is issued only to `ROLE_OPS` with the same TTL and opens in a sandboxed origin - evidence is never rendered inline in the app.
- Public exposure: badge state + cached totals only; `evidence_ref`, reviewer ids, and rejection reasons never appear on public or internal read payloads.

## Rate limiting (gateway rules; Redis-backed counters)

| Route | Rule | On breach |
|---|---|---|
| `POST verify-request` | 3/day per user + Turnstile | 429 `throttled` |
| `POST followers/refresh` | 6/h per user | 429 + `Retry-After` |
| `PUT socials` | 30/h per user | 429 |
| Ops endpoints | 120/h per reviewer | 429 + audit |
| `GET verify-status` (poll) | 60/h per user (client polls 60 s while pending) | 429 |

Refresh fetches themselves are budgeted at <= 1 req/s per network (no thundering herd on source platforms).

## Injection, fake-badge, and count-integrity controls

- Badge faking: no user-uploadable badge asset; exactly one rendering path (`verification_status == VERIFIED` -> shared SVG component); bio/display-name markup is stripped at write (feature 06 sanitizer) and asserted again in the renderer test.
- Handles/URLs: `https:` only, per-network pattern validation, markup rejected; anchors render `rel="noopener ugc"`; CSP holds on both surfaces.
- Counts: cap 100,000,000, rate-limited edits, versioned history, 10x jump flag; display copy never implies audit.
- Near-match flags are advisory (no auto-block, no username disclosure to claimants).

## Logging, tracing, audit

- Structured JSON logs: `service, traceId, userId, profileId, status, code, latencyMs`; evidence bytes, handles' full URLs, and count bodies are never logged; blob keys may appear on ops audit rows only.
- Audit events (append-only, 24-month retention): `trust.request.created`, `trust.decide` (actor + reason + from/to status), `followers.jump_flagged`.
- Tracing: W3C `traceparent`; sampling 100% mutations and ops decisions, 5% reads; refresh job traces carry profile id (never username).

## Degrade matrix (fail safe, never fail open)

| Failure | Behavior |
|---|---|
| Blob store down | presign 503; existing applications unaffected; no orphan keys created |
| Source network down/timeout | keep last-good + `stale`; refresh error metric; public page unaffected (AC-08) |
| Ops decision path down | queue readable; decisions fail closed (403/503) - no state mutation without audit write |
| PostgreSQL down | 503 on trust endpoints; public page serves cached <= 60 s then branded 503 |
| Event bus down | transitions still commit; purge heals via 60 s TTL |
| Turnstile provider down | `verify-request` fails closed (`422 challenge_failed`) |

## Threat checklist

Fake badge via markup/emoji (single SVG path + renderer test), impersonation (near-match flags, rename re-review, audited revocation), count inflation (cap + versioning + jump flag + rate limits), evidence leak (random keys, role-gated short-TTL URLs, sandboxed viewer, no logging of bytes), IDOR (403 + RLS probe in T-12), CSRF (token on all mutations), vault creep (grep gate on tables/endpoints/dependencies), secrets (env/vault only), dependency risk (SBOM at release gate T-17).
