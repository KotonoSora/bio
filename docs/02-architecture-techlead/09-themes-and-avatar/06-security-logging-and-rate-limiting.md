# Themes and Avatar - Security, Logging, and Rate Limiting (feature 09)

Phase 2 TL · Implements `BR-01`, `BR-03`..`BR-08`, `BR-12` and the security rows of `docs/01-requirements-ba/09-themes-and-avatar/07-non-functional-requirements.md`. Extends the baseline of `docs/02-architecture-techlead/06-public-profile/05-security-logging-and-rate-limiting.md` (auth model unchanged).

## Authentication and authorization

- Dashboard endpoints: session JWT via gateway; `user_id` from the session only (never bodies); the profile-row RLS baseline covers both written columns.
- Plan gate: per-request entitlement check against the cached snapshot; `proOnly` denied unless an explicit pro snapshot exists - **fail closed** on any lookup failure. UI hiding is honesty, never the gate.
- Internal catalog: service JWT (`aud=profile-service`); no user data in it (tokens are public by design).

## Upload validation (the security core of this feature)

- Bounded read: 2 MB + 1 byte ceiling enforced while streaming (no unbounded buffering, no temp files before validation).
- Magic-byte sniffing is the only content verdict: JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF....WEBP`, MP4 `ftyp`; extension and claimed MIME are never trusted. SVG/HTML/script content cannot pass any accepted signature.
- Video gate: MP4 accepted only when the active theme has `videoAvatar=true`, loop <= 10 s.
- No server-side image parsing/decoding of untrusted bytes in v1 (sniff + store only), removing the image-decoder attack surface.
- Serving: immutable media URLs served by the Nginx edge with the sniffed content type + `X-Content-Type-Options: nosniff`; names are ULID-random with no listing endpoint (no enumeration).

## Rate limiting (gateway rules; Redis-backed counters)

| Route | Rule | On breach |
|---|---|---|
| `POST /api/v1/profile/avatar` | 10/h per user + Turnstile | 429 `throttled` |
| `PUT /api/v1/profile/theme` | 60/h per user | 429 |
| `GET /api/v1/themes` | 300/h per user (public list, edge-cacheable) | 429 |
| `GET /internal/v1/themes` | service budget, trace-attributed | 429 + trace |

## Logging, tracing, audit

- Structured JSON: `service, traceId, userId, op, code, bytesIn, sniffedType, latencyMs`; upload bytes are never logged; claimed MIME is logged only as claimed (never as fact).
- Audit (append-only, 24-month retention): `appearance.theme.switch` (from/to theme, proOnly), `appearance.avatar.upload` (new key), `appearance.avatar.delete`.
- Tracing: W3C `traceparent`; 100% of uploads and theme switches sampled; sweep job traces carry counts, never keys in bulk.

## Degrade matrix (fail safe, never fail open)

| Failure | Behavior |
|---|---|
| Entitlement snapshot/lookup down | pro themes denied (`403 theme_forbidden`); standard themes unaffected |
| Storage volume down | upload `503` with zero row change; delete clears the row and lets the sweep retry the file |
| Redis rate counters down | mutations fail closed (503) - no unthrottled upload window |
| Event bus down | mutation commits; purge heals via 60 s TTL (<= 2 min freshness) |
| Catalog cache stale on a renderer | one fresh fetch, then default theme fallback; page never breaks |
| Registry deploy removes a theme in use | existing `themeId` resolves to default at render; the owner is prompted to pick a new theme on next dashboard visit |

## Threat checklist

Plan-gate bypass (server gate + fail-closed drill), hostile uploads (magic bytes, zero-write assertions on the file-store mock), enumeration (ULID keys, no listing), CSS/JS injection (structurally impossible: no authoring fields; grep + ArchUnit gates), storage abuse/hotlinking (immutable content-keyed URLs, rate limits, Turnstile), orphan growth (24 h sweep + drift alert), secrets (env/vault only), dependency risk (SBOM at release gate T-14).
