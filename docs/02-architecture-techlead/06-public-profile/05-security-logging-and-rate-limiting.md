# Public Profile - Security, Logging, and Rate Limiting (feature 06)

Phase 2 TL · Implements `BR-06`, `BR-08`, `BR-11`, `BR-12` and the security rows of `docs/01-requirements-ba/06-public-profile/07-non-functional-requirements.md`. Spring Security on Boot 4.1.1; gateway-terminated sessions.

## Authentication and authorization

- Users: SSO-only (Google, Apple, Facebook, LinkedIn, generic OIDC) at the gateway; no passwords. `profile-service` trusts only the signed internal JWT (`RS256`, `aud=profile-service`, 60 s TTL, JWKS from gateway); client headers are never trusted.
- Dashboard endpoints: session required; `user_id` set from the JWT `sub` by a request filter before any repository call; ownership checks in service code plus RLS (`app.user_id`) as layer two.
- Internal endpoints: service-to-service JWT only; the public-site service may resolve and list public profiles, never write.
- Deny-by-default: unmapped routes 401/403; actuator locked to the internal network (health only public).

## Rate limiting (gateway rules; Redis-backed counters)

| Route | Rule | On breach |
|---|---|---|
| `POST /api/v1/profile/username-check` | 60/min per user + 200/min per IP | 429 `throttled` |
| `POST /api/v1/profile/username-change` | 5/h + 20/day per user + Turnstile | 429 + audit |
| `PUT /api/v1/profile` | 30/min per user; claim variant 5/h per user + Turnstile | 429 |
| Internal resolve/list | 600/min per calling service | 429 |
| Redirect lookup path (feature 10) | cache-absorbed; cache miss <= 60/s per instance | 429 |

## Sanitization and XSS posture

- `HtmlSanitizer`: strips all markup from `displayName`, `bio`, `seoTitle`, `seoDesc` on write (no markup is ever stored).
- Rejects `javascript:`/`data:` URIs; stored links must be `https:`; rendering adds `rel="noopener ugc"` (feature 10).
- CSP enforced on dashboard and public page; no client-side username regex or reserved list (grep gate, T-13); private content is never cacheable publicly.

## Logging, tracing, audit

- Structured JSON logs: `service, traceId, userId, username, code, latencyMs`; `bio`/`displayName` bodies are never logged (they are the user's content, not an identifier).
- Audit events (append-only, 24-month retention): `profile.create`, `profile.update`, `username.change`, `visibility.change`; each carries actor, timestamp, old -> new values for renames.
- Tracing: W3C `traceparent` gateway -> service; sampling 100% mutations, 5% reads.
- Masking guardrail (platform): audit keeps usernames (public by design) but never SEO bodies or bio text; IPs are not stored on these paths.

## Degrade matrix (fail safe, never fail open)

| Failure | Profile dashboard | Public resolution |
|---|---|---|
| Redis down | rate counters fail closed for claim/rename (abuse-sensitive); `PUT` degrades with logged warning | feature 10 owns cache; resolve still served by profile-service |
| PostgreSQL down | 503 with retry copy; no partial writes | cached pages <= 60 s TTL, then branded 503 |
| Event bus down | mutations still commit; purge heals via 60 s TTL (BR-09) | stale <= 60 s, then fresh |
| Internal JWT key rotation | dual-key window; verification never disabled | same |
| Turnstile provider down | claim/rename fail closed (422 `challenge_failed`) | unaffected |

## Threat checklist

IDOR (403 + RLS probe in T-13), impersonation via retired names (permanent reservation, insert-only redirects), namespace squatting (reserved list double-layer), enumeration through `username-check` (rate-limited; verdict reveals availability only, accepted and documented), XSS via bio fields (sanitize + CSP), private-data leak (resolve returns `missing` for private; no cache of private rows), CSRF (token required on all mutations), secrets (env/vault only; grep clean in T-18), dependency risk (SBOM at release gate).
