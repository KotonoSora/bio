# Link Blocks - Security, Logging, and Rate Limiting (feature 08)

Phase 2 TL · Implements `BR-01`..`BR-05`, `BR-08`, `BR-12` and the security rows of `docs/01-requirements-ba/08-link-blocks/07-non-functional-requirements.md`. Extends the baseline of `docs/02-architecture-techlead/06-public-profile/05-security-logging-and-rate-limiting.md` (auth model unchanged).

## Authentication and authorization

- Dashboard endpoints: session JWT via gateway (feature 06 model); `user_id` comes from the session only - never from bodies. Ownership check precedes any load: `profile.userId == session.userId`, else 403 (never filter by profile alone).
- RLS: `b_owner` on all operations; `b_public` (`is_visible = TRUE`) is defense-in-depth for reads - the service still applies the window predicate explicitly in every public query.
- Internal read: service JWT (`aud=profile-service`); `profile_absent` for missing/private/suspended/retired keeps shape/timing parity with resolve (no existence leak).

## Injection and content safety

- Payloads are data-only: no HTML/JS/CSS fields exist; every string is sanitized at write (script/iframe/event-handler stripped) and re-asserted by renderer tests; anchors render `rel="noopener ugc"`; CSP holds on dashboard and public surfaces.
- URL allowlist: `https:` with non-empty host only; `javascript:`/`data:`/`http:` rejected (`bad_url_scheme`) - enforced pre-send client-side AND server-side, same message, server verdict final.
- `type` is a closed server enum + DB CHECK; `PATCH` type changes are `422 validation_failed` (immutability), so no re-typing bypasses per-type validation.
- `embed` stores provider + URL only; the renderer owns markup (third-party embed scripts are a renderer-sandbox concern, feature 10).

## Rate limiting (gateway rules; Redis-backed counters)

| Route | Rule | On breach |
|---|---|---|
| `POST/PATCH/DELETE /api/v1/blocks*` | 120/h per user | 429 `throttled` |
| `POST /api/v1/blocks/reorder` | 60/h per user (client also queues one in-flight) | 429 + `Retry-After` |
| `GET /api/v1/blocks` (owner) | 300/h per user | 429 |
| `GET /internal/v1/profiles/*/blocks` | service-level budget, not user-facing | 429 + trace |

## Logging, tracing, audit

- Structured JSON: `service, traceId, userId, profileId, blockId, type, op, code, latencyMs`; **payload bodies and URLs are never logged**; sanitizer hits increment `blocks.sanitized.total` without content.
- Audit (append-only, 24-month retention): one row per mutation (`op`, actor, blockId); reorder writes exactly one row with the new id order hash.
- Tracing: W3C `traceparent`; 100% of mutations and reorders sampled; drop-in `traceId` on `reorder_mismatch` responses to debug client staleness.

## Degrade matrix (fail safe, never fail open)

| Failure | Behavior |
|---|---|
| PostgreSQL down | 503 on block endpoints; public page serves from cache until TTL expires |
| Redis counters down | writes fail closed (503) - no unthrottled mutation window |
| Event bus down | mutation commits; purge heals via 60 s TTL (<= 2 min freshness) |
| Sanitizer/validator exception | write rejected (fail closed); no unsanitized row can persist |
| Reorder contention | requests serialize per profile (row lock); timeout -> 409-style retry advice, positions untouched |
| Storage volume down | thumbnail upload blocked in feature 09 pipeline; block saves with existing/no `thumbnailKey` unaffected |

## Threat checklist

IDOR (service assert + RLS probe in T-10), stored XSS via any payload field (sanitize + renderer test), scheme abuse (`https:` allowlist both layers), draft/window leakage into public reads (server-side filter, `b_public` backstop), cache poisoning (only server-rendered/filtered payloads cached by feature 10), enumeration (id = ULID, no sequential probing), CSRF (token on all mutations), secrets (env/vault only), dependency risk (SBOM at release gate T-16).
