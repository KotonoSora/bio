# Audience Insights - Security, Logging, and Rate Limiting (feature 13)

Phase 2 TL · BA sources: `docs/01-requirements-ba/13-audience-insights/` (BR-01, BR-03, BR-07..BR-10). Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`; inherited intake controls: `docs/02-architecture-techlead/12-profile-analytics/07-security-logging-and-rate-limiting.md`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Structural privacy | No IP or visitor-id column exists; projection accepts a compile-time whitelist DTO; country-grain geo only; raw logs on the 90d TTL |
| S-2 | Salt discipline | Daily dedupe salt + `RETURNING_SALT_MONTHLY` from the secret store; rotation bounds historical linkage; boundary visits count `new` (stated in copy) |
| S-3 | Owner scoping | Server-set `user_id` + RLS (12 S-5); optional `profile` param equality check; ops read metrics only |
| S-4 | Clamp integrity | Range/dimension validation server-side only; entitlement per request (max 30 s snapshot staleness); deep-link and direct API bypass impossible |
| S-5 | Retention correctness | Nightly dry-run then batched deletes; downgrade re-clamps on the next request; aged rows never resurrect on re-upgrade |
| S-6 | UTM input hygiene | Normalize (lowercase, charset `[a-z0-9._-]`, 64-char cap, <= 3 fields, <= 10 pairs); stripped chars counted; values rendered escaped in React; `Other (n)` bounds display |
| S-7 | Cardinality flood control | Top-25 + server-computed `Other` bounds every payload; gauge alert > 500 distinct UTM keys/profile/day; virtualized lists bound DOM |
| S-8 | No new intake surface | Abuse controls, bot decision, and burst tightening inherited from 12 (segments inherit bot filtering at intake — no second filter to drift) |
| S-9 | Returning memory hygiene | `seen` keys hold HMAC-derived values only (no raw IP/UA), TTL 30d, per-profile namespaced; no cross-profile stitching is representable |
| S-10 | Read isolation | Same session auth as 12; `private, no-store`; PWA worker network-only; analytics numbers never appear on public pages (feature 10 exclusivity) |

## Threat checklist

| Threat | Mitigation |
|---|---|
| Cross-profile visitor stitching | Structurally unqueryable: no visitor-id column, per-profile cache namespaces (AC-04, AC-11) |
| PII smuggled via UTM values | Charset strips `@`/spaces; caps + counted truncation; values are owner-visible only; residual PII-like charset-conforming values noted as accepted risk |
| Cardinality DoS via typo-split campaigns | S-7 bounds payloads; alert fires before memory/storage pressure |
| Age-based downgrade snooping (restore old data) | S-5: deleted rows stay deleted; reads clamp by current plan |
| Bypassing plan windows via deep link/API | S-4: server-side only; no client check is trusted |
| Replay double counting | 12's natural-key upserts; segment PKs are the idempotency keys |
| Correlation via cache keys | HMAC values + TTL + monthly rotation; raw inputs never stored |

## Logging and degrade

- Metrics: `rollup.segments_written`, `retention.deleted`, `retention.would_delete`, `segments.cardinality{dimension}`, `returning.keys`; alerts: UTM cardinality spike, retention overrun, segment-write failure.
- Logs: counts and job results only — never raw UTM values in log lines, never visitor keys.
- Degrade: seen-check failure -> default `returning=new` + counter (intake never blocks on this); retention job failure -> alert + retry next night (extra rows are harmless, reads clamp already); read failures inherit 12's stale/labelled states — no new failure domain.
