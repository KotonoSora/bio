# Deep Links and PWA - Database Schema and Cache Posture (feature 11)

Phase 2 TL · BA sources: `docs/01-requirements-ba/11-deep-links-and-pwa/02-user-stories-and-business-rules.md` (BR-04, BR-05, BR-11). Adjacent schema: `docs/02-architecture-techlead/10-share-and-seo/02-database-schema-design.md`.

## Zero schema (structural rule)

No DDL, no migration, no `V4` in any service. Webview detection output, banner state, escape actions, and install outcomes are request/session-scope only. A migration appearing for feature 11 fails review. The service worker caches HTTP responses only, never application state (no IndexedDB dataset is created). `public-site-service` remains databaseless.

## Reused data (read-only)

| Data | Owner | Use here |
|---|---|---|
| Block URLs (post-allowlist) | feature 08 tables | AnchorBuilder input; canonical rewrites derive at render |
| Profile + theme tokens | features 06 / 09 | Mobile-first template renders inside the feature-10 envelope |
| Cache envelope keys + purge | feature 10 | No UA dimension added; invariance asserted by drill |
| Beacon event types on `POST /api/t/:username` | features 12-15 (forward) | Sends `banner_show`, `escape_tap`, `prompt_*`; zero per-hit DB writes attributable to this feature |

## Cache invariance rules

1. Same slug + same publish version => byte-identical HTML across every UA fixture (drill AC-07).
2. Cache keys, `Vary` headers, and purge envelopes are unchanged from feature 10 (`Vary: Cookie` owner-mode only).
3. The `webview` class is applied after parse by the micro-script; SSR markup ships identically to all visitors.
4. Micro-script asset is fingerprinted, immutable, and shared across all visitors and versions until hash change.

## Forward contract (analytics 12-15)

- Events: `banner_show` (<= 1 per page view), `escape_tap` (`result: native|browser|copy`), `prompt_shown|prompt_accepted|prompt_dismissed`.
- Transport: existing beacon endpoint; filtering and rate limits are owned there; this feature sends, never defines.
- Until 12-15 land, drills run against a stub collector; production wiring is one config value (`beacon.enabled`).

## Verification drills

| Drill | Asserts |
|---|---|
| Absence | `git grep` shows no migration, no new table, no new endpoint from this feature |
| Invariance | UA fixture matrix returns byte-identical bodies; cache hit ratio unchanged (AC-04, AC-07) |
| No-scheme | Hostile fixtures never render non-`https` URLs from input (AC-05) |
| Measurement | Stub collector receives exactly the contracted events; no DB writes on the public path (AC-06) |
| Violation review | Any mobile-specific aggregation table or endpoint => review rejects (BA BR-11) |
