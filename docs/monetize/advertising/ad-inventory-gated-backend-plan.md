# Ad Inventory Gated Backend Plan (catalog + flights + safety — buildable at plan update)

PLAN ref: none — P4-growth, needs plan update. GATED. BA source: `docs/monetize/advertising/ad-inventory.md`. Storage contract in `docs/monetize/advertising/ad-inventory-gated-database-plan.md` (tables designed, nothing created now). Screens in the inventory frontend plan (splits next to this file).

## Purpose

Hold the buildable server design for direct-deal ad inventory: placement catalog versioning, flight lifecycle, creative pre-approval, brand-safety enforcement, creator opt-in, and takedown. Until the plan update, this file's only executable content is the absence gate.

## Users

- Backend developer (future): ships catalog/flight/creative endpoints + safety enforcement in package `com.app.ads.inventory` (GATED).
- QA (now): asserts absence; (future) catalog/safety/opt-in/takedown drills.
- Advertiser/creator/ops (future): buys, opts in, approves, takes down.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith catalog module (package `com.app.ads.inventory`, GATED). Monolith pros: flight launch (validate + scrub + insert + audit) commits atomically; catalog reads share the RLS baseline. Microservices cons avoided: an ads service for catalog-volume traffic adds auth boundaries with no ≥10x asymmetry. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, data-jpa, security, scheduling, actuator, micrometer. Load posture: catalog reads are versioned-config cacheable; gallery lists are public-predicate index scans + edge cache.
2. Step 1 — placement catalog (GATED): versioned rows (never config files); platform slots + one creator in-feed unit per page (position below-header or end-of-blocks); format closure enforced (image + headline + CTA; video/interstitial/pop-under rejected by CHECK + validator); labeling always `Sponsored`, distinct, non-dismissable-as-content. Checkout/payment surfaces provably ad-free (grep + review gate — an ad slot inside checkout fails the build).
3. Step 2 — flight lifecycle (GATED): `draft → scheduled → live → paused|ended` with caps + window required for launch; pause instant (single audited action); ended terminal; flat-fee pricing v1 (measurement counts both models, so pricing switches without re-instrumentation). Ops flight/creative CRUD + approve/pause endpoints (audited, `ROLE_ADOPS`); NO advertiser self-serve writes in v1 (absence of self-serve endpoints asserted, not just undocumented).
4. Step 3 — brand safety (GATED, server-enforced): bidirectional category exclusions + prohibited-vertical list as data; creative pre-approval mandatory per flight (unapproved creative never servable — decisioning joins approval state, it doesn't trust it); takedown pauses a live creative everywhere within minutes (status action + cache purge + audit).
5. Step 4 — creator opt-in (GATED): per-profile toggle default OFF + category declaration; opt-out mid-flight stops serving immediately (decisioning reads current opt-in per render, never a stale copy); free-plan opt-in allowed with identical economics (no plan-based rate discrimination); pro never forced (rule + test).
6. Step 5 — abuse: Turnstile + WAF/rate on gallery/report paths; creative asset uploads validated (size/type/magic-byte, avatar-grade); report-abuse per creative feeding the takedown queue.
7. Step 6 — gate enforcement NOW: zero ad tables/endpoints/jobs/scheduled tasks in current images — grep + migration audit fails the tag on any `ad_flights|ad_creatives|ad_placements|/api/a/|/api/v1/ops/ad-` surface before the plan update.

## Data / API touches

- Schema: database plan above (none now). Endpoints (GATED, absent from current OpenAPI): ops flight/creative CRUD + approve/pause; creator opt-in toggle + category declaration; public ad-choice/why-this-ad disclosure; `POST report` per creative. CTA links `https:` + `rel="noopener sponsored ugc"`, routed through the measurement redirect (never direct).

## Acceptance criteria

- Absence drill (now): zero ad surface in schema/OpenAPI/source on pre-update tags.
- Catalog drill (at build): placements render exact format/position/labels desktop + mobile; checkout provably ad-free.
- Safety drill (at build): prohibited rejected; bidirectional blocks respected; takedown within minutes.
- Opt-in drill (at build): default-off; mid-flight opt-out immediate; pro never forced.
- Separation drill (at build): zero ad rows in creator analytics under load.
- Auth drill (at build): cross-advertiser 403 + RLS deny; creators cannot touch flights.

## Out of scope

- Catalog/flight/creative DDL (database plan above); gallery/opt-in screens (frontend plan); decisioning/counting/fraud (serving-measurement plans); earnings/payouts (revenue-share plans); retargeting; affiliate/promo; creator analytics; third-party SDKs + self-serve (deferred).

## Open questions

- Q1: Prohibited list legal finalization (blocking approval policy)?
- Q2: Flat-fee vs CPM/CPC (measurement supports either)?
