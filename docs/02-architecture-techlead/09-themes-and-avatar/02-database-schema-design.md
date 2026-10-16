# Themes and Avatar - Database Schema Design (feature 09)

Phase 2 TL · **Zero migrations**: this feature adds no tables, no columns, and no Flyway version (the chain stays at V5 from feature 08). It consumes two existing columns and defines the blob namespace. Implements the `BR-*` rules of `docs/01-requirements-ba/09-themes-and-avatar/02-user-stories-and-business-rules.md`.

## Consumed columns (defined once in feature 06 V1)

`profile.profiles.theme_id TEXT NOT NULL DEFAULT 'default'` and `profile.profiles.avatar_key TEXT` (nullable; NULL = default glyph) - see `docs/02-architecture-techlead/06-public-profile/02-database-schema-design.md`. Both are written only through this module's endpoints, audited, and protected by the existing profile-row RLS (`p_owner`). `theme_id` is FK-by-convention to `ThemeRegistry` ids - deliberately not a DB FK, because themes ship with the deploy and a FK would couple data to code versions.

## Blob storage (platform MinIO; no relational rows)

| Namespace | Keys | Rules |
|---|---|---|
| `avatars/` | `avatars/{ulid}.{ext}` (ext from sniffed type: jpg/png/webp/mp4) | Server-generated random keys (no enumeration); objects immutable (new upload = new key); previous key deleted only after the row update commits; `Cache-Control: public, max-age=31536000, immutable` |
| `themes/` | `themes/{id}-{version}.{ext}` (thumbnails) | Fingerprinted by theme version; immutable cache; served from platform static/blob |
| `verification/` | feature 07 namespace | Named here so the two are never confused; lifecycle owned by 07 |

Write ordering: blob PUT -> profile row update -> old-key delete. A crash between steps leaves exactly one orphan object; the sweep deletes it - requests never block on cleanup.

## Orphan sweep (no table)

Daily job (`APP_AVATAR_SWEEP_CRON`): list `avatars/` objects older than 24 h, exclude keys referenced by any `profiles.avatar_key`, delete the rest; dry-run metric (`appearance.orphan.swept.total{mode=dryrun}`) emitted before the first live run and kept as a daily gauge.

## Caches (no relational state)

- Theme registry: memory-resident in this service; `GET /api/v1/themes` and the internal catalog carry `Cache-Control: public, max-age=300`.
- Entitlement snapshot: consumed from `account-service` (consumer cache `<= 30 s`) - explicitly **not** a table (keeps the no-table rule); contract: `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`.
- Public-page purge: `profile.published.v1` + the feature 10 TTL (60 s) bound freshness to <= 2 min.

## Verification drills

1. No-table drill: schema diff before/after this feature shows zero new tables; `flyway info` shows no V4.
2. Key drill: 100 uploads -> 100 unique non-sequential ULID keys; guessing a neighboring key returns nothing enumerable (no listing).
3. Orphan drill: interrupted upload leaves one unreferenced object; sweep deletes it after 24 h; referenced keys untouched.
4. Cache drill: avatar + thumbnail responses carry immutable headers; a theme switch leaves the avatar URL unchanged.
5. Violation drill: any PR adding a theme/avatar table fails review by rule (BR-11); grep gate `grep -rn "CREATE TABLE" docs/02-architecture-techlead/09-themes-and-avatar/` returns nothing.
