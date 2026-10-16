# Audience Insights - User Stories and Business Rules (feature 13)

Phase 1 BA · Rules `BR-xx` are local to this feature and quoted by every later file. Base pipeline rules live in `docs/01-requirements-ba/12-profile-analytics/02-user-stories-and-business-rules.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | new vs returning splits | I know who comes back | Must |
| US-02 | Creator | device class and browser family splits | I know how visitors arrive | Must |
| US-03 | Creator | country splits | I know where visitors come from | Must |
| US-04 | Creator | referrer domains grouped as social/search/direct | I know which channels work | Must |
| US-05 | Creator | UTM campaign rows with fallbacks when untagged | campaigns are comparable, never dead-ends | Must |
| US-06 | Creator | per-link CTR, rank, and weekly Movers | I see which links earn taps | Must |
| US-07 | Creator | previous-period overlays with weekday alignment | Monday spikes are not misread | Must |
| US-08 | Pro creator | 365d history | long campaigns stay analyzable | Should |
| US-09 | Visitor | to be counted exactly as before | no new tracking follows me | Must |
| US-10 | Creator | honest `not enough data` states | I never read noise as signal | Must |

## Business rules

- **BR-01 Same pipeline only.** No new beacon, queue, or endpoint surface for ingestion; segments derive from the same messages during the existing hourly rollup. Queue payload vocabulary is extended additively (`utm`, `browser`, `returning`) per feature 12's governance rule. Zero per-hit PG writes still holds.
- **BR-02 Segment derivation.** Dimension keys: `device` = `{class}-{browser}` (`mobile-chrome`); `geo` = lowercase ISO 3166 alpha-2 or `unknown`; `referrer` = normalized domain or `direct`; `utm` = `{field}:{value}` (`campaign:launch`); `returning` = `new|returning`. Bot-filtered hits never reach projection (inherited decision, no second filter).
- **BR-03 New vs returning.** `returning` = the visitor key was seen for this profile within 30 days (`seen:{profileId}:{visitorKey}` in cache, TTL 30d). Detection uses the HMAC construction with a monthly-rotated salt (separate from the daily dedupe salt — matching must span the window). The boolean rides the queue; no visitor id ever reaches PG. After a salt rotation, boundary visits count `new` (privacy-first trade-off, stated once in copy). Profiles never share visitor memory (no cross-profile stitching).
- **BR-04 Referrer fallback chain.** Attribution order: UTM field -> normalized referrer domain -> `direct` when absent. `social`/`search`/`direct` classes are read-time rollups over domain keys via a shipped map; unknown domains roll into `other`. No `unknown` dead-end exists where a fallback is defined.
- **BR-05 CTR canonical definition.** `ctr_x1000` (per-mille integer) = windowed clicks ÷ windowed profile views for the same link and window, computed from aggregates only (never raw logs), round-half-up. Every CTR display everywhere divides these two aggregates; mismatched-window division fails review by rule.
- **BR-06 Compare overlays.** Every metric ships absolute + percentage deltas versus the equal-length previous period; overlay charts render only when the needed span is retained (`dataStart` field drives enablement). When a comparison window is unavailable, the toggle disables with a one-line reason — never a zero-filled false comparison. DEVIATION FLAGGED (first use): year-ago overlay is deferred in v1 — 90d/365d retention cannot cover a 365-day lookback, so the toggle ships disabled until the platform retention policy grows (`00-platform-baseline`). Weekday alignment is flagged (`aligned:true`) and labeled in-UI when day counts differ.
- **BR-07 Plan windows.** Free: 7/30/90d; pro: adds 365d. Clamp is server-side: out-of-plan range -> `422 range_clamped` + `allowed` (feature 12's code). Direct API calls clamp identically — no bypass via deep links. Entitlement is checked per request (never cached beyond the platform snapshot's invalidation).
- **BR-08 Retention job.** Nightly `AggregateRetentionJob` deletes rows older than each profile's plan window across base + audience tables; dry-run count (`retention.would_delete`) precedes deletion. Downgrade re-clamps reads on the next request; aged rows are never resurrected by re-upgrade.
- **BR-09 Privacy ceiling.** No IP or visitor-id column exists in any table; projection functions accept a compile-time whitelist DTO (a PII field cannot flow without a type change, which review catches). Geo remains country-grain (DEVIATION CARRIED from feature 12 Q-04). Raw logs age out on the base 90-day TTL.
- **BR-10 UTM hygiene.** Values normalized: lowercased, charset `[a-z0-9._-]` kept, others stripped, capped at 64 chars and 3 fields; over-length values truncated + counted. Read trims to top-25 keys + server-computed `Other (n campaigns)`.
- **BR-11 Honesty states.** CTR under 30 views in-window renders `not enough data` (threshold copy states the rule once). A profile with UTM-untagged links sees `Tag links with utm_campaign to unlock this view` plus an editor link — not an empty chart.
- **BR-12 No-regression.** Beacon payload size and queue throughput unchanged versus feature 12; zero per-hit PG writes holds under load; burst tightening at intake automatically reduces segment work (no new abuse surface).
- **BR-13 Same numbers.** Audience values match the overview tab exactly at the same `generatedAt` (same aggregates, same windows, same clamps).
- **BR-14 Device read for feature 11.** `dimension=device` serves feature 11's UA-mix banner-eligibility card; no separate read exists.
- **BR-15 CTR reuse for feature 15.** Earnings displays consume `ctr_x1000` from `link_perf`; no second formula, no re-derivation.
