# Short Links Backend Plan (mint + resolve + suspend — server build steps)

PLAN ref: P2 delivery (short links in P2 scope per `docs/PLAN.md`; BA P4-growth proposal noted, PLAN governs) + S-1/S-3?/S-5/S-6/S-7 — S-3 N/A (no webhooks here), bot/abuse posture throughout. BA source: `docs/url-shortener/links/short-links.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Schema in `docs/url-shortener/links/short-links-database-plan.md` (single table, written once). Manager screens in the short-links frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the platform's single redirector: unguessable minting, millisecond resolve with honest counting semantics, destination safety at mint AND resolve, lifecycle transitions, and budgets that keep shorteners from becoming spam infrastructure. Follow steps 1–9 in order. The resolve hot path is the system's fastest anonymous endpoint — every step below protects its latency budget first.

## Users

- Backend developer: executes steps 1–9 in package `com.app.links.shortener` (new `links` Gradle module, `links` schema).
- QA: runs collision, counting, safety, expiry, abuse, and hot-path drills.
- Operator: mints within quota; abuse reviewer suspends once, killing every link to the destination.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`links` module). Monolith pros: mint validation + insert + audit commit atomically; resolve joins blocklist + status locally; one RLS set. Microservices cons avoided: a redirector service would put the highest-QPS anonymous path on a network hop to policy truth (added p99 + invalidation chatter) for a table whose growth is quota-bounded. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire (P5 edge-redirector only on measured resolve pressure). Starters: web, validation, data-jpa, security, scheduling, cache, actuator, micrometer. Load posture: cache-fronted PK lookups (CDN + cache layer, PG as truth), stateless resolve (horizontal replicas share nothing), 302 `no-store` (counting correctness over edge-hit rate — deliberate, documented).
2. Step 1 — schema: run the short-links database plan script once (after profile/blocks scripts for FK-free ordering simplicity). No other DDL in this slice.
3. Step 2 — `service/CodeGenerator.kt` (pure, property-tested): random base62, 7+ chars, CSPRNG (never sequential, never timestamp-derived — enumeration resistance is the requirement); vanity path validates availability (PK probe) + length floors + profanity/reserved screening + per-account scoping (vanity never global-first-come for trademarked terms — screening list + Q1 process). Codes immutable once minted (no rename endpoint exists — absence asserted in review).
4. Step 3 — `POST /api/v1/links` (mint): validate destination (`https:` + blocklist check at mint: phishing/malware + platform abuse list → `422 destination_blocked`) → enforce per-account mint quota (`429 link_quota_exhausted` with reset window) + per-IP rate limit + Turnstile (minting is spam-shaped — Turnstile is cost/abuse defense) → generate code (PK-collision retry loop, bounded 5 attempts, then `503` + alert — never an unbounded loop) → insert `live` → audit. Bulk minting exists ONLY as an audited ops action (public loop → 403 + audit, no bulk endpoint for creators).
5. Step 4 — `PATCH /api/v1/links/:code` (destination/expiry ONLY — code, kind, ownership immutable; attempts on other fields → `422 immutable_field`) and `DELETE /:id` (tombstone: `expires_at=now()`, row + clicks retained; hard delete refused while analytics rows reference the code — service checks, tombstone is the only path).
6. Step 5 — resolve `GET /go/:code` (+ `/r/`, `/a/` aliases through the same table; affiliate/ad refs ride as signed query params, never separate tables): order — cache lookup → hit serves cached 302; miss → single PK read (status + destination + expiry) → blocklist RE-CHECK (re-listed after mint resolves to suspension, never to the destination) → state machine: live → `302` with `Cache-Control: private, no-store` (every open re-hits the counter — cached 301s undercount, the classic bug this forbids); explicit-permanent vanity → `301` (documented uncounted-after-first); expired → branded gone-page `410` (creator contact, never homepage redirect); dormant with fallback → fallback, else gone-page; suspended → suspension page (evidence rows preserved). Then (never before): enqueue the hit asynchronously (analytics pipeline owns counting — zero PG writes on this path, asserted in query logs).
7. Step 6 — ops endpoints: `POST /api/v1/ops/links/report` (Turnstile-protected, reporter-agnostic) → review queue; `POST /api/v1/ops/links/:code/suspend` (`ROLE_OPS`, reason mandatory) → status flip + audit + purge of cached resolve entries (suspension effective within minutes, proven by the safety drill). Expiry sweep `@Scheduled` materializes `expired` (resolve-time computation is the source of truth; the sweep is the read optimization).
8. Step 7 — bot/abuse hardening (this slice IS abuse-shaped infrastructure): Turnstile on mint/report; WAF + per-IP rate rules on resolve (S-5); velocity signals shared with the analytics quarantine (floods hold out of aggregates, resolve still serves — availability and honesty computed separately on purpose); shortener-specific abuse counters (`links.mint_rejected{reason}`, `links.resolve_suspended_hit`).
9. Step 8 — standardized API: additive-only `/api/v1/links`, RFC 9457 problems with stable codes (`destination_blocked`, `link_quota_exhausted`, `immutable_field`, `link_not_found` (404, tombstoned included — no state oracle), `link_gone` (410 with contact payload)); OpenAPI diff gate; owner 403s on foreign codes + RLS deny (S-1/S-6).
10. Step 9 — tests + container + hot-path proof: collision matrix (username `go/r/a` rejected at profile layer; code never resolves as profile and vice versa); counting test (1,000 opens of 302 = 1,000 hits; 301 vanity asserts uncounted-after-first); safety test (re-listed destination blocked at resolve); expiry/dormant matrix; abuse test (mint-flood throttled; public bulk 403 + audit); hot-path benchmark (resolve p95 with counting on vs off — delta ≈ 0, enforced as a gate). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (only writer here). Endpoints: `POST /api/v1/links` → `201 {code}` / `422` / `429`; `PATCH /:code` (destination/expiry only); `DELETE` (tombstone); public `GET /go/:code` (+ `/r/`, `/a/`) → `302` / `301` / `410` / suspension page (all `private, no-store` except the documented 301); ops report/suspend (audited). Metrics: `links.mint_rejected`, `links.resolve_suspended_hit`, resolve p95.

## Acceptance criteria

- Collision drill: namespace matrix green both directions.
- Counting drill: 302 counts every open; 301 asserts the documented undercount.
- Safety drill: mint-time + resolve-time blocklist both fire; report suspends platform-wide within minutes, evidence intact.
- Expiry drill: gone-page with contact (never homepage); dormant uses fallback or gone-page, never blind 404.
- Abuse drill: floods throttled; public bulk impossible; tombstones preserve history.
- Hot-path drill: counting on/off delta ≈ 0; zero PG writes per resolve.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Table DDL (database plan above); manager screens (frontend plan); QR render log (qr database plan); hit aggregates + fraud filters (analytics plans); username rules (profile database plan); affiliate/ad measurement (consumers); custom short domains (future); P4/P5 except the recorded edge-redirector trigger.

## Open questions

- Q1: Vanity trademark process (see database plan Q1)?
- Q2: Interstitial warnings for unlisted-untrusted destinations (see database plan Q2)?
