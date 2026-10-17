# Themes and Avatar - Architecture and Tech Stack (feature 09)

Phase 2 TL · Extension of `profile-service` (module `com.app.profile.appearance`), writing `theme_id` / `avatar_key` on the existing profile row. Implements `docs/01-requirements-ba/09-themes-and-avatar/`. Platform pin set: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary (microservices)

| Owns | Excludes (owner -> contract) |
|---|---|
| `ThemeRegistry` (code-shipped tokens), plan-gated theme switching, the avatar upload/delete pipeline, storage namespace `profile/avatars/`, orphan-sweep hygiene, `GET /api/v1/themes` + internal catalog | Profile identity, publish flow, purge chip -> feature 06; blocks -> feature 08; SSR fragment, preview mode, cache purge -> feature 10; `verification/` evidence namespace -> feature 07; resume templates, shop/email/agency theming -> their majors |

Boundary rationale: theme switch is one `profiles.theme_id` update on the existing RLS/audit/purge path, and avatar metadata rides the same transaction as the key record; a media service would split the publish transaction (row + file + purge) for rare writes with no independent scale. Extraction trigger: upload processing volume or a media-transformation pipeline that outgrows the service profile.

Cross-service rules: no new database, no new container, no cross-service table access; SSR (feature 10) gets `themeId`/`avatarKey` from the feature 06 resolve payload (no DTO change) and token definitions from the internal catalog below.

## Runtime delta (compose slice unchanged from feature 06)

```yaml
  profile-service:                # same service; new env only
    environment:
      APP_AVATAR_SWEEP_CRON: "0 30 3 * * *"    # daily orphan sweep (UTC)
```

No new containers; the platform uploads volume hosts `profile/avatars/` and the Nginx edge serves it; gateway owns Turnstile. Podman parity remains release acceptance.

## build.gradle.kts delta (no new dependencies)

Unchanged versus `docs/02-architecture-techlead/06-public-profile/01-architecture-and-tech-stack.md`: Boot 4.1.1, Kotlin 2.4.21, Gradle 9.8.1 wrapper; multipart and byte sniffing use built-ins (no image libraries - v1 stores uploads as-is and sniffs magic bytes only).

## React.js 19.3 integration topology

Route `/app/appearance` (owner-only): gallery renders `GET /api/v1/themes` (zero hardcoded themes), token preview applies CSS variables to dashboard chrome only, uploader posts multipart with progress. No color picker, CSS textarea, or font-URL input exists - the front-end grep gate fails the build if one appears. Field rules: `docs/01-requirements-ba/09-themes-and-avatar/04-ui-data-dictionary.md`.

## Entitlement dependency

`PlanGate` consumes the platform entitlement snapshot from `account-service` (contract: `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`; consumer cache `<= 30 s`). DEVIATION CARRIED (feature 01 migration): the earlier 300 s cache sketch is superseded by the feature 13 D-7 `<= 30 s` bound. The local rule stays fixed: `proOnly` themes require an explicit `pro` snapshot, and any lookup failure denies pro themes (fail closed) while standard themes proceed.

## Key decisions

| Decision | v1 | Trigger to revisit |
|---|---|---|
| Registry placement | code in this service; internal catalog endpoint for renderers | cross-service skew observed despite contract test |
| Upload path | server-proxied multipart; validate before any file write | upload volume makes proxy a bottleneck (upload-grant + post-validate fallback) |
| Write ordering | file write -> row update -> old-file delete; orphans swept 24 h | sweep lag causes storage growth |
| Theme thumbnails | platform assets fingerprinted by version, immutable cache | renderer needs runtime-generated previews |
| Plan gate | fail closed for proOnly on entitlement failure | entitlement outage becomes frequent (cache TTL tuning) |
| Purge signaling | reuse `profile.published.v1` (no new event) | appearance-only consumers need separation |
