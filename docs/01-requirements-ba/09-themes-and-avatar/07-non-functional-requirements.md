# Themes and Avatar - Non-Functional Requirements (feature 09)

Phase 1 BA · Thresholds are v1 gates; changing one requires a spec revision with a measured trigger.

## Performance (client-observed)

| NFR | Target |
|---|---|
| NFR-01 `GET /api/v1/themes` | p95 <= 150 ms warm (registry in memory), <= 300 ms cold |
| NFR-02 `PUT /api/v1/profile/theme` | p95 <= 300 ms |
| NFR-03 avatar upload server processing (after transfer, <= 2 MB) | p95 <= 300 ms |
| NFR-04 `DELETE /api/v1/profile/avatar` | p95 <= 200 ms (file delete best-effort) |
| NFR-05 public render addition (tokens + avatar key from the same row read) | <= 20 ms; zero extra queries (06 pattern) |
| NFR-06 save -> public visibility | <= 2 min (60 s TTL + purge event) |

## Uploads and storage

- Image caps: <= 2 MB, formats JPEG/PNG/WebP, magic-byte verified; no server-side recompression in v1 (upload-as-is, CSS crop).
- Video: <= 2 MB, <= 10 s loop, MP4 `ftyp` only, allowed only on `videoAvatar` themes.
- Orphan sweep: daily job deletes unreferenced `profile/avatars/` files older than 24 h (dry-run metric first); daily storage drift alert if orphans > 100.
- Avatar files and theme thumbnails: immutable, 1-year cache; theme catalog: 300 s cache.

## Availability and scale

- Appearance endpoints: 99.95% monthly; uploads fail closed (no partial state: a rejected request leaves zero files).
- Registry is memory-resident: no PG reads for gallery; catalog endpoint scales with SSR traffic via cache, not queries.
- Write volume is rare (theme/avatar changes are occasional): no partitioning, no new tables, no new storage beyond `avatars/`.

## Security

- Plan gate is server-side and fail-closed (entitlement unreachable -> pro themes denied); UI checks exist for honesty only.
- Rate limits: avatar upload 10/h/user + Turnstile; theme switch 60/h/user; themes GET 300/h/user; breach -> `429 throttled` + `Retry-After`.
- No enumeration: names are ULID-random; no listing endpoint; direct-guess probes return nothing.
- Upload bytes are never logged; audit rows carry actor, operation, and key only.

## Accessibility and localization

- Theme cards expose name + pro state + selected state to assistive tech; uploader announces progress and errors via ARIA live; alt text = profile display name.
- All copy resolves from stable codes; dates (if shown) use the user's locale.

## Observability

- Metrics: `appearance.theme.switch.total{themeId, proOnly}`, `appearance.gate.denied`, `appearance.avatar.upload{result}`, `appearance.avatar.rejected{code}`, `appearance.orphan.swept.total`, `appearance.catalog.age_seconds`.
- Alerts: rejected-upload rate > 20% for 10 min (abuse or client bug); catalog age > 15 min on the public-site service; sweep failure two days in a row.
