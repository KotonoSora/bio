# Share and SEO - Architecture and Tech Stack (feature 10)

Phase 2 TL · **New service: `public-site-service`** (stateless SSR renderer, no database). Implements `docs/01-requirements-ba/10-share-and-seo/`. Platform pin set: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary (microservices)

| Owns | Excludes (owner -> contract) |
|---|---|
| SSR routes `/{username}` + `/{username}/cv`, tag builder, HTTP cache policy + service page cache, purge listener, single-flight, `robots.txt` + `sitemap.xml`, owner preview surfaces + head model | Profile identity/rename rows -> feature 06; badge/follower fields -> feature 07; blocks -> feature 08; theme registry -> feature 09; deep-link banner + PWA (same origin) -> feature 11; analytics beacons -> features 12-15; resume snapshot semantics -> features 17-21 |

Why a separate service: the public read path has a different scale profile from all authoring paths (anonymized, cacheable, read-only) and a different lifecycle (renderer changes ship independently of authoring rules). Why no database: it owns no state - the cache (Redis) and derived HTML are disposable; database-per-service is honored by owning zero databases. Zero writes become structural, not reviewed.

Gateway route precedence (single segment matches only; reserved slugs are enforced upstream by feature 06): `/api/*` -> owning services; `/app/*` -> SPA origin; `/go|/r|/a/*` -> link-service; `/assets/*` -> static edge; `/robots.txt`, `/sitemap.xml`, `/{username}`, `/{username}/cv` -> public-site-service.

## Compose slice (new container)

```yaml
  public-site-service:
    environment:
      APP_PROFILE_INTERNAL_URL: "http://profile-service:8082"
      APP_CACHE_TTL_SECONDS: "60"
      APP_S_MAXAGE_SECONDS: "120"
      APP_SWR_SECONDS: "600"
```

Redis: platform instance, database 5 (final numbering pinned by the platform baseline). No Postgres. Production front: CDN honoring `s-maxage`/SWR; compose parity uses the platform edge cache container (pinned by the pending platform baseline). Replicas: stateless x3, autoscale on rps.

## build.gradle.kts delta (one new container's build)

Boot 4.1.1, Kotlin 2.4.21, Gradle 9.8.1 wrapper-only. Starters: web, security (service JWT resource server), actuator, micrometer; flagged additions once: `spring-boot-starter-thymeleaf` (fragments `head` / `blocks` / `seo-tags`) and `caffeine` (in-process L1 under the portable cache port). No database driver, no Flyway - absent by design and asserted by grep.

## React.js 19.3 integration topology

The public pages ship **zero JS** (crawler parity by construction - no hydration). React owns the dashboard surfaces: the sandboxed preview iframe, the shared `PurgeChip`, the SEO checklist, and the share/QR panel (client-side QR generation; no server endpoint). Field rules: `docs/01-requirements-ba/10-share-and-seo/04-ui-data-dictionary.md`.

## Key decisions

| Decision | v1 | Trigger to revisit |
|---|---|---|
| `s-maxage` launch default | 120 s inside the 60-300 window | churn complaints exceed support budget (drop to 60) |
| Service cache TTL | 60 s + purge; Caffeine L1 <= 10 s | stale reads observed after purge (shrink L1) |
| Canonical host | bio URL indexed; `/cv` `noindex` + canonical to bio | SEO report demands separately indexed CVs |
| Redirect caching | renames `301` cacheable (permanent); 404 never cached | redirect churn observed |
| Preview reads | additive `mode=owner` param on internal resolve/blocks reads | owning services expose dedicated preview endpoints |
| Deploy strategy | blue/green for this container (cache is disposable) | cache warmup cost exceeds blue/green budget |
