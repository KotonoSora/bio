# Short Links - Security, Logging, and Rate Limiting (feature 29)

Phase 2 TL · Implements `BR-05`, `BR-09`, `BR-11` and the security/session rows of `docs/01-requirements-ba/29-short-links/07-non-functional-requirements.md`. Spring Security on Boot 4.1.1; stateless resource server.

## Authentication and authorization

- Users: SSO-only (Google, Apple, Facebook, LinkedIn, generic OIDC) terminated at the gateway; no passwords, no reset flows. `link-service` trusts only the signed internal JWT (`RS256`, `aud=link-service`, 60 s TTL, JWKS from the gateway); raw client headers are never trusted.
- Owner endpoints: principal required; ownership = JWT `sub` match + RLS `app.user_id`; IDOR attempts return 404 (no state oracle).
- Ops endpoints: `ROLE_OPS` re-verified per request; suspend and bulk mint always audited with actor, reason, timestamp.
- Public resolve: allowlisted anonymous route only (`GET /go|r|a/*`); no session material on that path.
- Deny-by-default: unmapped routes 401/403; actuator locked to the internal network (health only public).

## Rate limiting (gateway rules; Redis-backed counters)

| Route | Rule | On breach |
|---|---|---|
| `GET /go\|r\|a/*` | 600 rpm per IP, burst 60; bot-class WAF filter | 429 page; hashed IP counter increments |
| `POST /api/v1/links` | 20/min per account + 60/min per IP + Turnstile | 429 `throttled` |
| `POST /api/v1/ops/links/report` | 5/min per IP + Turnstile | 429 `throttled` |
| Ops endpoints | 60/min per ops user | 429 + audit |
| Bulk mint job | ops-only; creator tokens -> 403 + audit | - |

## Logging, tracing, audit

- Structured JSON logs: `service, traceId, code, destinationHost, status, latencyMs`; never full URLs, never raw IPs, never PII.
- IP handling: salted SHA-256 with 24 h window rotation, for throttling only.
- Tracing: W3C `traceparent` gateway -> service -> queue; Micrometer Tracing (OTel) export; sampling 100% mint, 1% resolve.
- Audit events (append-only, 24-month retention): `link.suspend`, `bulk.mint`, `quota.override`; evidence rows for suspended links retained (AC-04).

## Degrade matrix (fail safe, never fail open)

| Failure | Resolve | Mint |
|---|---|---|
| Redis down | direct DB read, p95 <= 300 ms | quota check fails closed -> 503 |
| Blocklist client down | cached snapshot <= TTL; after TTL, serve and flag | fail closed -> 503 `blocklist_unavailable` |
| PostgreSQL down | cached hits <= TTL, then branded 503 | 503 |
| Gateway JWT key rotation | dual-key window; verification never disabled | same |
| Queue down | resolve keeps serving; events buffered locally and re-sent (at-least-once) | unaffected |

## Threat checklist

IDOR (403/404 + RLS probe), enumeration (CSPRNG codes + rate limits + no state oracle), open-redirect misuse (scheme check + blocklist + suspension), spam economics (quota + Turnstile + audited bulk), cache poisoning (purge on suspend; no user-controlled cache keys), secrets (env/vault only, never committed), dependency risk (SBOM at release gate T-20).
