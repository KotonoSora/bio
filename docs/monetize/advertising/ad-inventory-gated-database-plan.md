# Ad Inventory Gated Database Plan (placements + flights + creatives — designed, not built)

PLAN ref: none — beyond P0–P5, P4-growth; needs a plan update before build. GATED: design-complete only, never in P1–P3 images. BA source: `docs/monetize/advertising/ad-inventory.md`. Server design in the ad-inventory backend plan; gallery/opt-in screens in the frontend plan (both gated, split next to this file). Backlog scope per `docs/platform/delivery/execution-overview.md` (P4-growth, never stubbed earlier).

## Purpose

Hold the buildable inventory schema so that, at the plan update, a developer creates it with no further decisions — and until then, nothing exists. Placements (what we sell), flights (what runs), creatives (what shows): three tables, one lifecycle discipline, brand safety as rows (exclusion lists) rather than prose.

## Users

- Backend developer (future): runs this script once at plan update; flight lifecycle only through versioned inserts + status flips.
- QA (now): runs the absence drill every release; (future) catalog/safety/opt-in drills.
- Advertiser/creator (future): buys direct-deal flights; opts inventory in/out per page.

## Functional requirements

1. Present state: zero ad tables, zero ad endpoints, zero ad jobs. Absence verified per release (Acceptance drill), not assumed.
2. Table `ads.ad_placements` (future) — placement catalog, versioned rows not config files (auditable changes): `id`, `slot` (platform slot key or `creator-infeed`), `position` (`below-profile-header|end-of-blocks` for creator units; exactly one ad unit per page in v1 — enforced in serving, counted here), `format` (image + headline + CTA; no autoplay video/interstitials/pop-unders in v1 — format closure is a CHECK), `size_caps`, `labeling` (always `Sponsored`, distinct, non-dismissable-as-content), `version`, timestamps. Catalog edits insert new versions (running flights reference pinned versions — a catalog change never rewrites a live flight's terms).
3. Table `ads.ad_flights` (future) — `id`, `advertiser_ref`, `placement_ids[]`, `starts_at/ends_at`, `cap_impressions?`, `cap_spend?`, `status` (`draft|scheduled|live|paused|ended` — launch requires caps + window; pause instant; ended terminal), `pricing_model` (`flat-fee` v1; measurement already counts impressions + clicks so CPM/CPC switch needs no re-instrumentation), `user_id` (owner, RLS). Launch/paused/ended transitions audited.
4. Table `ads.ad_creatives` (future) — `id`, `flight_id` FK cascade, `assets` (blob keys, avatar-grade validation: size/type/magic-byte), `headline`, `cta_url` (`https:` allowlist), `approval` (`pending|approved|rejected` — ops pre-approval mandatory, no self-serve upload in v1), `takedown_at?` (instant takedown = audited status action pausing the creative everywhere within minutes).
5. Brand-safety rows (future): category exclusion lists both directions (advertiser↔creator blocks) + prohibited-vertical list (adult, gambling, misleading earnings, crypto-yield — VN-sensitive, legal-finalized at plan update) stored as data (`ads.brand_safety_rules`), enforced server-side at decisioning (never policy prose).
6. Creator opt-in (future): per-profile toggle (default OFF) + category declaration — stored on the profile row extension or a `creator_ad_prefs` row (decided at plan update; either way owner-scoped, RLS). Opt-out mid-flight stops serving immediately, no penalty. Free-plan opt-in allowed; pro never forced (rule, tested).
7. Separation structural: ad traffic flows only through the measurement pipeline (next slices) — creator `profile_views`/`link_clicks` tables gain zero ad rows by the absence of any write path (proven by the separation drill, not by convention).

## Data / API touches

- DDL now: none — assertion: `grep -rn "CREATE TABLE ads\.\|ad_flights\|ad_creatives\|ad_placements" src/ db/ docs/monetize/advertising/*gated* 2>/dev/null` over pre-plan-update tags returns nothing except these gated design files. Future: three tables + safety rules per rules 2–5 (full `CREATE TABLE` written at plan update from this design — column lists above are the source of truth).
- RLS posture (future): advertisers read own flights; creators read own opt-in state; catalog/config restricted to ad-ops role; creative assets in blob storage with random keys.

## Acceptance criteria

- Absence drill (every release until plan update): schema + OpenAPI + source diffs show zero ad surface.
- Design drill (at plan update): tables created once from rules 2–5; catalog versioning proven (live flight pinned across catalog edit); takedown SLA demonstrated.
- Safety drill (at build): prohibited-vertical rejected at approval; bidirectional category blocks respected; takedown effective within minutes.
- Opt-in drill (at build): default-off; mid-flight opt-out immediate; pro never forced.
- Separation drill (at build): zero ad rows in creator analytics under load.

## Out of scope

- Decisioning/counting/fraud (serving-measurement plans); earnings/payouts (revenue-share plans); retargeting segments (retargeting plans); affiliate commissions (affiliate majors); promo discounts; creator analytics; third-party ad-network SDKs + self-serve tooling (deferred); P5.

## Open questions

- Q1: Prohibited-vertical list finalization with legal (BA Q1 — blocking creative approval policy)?
- Q2: Flat-fee vs CPM/CPC at launch (BA Q2 — measurement supports either)?
