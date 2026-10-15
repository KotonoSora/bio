# Short-Link Analytics (counting, fraud filters, readouts)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `links/short-links.md` for placement). Minting/resolution there and in `links/qr-codes.md`; this doc owns honest numbers.

## Purpose

Define how short-link opens become trustworthy counts: the hit pipeline, bot/fraud filtering, per-link readouts, and the separation from profile analytics. Advertisers, affiliates, and campaign owners make spend decisions on these numbers, and the redirect hot path must stay millisecond-fast — so counting is async, filtered, and dispute-replayable, never inline and never approximate.

## Users

- Link owner (creator/operator/affiliate/advertiser): *As a Link owner, I want to see opens, uniques, devices, referrers, and scan-vs-click splits per link so that spend decisions use trustworthy counts.* Sees opens, uniques, devices, referrers, and scan-vs-click splits per link.
- Dispute reviewer: *As a Dispute reviewer, I want to replay any billed/reported count from stored evidence so that figures stay dispute-replayable.* Replays any billed/reported count from stored evidence.
- Visitor: *As a Visitor, I want to be counted once per genuine open so that I am never tracked across links into a behavioral profile.* Counted once per genuine open, never tracked across links into a behavioral profile.

## Functional requirements

1. Hit pipeline (the house pattern, mandatory): resolve endpoint validates → redirects immediately → enqueues the hit asynchronously (never direct PG writes on the hot path) → KV-deduped consumer (10-minute window per visitor/link, same discipline as profile beacons) → `link_hits{day, short_code, uniques, opens, scans?, devices, referrers}` aggregates; raw hits to R2 NDJSON with the standard TTL discipline. Redirect latency never waits on counting.
2. Fraud and noise filtering (v1 essentials): bot/crawler exclusion (shared posture decision with creator analytics — billed/affiliate-attributed traffic defaults to the stricter setting), prefetch/prerender suppression (browser speculative loads don't count — validated via purpose headers where present), double-open dedupe (same visitor/link in-window collapses), and velocity quarantine (single-source floods held out of aggregates pending review, same pattern as ad measurement).
3. Scan-vs-click split: QR scans distinguished from link clicks where signals allow (absent referrer + mobile + camera-class user agents → scan-leaning heuristic, labeled as estimated, never asserted); event-pack per-location codes give exact splits regardless of heuristics.
4. Readouts (owner-scoped): per-link dashboard (opens/uniques curve, top referrers, device split, scan share, geo-country where available) plus CSV export; affiliate `/r/` and ad `/a/` consumers read through their own scoped endpoints (same numbers, no cross-link visibility). Aggregates only — raw-hit browsing restricted to ops dispute work.
5. Evidence and transparency: filter-exclusion counts reported alongside billed/reported figures (what was dropped and why); any reported count replays from retained evidence within the TTL window. No metric exists that cannot be decomposed to its underlying hits.
6. Separation: short-link hits never enter creator `profile_views`/`link_clicks` (profile block clicks stay in link-to-bio analytics); profile opens of short URLs (a short link inside a bio block) count once in each system by design, documented as such to avoid double-billing confusion (short-link count = opens; profile count = block click — different events, both true).

## Data / API touches

- New aggregates `link_hits_daily{day, short_code, ...}` (idempotent rebuilds from raw); raw R2 NDJSON per pipeline retention classes. Resolve endpoint enqueues only. All PG via `worker/db/*`; RLS (owners see own links; ops see all for disputes).
- Endpoints: owner `GET /api/v1/links/:code/stats` (+ CSV export), ops dispute replay lookup; affiliate/ad scoped stats reuse their minors' endpoints against these aggregates. Hit ingestion has no public write API beyond the resolve path itself.
- Consent posture follows the analytics model (first-party only, no cross-link identity resolution, declined-consent traffic contributes aggregate-safe counts only).

## Acceptance criteria

- Counting drill: scripted genuine opens (mixed devices/referrers) reconcile hit-for-hit with aggregates; prefetch/bot traffic excluded per the decided posture. *GWT: Given scripted genuine opens with mixed devices/referrers plus prefetch/bot traffic; When aggregates build; Then genuine opens reconcile hit-for-hit and prefetch/bot traffic is excluded per the decided posture.*
- Hot-path drill: resolve latency unchanged with counting enabled vs disabled (benchmarked); queue outage degrades to sweep-repair with no loss and no doubles. *GWT: Given counting enabled vs disabled and a queue outage; When resolve runs and recovery runs; Then resolve latency is unchanged (benchmarked) and outage degrades to sweep-repair with no loss and no doubles.*
- Flood drill: single-source velocity flood quarantined from aggregates with an ops case; legitimate traffic in the same window unaffected. *GWT: Given a single-source velocity flood with concurrent legitimate traffic; When filtering runs; Then the flood is quarantined from aggregates with an ops case and legitimate traffic stays unaffected.*
- Separation drill (under load): zero short-link rows in creator analytics and vice versa; dual-counted bio-block case documented and verified. *GWT: Given short-link and creator analytics under load with a bio-block short URL; When hits are recorded; Then zero short-link rows appear in creator analytics and vice versa, with the dual-counted bio-block case documented and verified.*
- Replay drill: any reported figure replays from evidence within TTL; filter-exclusion math (raw − excluded = reported) holds exactly. *GWT: Given any reported figure within TTL; When replayed from retained evidence; Then it replays exactly with filter-exclusion math (raw − excluded = reported) holding.*

## Out of scope

- Minting, namespaces, and redirect semantics (see `links/short-links.md`); QR rendering (see `links/qr-codes.md`).
- Profile analytics content (`docs/link-to-bio/`); affiliate attribution and ad measurement (their own minors — consumers here); custom short domains (named future work); P4/P5.

## Open questions

- Q1: Bot-exclusion posture — one shared decision across creator/short-link/ad counting, or strictest-wins for anything tied to money?
- Q2: Custom short domains (brand.go on own domain) — scope now as part of custom-domain work, or separate future major?
