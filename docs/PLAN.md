# Detailed plan — link-in-bio + resume block (containerized Spring Boot service)

Source: `AGENTS.md` is the compact agent contract. This file is the build order + acceptance + security detail. Step IDs (P0–P5, S-1…S-7) are stable — specs cite them; never renumber.

Supersedes: the former Cloudflare-first revision (Workers Hono default, Neon + Hyperdrive, Drizzle, `wrangler` deploys, password auth, PayOS-default checkout). Those defaults are replaced below; the frozen contract (JSON REST, cookie session, server `user_id`, idempotent `orderCode`/webhooks, no direct DB from client) is unchanged. A deviation log sits at the end of §0.

## 0. Stack validation (targets: 10M registered / 100k concurrent normal / 1M concurrent peak; security > cost)

- Mandated stack (no per-doc re-decision): PostgreSQL LTS `18.6` (containerized primary, same version dev → prod) + Spring Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` (wrapper-only). One Spring Boot artifact per release; Flyway migrations; Testcontainers PG 18.6 for grant-path tests. Full pins in `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`, `docs/platform/stack/postgres-baseline.md`.
- Containerized throughout: every dev and official release ships as an OCI image runnable independently via Docker or Podman (`image + env + PG DSN`; Flyway migrates on boot; rollback = re-run previous tag). Mechanics in `docs/platform/containers/container-lifecycle.md`; per-tag acceptance in `docs/platform/delivery/release-gates.md`.
- Architecture: modular monolith first (`resume`, `bio`, `billing`, `shop`, `leads`, `platform` modules, ArchUnit boundaries); microservices only per use case when ≥2 split triggers fire with gate numbers. Framework, triggers, and frozen split contract in `docs/platform/architecture/architecture-decision.md`. Each delivery plan records its own verdict.
- External: R2-compatible blob storage (assets, snapshot archives >30d, raw beacon NDJSON 90d TTL), cache/CDN layer in front of SSR (throttle counters + single-flight locks live here; never truth), AI text capability for JD-tailor with per-plan credit caps, Turnstile + WAF/rate rules. No self-hosted DB beyond the containerized PG in v1.
- Pipelines (logical names kept from specs; run as containerized service equivalents, not Workers bindings): beacon ingest → hourly rollup consumer (zero PG writes per hit); scheduler + outbox fan-out at P4 (never `await` publish in-request); encrypted OAuth token vault at P4 (`access_token_enc`, key via vault/env, never logged); read replicas + top-profile pre-render at P5 (not upfront). PWA install prompt instead of native mobile app.
- Cost math: at 1M concurrent (~10–50k req/s) bandwidth + DB I/O dominate. Cheapest levers in order: (a) cache-hit >95% on `/:username` (CDN + cache layer, TTL 60–300s + SWR), (b) batch all writes (hourly rollups), (c) scale the container fleet on demand, (d) read replicas last. Single-PG rule (no Mongo/MySQL/second OLTP): two systems = two bills + two audit logs + cross-DB integrity gaps — worse cost AND worse security. Offload by tier (PG truth / blob archives / cache), never by feature.
- Load gates (k6, staging mirror): P1 exit ≥10k concurrent public reads p95 <500ms; P3 exit ≥100k concurrent normal-use mix; P5 exit ≥1M concurrent read-heavy + 100k mixed. No phase ships past its gate.
- Database decision: containerized PG `18.6` primary from P1 (RLS on, PITR, least-privilege app vs migration roles, per-module schemas). Size math: profile+blocks+1 snapshot ≈ 20–50KB/user → 10M users ≈ 200–500GB. Growth killers never touch PG: `link_clicks{day,count}` aggregates only via consumer, `snapshot_json` versions >30d to blob, raw beacon logs to blob NDJSON 90d TTL. Portability: TEXT ids, ISO8601 timestamps, `amount_vnd INT`, numeric int64 `orderCode` UNIQUE. MySQL/PlanetScale: weaker story — no reason to prefer. MongoDB: rejected — joins (`users→profiles→blocks→orders`), billing transactions, and SQL-first access all fight a document store; two systems double audit/backup surface against our security-first rank.
- Auth decision: SSO-only (Google, Apple, Facebook, LinkedIn + generic OIDC). No password hashes, no reset flow. Detail in `docs/account/access/sso-login.md`; authorization in `docs/account/access/authorization-model.md`.
- Billing decision: eight equal rails (`payos`, `sepay`, `vnpay`, `zalopay`, `napas`, `google-pay`, `apple-pay`, `stripe`), explicit `provider` on every checkout/create, no hardcoded default, display order from ops config. Omitted/unknown `provider` → 422. Registry and no-default contract in `docs/monetize/overview/monetize-overview.md`.
- Security note (top priority): WAF managed + rate rules on all anonymous routes from P0; Turnstile on signup/login/checkout/beacon; RLS on every business table + app-level `user_id` scoping (both, always); IdP/PSP secrets via environment/vault with quarterly rotation; append-only `audit_log` for plan/role/username/domain/impersonation; PG PITR with quarterly restore drill. Engine or cache never replaces these — keep signature verify + IDOR probes regardless.
- Scale signals: queue backlog sustained → consumer parallelism/partitions first; PG primary CPU >70% at p95 → read replicas for public reads (writes stay primary); top-1% slugs hot → pre-render + longer TTL. Cache layer is throttle/cache only, never truth.
- Deviation log (former defaults → current): Workers Hono API/SSR → Spring Boot service (proxy/cache in front); Neon + Hyperdrive → containerized PG 18.6; Drizzle/`worker/db/*` → Spring Data JPA + Flyway; `wrangler secret`/`.dev.vars` → env/vault + `.env`; `wrangler deploy` → OCI image + `docker run`/`podman run`; Better Auth + password reset → SSO-only session auth; PayOS-default checkout → eight-rail explicit-`provider`; `BEACON_Q`/`POST_QUEUE`/`SCHEDULER` bindings → containerized consumer/scheduler equivalents (logical names kept).

## P0 — scaffold (platform baseline)

| Step | Action | Done when |
|------|--------|-----------|
| P0-1 | Gradle `9.8.1` wrapper + version catalog (Boot `4.1.1`, Kotlin `2.4.21`); multi-module layout; `application.yaml` + env overrides (`dev\|staging\|prod`) | Clean-checkout `./gradlew build` green (lint + unit/slice + Testcontainers PG + ArchUnit + `bootJar`) |
| P0-2 | Wire `compose.yaml`: `app` (Boot jar, port 8080) + `db` (`postgres:18.6`, `pgdata`, `pg_isready`); Flyway V1 identity (users, SSO links, sessions, `audit_log`); `.env.example`; `.gitignore` (`.env`, compose overrides) | Compose boots green under Docker AND Podman; empty-DB migrate with zero manual SQL |
| P0-3 | WAF managed rules + rate rules on anonymous routes; Turnstile keys via env; actuator health + strict `prod` headers/CSP | No secrets in repo or image layers; abuse paths throttled from day one |
| P0-4 | Verify: `./gradlew build`, compose cold boot, `docker run` + `podman run` to ready, SBOM scan, OpenAPI diff | All green; same digest promotable dev → staging → prod |

Delivery plan: `docs/platform/delivery/scaffold-platform.md`. Gates: `docs/platform/delivery/release-gates.md`.

## P1 — share + SEO (link.me parity, researched from link.me/faq 2026)

| Step | Action | Done when |
|------|--------|-----------|
| P1a-1 | SSO-only session auth on PG (RLS on, least-privilege app role; sessions + revoke + 30-day delete flow); password endpoints dead | Signup/login/logout works locally; RLS blocks cross-user reads at DB level; credential endpoints 404/`auth_disabled` |
| P1a-2 | `profiles{user_id, username UNIQUE lowercase, display_name, bio, avatar_key, theme_id, is_public, seo_title, seo_desc}` + reserved slugs (`app,api,admin,static,r,cv,login,signup,pricing,blog,shop`) in app + DB check | Duplicate/uppercase/reserved username rejected |
| P1a-3 | Username-change 301 table; free plan = unlimited links + badge | Old URL 301s to new |
| P1b-1 | `profile_blocks{profile_id, type, position, payload_json, is_visible, visible_from?, visible_until?}`, type ∈ `link\|social\|gallery\|video\|music\|event\|form\|product\|course\|tip\|resume\|contact\|embed` | Superset exists, P2 needs no type migration |
| P1b-2 | Dashboard CRUD + drag-order (`position` persist) + link sizes (small/medium/big/button) + carousel + thumbnails | Reorder survives reload |
| P1b-3 | Themes as tokens (token sets: bg/text/font, video avatar; animated links + own logo = pro gate; no custom CSS upload) | Token switch restyles page, no CSS injection possible |
| P1b-4 | Avatar upload: 2MB cap + magic-byte check → random blob key | Non-image rejected, key unguessable |
| P1c-1 | Spring SSR `/:username`, `/:username/cv`: title/meta/canonical/OG/Twitter/JSON-LD `Person` | Cold crawler (curl, no JS) sees tags |
| P1c-2 | Cache-first: `public, s-maxage=60-300, SWR=600`, purge on publish; single-flight lock per slug (miss storm guard); `robots.txt` + `sitemap.xml` (only `is_public=1`) | Cache-hit >95% at gate; publish visible <2 min, private excluded |
| P1d-1 | Beacon `POST /api/t/:username` → ingest (10min dedupe) → hourly consumer upserts `profile_views{day,views,interactions}` + `link_clicks{day,count}`; raw NDJSON to blob (90d TTL); never direct PG writes from beacon | Zero PG writes per hit; consumer lag <1h at gate |
| P1d-2 | Derive views/clicks/engagement/period-compare/source-city (geo header + Referer)/per-link; realtime-30min pro-only via ephemeral window; QR = same URL | Dashboard shows link.me-parity numbers |

Delivery plans: `docs/platform/delivery/account-access.md` (P1a); link-to-bio buildable plans `docs/link-to-bio/profile/public-profile-backend-plan.md` + `docs/link-to-bio/blocks/link-blocks-backend-plan.md` + `docs/link-to-bio/appearance/themes-avatar-database-plan.md` + `docs/link-to-bio/appearance/themes-avatar-backend-plan.md` + `docs/link-to-bio/appearance/themes-avatar-frontend-plan.md` + `docs/link-to-bio/profile/verification-followers-database-plan.md` + `docs/link-to-bio/profile/verification-followers-backend-plan.md` + `docs/link-to-bio/profile/verification-followers-frontend-plan.md` (P1a–P1b), `docs/link-to-bio/public-page/share-seo-database-plan.md` + `docs/link-to-bio/public-page/share-seo-backend-plan.md` + `docs/link-to-bio/public-page/share-seo-frontend-plan.md` + `docs/link-to-bio/public-page/deep-links-mobile-database-plan.md` + `docs/link-to-bio/public-page/deep-links-mobile-backend-plan.md` + `docs/link-to-bio/public-page/deep-links-mobile-frontend-plan.md` (P1c), `docs/link-to-bio/analytics/profile-analytics-database-plan.md` + `docs/link-to-bio/analytics/profile-analytics-backend-plan.md` + `docs/link-to-bio/analytics/profile-analytics-frontend-plan.md` + `docs/link-to-bio/analytics/audience-insights-database-plan.md` + `docs/link-to-bio/analytics/audience-insights-backend-plan.md` + `docs/link-to-bio/analytics/audience-insights-frontend-plan.md` + `docs/link-to-bio/analytics/realtime-global-database-plan.md` + `docs/link-to-bio/analytics/realtime-global-backend-plan.md` + `docs/link-to-bio/analytics/realtime-global-frontend-plan.md` + `docs/link-to-bio/analytics/earnings-dashboard-database-plan.md` + `docs/link-to-bio/analytics/earnings-dashboard-backend-plan.md` + `docs/link-to-bio/analytics/earnings-dashboard-frontend-plan.md` + `docs/link-to-bio/analytics/mobile-pwa-database-plan.md` + `docs/link-to-bio/analytics/mobile-pwa-backend-plan.md` + `docs/link-to-bio/analytics/mobile-pwa-frontend-plan.md` (P1d). MIGRATION NOTE: the former platform delivery plans `bio-profile.md`, `bio-public.md`, `bio-analytics.md` (under the former `platform/delivery/` path) were removed after their buildable content migrated into the feature plans above; phasing order stays in `docs/platform/delivery/execution-overview.md`.

## P2 — resume + shop + leads + portability (link.me shops/courses parity, VN-adapted)

| Step | Action | Done when |
|------|--------|-----------|
| P2-1 | `documents{profile_id, template_id, snapshot_json}` ← `resume` block; token templates screen + `@media print` A4; `window.print()` only | Reprint after profile edit unchanged |
| P2-2 | Shop `/:username/shop`: `products{profile_id, kind: merch\|digital\|paywall\|course\|tip\|service\|linkout, title, price_vnd, file_key}`; merch = POD listing (no inventory); digital/paywall = signed URL after paid `orders{product_id}` | Paid buyer gets one-time download link |
| P2-3 | Courses: `course_sections/lessons{video_key, materials}` + buyer-email claim + progress; tips = fixed/custom-amount product | Student resumes last lesson |
| P2-4 | Leads: `form` block + Connect → `leads{profile_id, email, source, consent_at}` + CSV export (campaigns deferred to P4) | Export contains consent timestamps |
| P2-5 | JSON import/export profile+blocks+documents; LinkedIn/GitHub adapters; fixture profile for migrations | Round-trip lossless |
| P2-6 | `custom_domains{hostname, profile_id, verified}` via challenge TXT, serve same SSR | Unverified host never serves another user's page |

Delivery plans: `docs/platform/delivery/resume-documents.md`, `docs/platform/delivery/resume-render-share.md`, `docs/platform/delivery/resume-portability.md` (P2-1/P2-5), `docs/platform/delivery/shop-catalog.md` (P2-2/P2-3), `docs/platform/delivery/leads-capture.md` (P2-4), url-shortener triples `docs/url-shortener/links/short-links-database-plan.md` + `docs/url-shortener/links/short-links-backend-plan.md` + `docs/url-shortener/links/short-links-frontend-plan.md` + `docs/url-shortener/links/qr-codes-database-plan.md` + `docs/url-shortener/links/qr-codes-backend-plan.md` + `docs/url-shortener/links/qr-codes-frontend-plan.md` + `docs/url-shortener/tracking/link-analytics-database-plan.md` + `docs/url-shortener/tracking/link-analytics-backend-plan.md` + `docs/url-shortener/tracking/link-analytics-frontend-plan.md` (short links/QR/analytics; P2-6 custom hosts stay a pending account-major triple). MIGRATION NOTE: the former platform delivery plan `links-domains.md` (under the former `platform/delivery/` path) was removed after its url-shortener content migrated into the feature triples above.

## P3 — monetize VN (eight rails, explicit provider, no default)

| Step | Action | Done when |
|------|--------|-----------|
| P3-1 | `orders{user_id, product_id?, plan?, amount_vnd INT, status, orderCode UNIQUE}`; `POST /api/billing/checkout` with explicit `provider` → numeric `orderCode` + that rail's handoff (VND integers, same-origin return/cancel); omitted/unknown `provider` → 422 | Sandbox handoff opens, amounts exact, no-default drill green |
| P3-2 | Per-rail verify (HMAC/signature scheme) → idempotent grant (`paid` once) → entitlements; confirm/runbook per rail after deploy; local test via public tunnel to dev service | Replay/tamper tests: single grant, 401 on bad sig |
| P3-3 | Plans: free 1 profile/unlimited links/tips/basic + badge; pro unlimited + domain + realtime + AI JD-tailor (AI capability + credit cap, `ai_generations{...credits}`) | Gate enforced server-side, not just UI |
| P3-4 | `orgs/org_members` skeleton for agency seats (full roster in P4) | Table exists, no orphan queries |

Delivery plans: `docs/platform/delivery/monetize-foundation.md` (kernel), `docs/platform/delivery/monetize-rails.md` (eight rails), `docs/platform/delivery/monetize-lifecycle.md` (plans/renewals/receipts/coupons/refunds). Growth majors (`monetize/marketing`, `affiliate`, `advertising`, `sales`, `data-analysis`, `user-behavior`) are design-complete backlog — need plan updates before build, never stubbed earlier.

## P4 — publish + automate + agency (needs OAuth vault + scheduler + audit; do NOT build in P1–P3)

| Step | Action | Done when |
|------|--------|-----------|
| P4-1 | `social_connections` OAuth per network (YT/IG/TikTok/FB/X/Threads/LinkedIn; IG = Business) with encrypted vault + key rotation; per-plan connection caps | Tokens never logged, refresh works |
| P4-2 | Composer + per-platform caption limits → `scheduled_posts{video_key, caption_json, run_at, status}` via scheduler + fan-out jobs; poll status, never `await` publish in request | Failed post retryable with state |
| P4-3 | IG automations: `automation_rules{kind: comment\|dm, match, reply_templates, delay_s}` + watcher; public-reply + 1-link DM → `leads`; contacts quota enforced pre-send | 100/yr-style quota blocks over-send |
| P4-4 | Resend broadcast to active `leads` + `campaigns{stats_json}` webhook (no segments/scheduling v1) | Delivered/Open/Click recorded |
| P4-5 | Agency: invite-by-username, roles owner/manager/member (+ client), scoped Act-as-user + audit log, leaderboard (views/clicks/CTR/sales) | Impersonation fully audited |

Delivery plans: `docs/platform/delivery/publish-automate.md` (P4-1–P4-4; first split evaluation on measured backlog), `docs/platform/delivery/agency-teams.md` (P4-5). E4 scope audit: zero P4 tables/jobs/endpoints in E0–E3 images.

## P5 — scale-out (only after P3 gate passes at 100k concurrent)

| Step | Action | Done when |
|------|--------|-----------|
| P5-1 | PG read replicas for public reads (writes stay primary); datasource read/write split | Replica lag <1s at 1M concurrent reads |
| P5-2 | Pre-render top-10k slugs to blob + raise TTL to 300s; single-flight already in P1c | Cache-hit >95%, origin req/s flat as traffic 10x |
| P5-3 | k6 gates: 10k (P1 exit) → 100k mixed (P3 exit) → 1M read-heavy + 100k mixed (P5 exit), p95 <500ms reads / <1s checkout | No phase ships past a red gate |
| P5-4 | Quarterly PG restore drill + secret rotation (vault keys, PSP keys, datasource creds) | Restore + rotation runbook green |

Delivery plan: `docs/platform/delivery/scale-out.md`. Overview: `docs/platform/delivery/execution-overview.md`.

## Security checklist (run before each public deploy)

| # | Probe | Pass |
|---|-------|------|
| S-1 | IDOR: `userA` reads/writes `userB profile_id/block/order` | 403 everywhere |
| S-2 | XSS: script/iframe/event-handler in bio/link/payload/embed | Stripped, CSP holds |
| S-3 | Webhook: bad sig / replay / amount tamper | 401 / single grant / reject |
| S-4 | Uploads: fake image, SVG/HTML, 5MB file | Rejected or neutralized |
| S-5 | Abuse: rapid signup/login/checkout/beacon, unverified domain | Throttled (Turnstile+WAF), domain refused |
| S-6 | RLS: direct PG connection as app role reads another user's row; `audit_log` covers grant/role/domain/impersonation | Denied at DB + logged |
| S-7 | Commands: Flyway migrate on staging mirror, `./gradlew build`, compose cold boot, `docker run` + `podman run` to ready, k6 gate for the milestone | Green + manual pass above |
