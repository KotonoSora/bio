# Profile Analytics - User Stories and Business Rules (feature 12)

Phase 1 BA · Stories seed `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Visitor | my visit counted transparently (no login, no consent beyond the platform notice) | analytics exists without friction | Must |
| US-02 | Creator | views, clicks, and engagement totals with period deltas | I learn what works period over period | Must |
| US-03 | Creator | per-link click counts with share | I know which links earn attention | Must |
| US-04 | Creator | source and country breakdowns | I learn where traffic comes from | Must |
| US-05 | Creator | a daily series for the chosen range | I see trends, not just totals | Must |
| US-06 | Creator | honest hourly freshness labels | I never mistake aggregates for live numbers | Must |
| US-07 | Free creator | a locked realtime teaser instead of errors | I understand the pro upgrade path | Should |
| US-08 | Pro creator | extended ranges (to 365d) | long-term comparisons match my plan | Should |
| US-09 | Ops | lag, abuse, and rejection dashboards | spikes and abuse are visible before creators complain | Must |

## Business rules

- **BR-01 Beacon semantics.** `POST /api/t/:username` is fire-and-forget: `202 {ok:true}` on every accepted path (no oracle for tampered ids), `400` only for malformed shape, `429` + `Retry-After` when throttled. The handler performs zero PG writes — asserted by query-log test, not convention.
- **BR-02 Dedupe.** 10-minute per (visitor, page/block) window in the cache layer before enqueue; refreshes and double-taps collapse. Visitor key = `HMAC(salted ip+ua, dailySalt)`; raw IPs never leave intake scope. The dedupe key family feeds new-vs-returning derivation in feature 13.
- **BR-03 Zero per-hit PG writes (structural).** No aggregate row exists until the hourly rollup; raw hits are NDJSON in blob storage (90-day TTL), never PG. Any dashboard query joining raw logs is a layering violation.
- **BR-04 Rollup.** Hourly at :05; idempotent additive upserts on the natural keys `(profile_id, day)` and `(profile_id, block_id, day)`; a replayed batch increments exactly once; partial failure retries the hour (upsert math makes retries additive-correct). Lag target < 1 h at gate.
- **BR-05 Bot filtering.** Known-crawler UA denylist is default-on, configurable; denials are counted `beacon.rejected{bot}` and never stored. No "include bots" toggle exists in v1; if the default flips, it is config + query param, never a new endpoint.
- **BR-06 Privacy ceiling.** Country-grain geo only (edge header); no raw IPs at rest anywhere; no cross-profile visitor stitching; raw logs age out on the blob TTL; aggregates are owner-only.
- **BR-07 Ranges and clamping.** Free: 7/30/90d. Pro: adds 365d. Out-of-plan range returns `422 range_clamped` with `allowed`; the picker snaps back with "Available on your plan" text — never a dead-end.
- **BR-08 Series honesty.** Server-side downsampling via `?points=` (cap 120); totals are always exact regardless of points. Every widget footer shows `generatedAt` + an "hourly data" lag label once per group.
- **BR-09 Deleted blocks keep history.** `link_clicks.block_id` is a logical reference (no FK). Titles resolve live from the profile service; a deleted block shows `Deleted link` with its historical counts intact.
- **BR-10 Rename safety.** Beacon usernames resolve through the profile service (redirects followed) to the current `profile_id`; unknown targets are dropped with `202` and counted `beacon.rejected{unknown_target}`.
- **BR-11 QR-as-view.** QR codes encode `/:username`; scans arrive as ordinary deduped views. No QR instrumentation, no table.
- **BR-12 Same numbers everywhere.** Identical endpoints and clamps on desktop, mobile browser, and the installed PWA (feature 11); no client-side aggregation beyond display formatting.
- **BR-13 Realtime teaser.** Free accounts see a locked realtime card and issue zero realtime fetches; the endpoint and its 403 enforcement are owned by feature 14 (forward contract).
- **BR-14 Banner and prompt events.** `banner_show`/`escape_tap` (feature 11) increment `interactions`; install-prompt outcomes are accepted on the beacon and counted into observability metrics, with no aggregate table in v1.
