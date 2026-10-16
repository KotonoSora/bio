# Advertising (Inventory, Serving, Creator Share, Retargeting) - User Stories and Business Rules (feature 50) - GATED

Phase 1 BA · GATED: design-only; AC-01 (absence) runs every release, all other drills run at/after the amendment. Scope: `docs/01-requirements-ba/50-advertising/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Advertiser | to buy placements for a period/audience and read trustworthy reports | renewals use defensible numbers | High |
| US-02 | Creator | to opt page inventory in or out and control adjacency | my page stays mine; ad money is optional | High |
| US-03 | Visitor | clearly labeled sponsored units that never impersonate creator content | the page stays honest and fast | High |
| US-04 | Creator | to watch estimated vs confirmed earnings and get paid with statements | every dong traces to a counted, filtered event | High |
| US-05 | Creator/advertiser | to build "viewed X, didn't buy in 7 days" and read lift vs control | attention compounds measurably, never creepily | Medium |
| US-06 | Dispute/privacy reviewer | to replay billed events and verify segment consent bases | disputes and privacy checks end in facts | Medium |

## Business rules

- **BR-01 Gate (LIVE).** Design-complete only: no ad surface (table, route, job, chunk, UI string) may exist in any image or bundle until a spec amendment; absence drills run every release; a hit blocks promotion.
- **BR-02 Placement catalog (config, versioned).** Platform slots (theme gallery, discovery, post-checkout thank-you — never inside checkout/payment) + creator units (exactly one in-feed slot per page in v1; below-header or end-of-blocks). Formats: image + headline + CTA; no autoplay video, interstitials, or pop-unders. Labeling: "Sponsored", visually distinct, never dismissable-as-content. Checkout/payment surfaces provably ad-free (grep + review gate).
- **BR-03 Targeting (v1, privacy-conservative).** Page category/language, device class, coarse geo from existing analytics signals (no new tracking); no behavioral profiling, no cross-site identity; health/finance/children targeting excluded; all targeting attributes buyer-visible pre-purchase.
- **BR-04 Brand safety (server-enforced).** Category exclusion lists both directions; prohibited verticals per config + legal list; creative pre-approval by ops for every flight (no self-serve upload); takedown is one audited action, everywhere ≤ minutes; prohibited/conflicting creatives rejected at approval or excluded from serving.
- **BR-05 Flight model.** `ad_flights{advertiser, placements[], creatives[], starts_at, ends_at, cap_impressions?, cap_spend?, status}` with promo-campaign discipline: launch requires caps + window; instant pause; ended terminal; v1 flat-fee pricing (values finance-owned).
- **BR-06 Creator opt-in.** Per-profile toggle default OFF + per-page category declaration; mid-flight opt-out stops serving immediately, no penalty; free-plan creators may opt in (ad revenue separate from plan gates); pro pages never forced; same share formula free and pro.
- **BR-07 Separation guarantee.** Ad impressions/clicks flow through ad tables only; creator analytics (`profile_views`/`link_clicks`) are never touched — separate code paths, verified under load in both directions.
- **BR-08 Decisioning.** Eligible flights = live window + placement match + opt-in + category compatibility both ways + cap headroom; selection weighted by priority/caps, deterministic tie-breaks (no real-time bidding in v1); cached per (placement, coarse segment); empty result collapses the slot with zero layout shift; 50 ms cap with collapse-on-timeout.
- **BR-09 Measurement.** Ad-view pings + click redirects flow through a dedicated queue (never direct PG writes), KV-deduped (10-min per visitor/slot/creative) into `ad_impressions`/`ad_clicks`; raw logs to R2 NDJSON per the analytics TTL discipline; evidence bundle per billed event; filter counts reported (what was excluded and why).
- **BR-10 Fraud filters.** Known bots excluded; impressions require viewport visibility; clicks anti-doubled (dedupe + landing validation); velocity anomalies quarantined from billing aggregates pending review.
- **BR-11 Consent posture.** First-party only, analytics-aligned; declined-consent visitors see contextual-only ads or collapsed slots per config — pages never blocked.
- **BR-12 Creator earnings ledger.** `creator_ad_earnings{day, profile_id, flight_id, amount_vnd, status}`: daily estimates from filtered aggregates → `confirmed` at monthly close → `paid` on rail confirmation; rev-share rate config-driven, versioned, forward-only; quarantined traffic never earns, even if later cleared (cleared traffic re-enters aggregates only).
- **BR-13 Clawbacks.** Post-close invalid-traffic findings move only the tainted share to `clawed_back` with linked evidence + creator notice — never silent, never beyond taint; self-farming voids tainted earnings and triggers the warn-then-restrict ladder up to opt-in revocation, legitimate history preserved.
- **BR-14 Shared payout runner.** Creator lines settle on 49's earner-type-partitioned runner: monthly cycle, config threshold, roll-forward, audited batches, VN bank rail with dual control, per-cycle statements; no cross-earner visibility.
- **BR-15 Transparency minimums.** Creator dashboard: estimated vs confirmed, per-flight breakdown, filter-exclusion counts, threshold progress, payout history with references; "why did my earnings drop" answers from the dashboard (clawback lines carry reasons + evidence links).
- **BR-16 Segments (closed v1 event set).** Viewers, product viewers, preview-finishers, checkout abandoners (started, never granted), past buyers (upsell/exclude); each declares source events + lookback + membership cap; definitions versioned (a running segment never silently changes meaning); minutes-scale evaluation with labeled lag and `as of` sizes; no per-hit synchronous writes; per-creator membership only; opaque refs; TTL'd rows (expiry is the privacy mechanism).
- **BR-17 Reach + consent gating.** Reach surfaces: onsite inventory (segment dimension in decisioning) + owned email via 48's campaign path; behavioral membership never overrides consent class (transactional-only abandoners get the lifecycle reminder, never the promo variant — eligibility arbitrates per recipient at send time); frequency caps per segment per recipient per week (creator-tunable within platform ceilings); past buyers excluded from acquisition nudges by default; every message carries its cause + one-click stop; off-platform export absent in v1 (404/422).
- **BR-18 Lift measurement.** Holdout-first: each retargeting flight reserves a no-contact control slice (10% default); lift reported vs control, never vs zero; reports show size, reach, conversion, suppression counts; no claim without its control slice.

## Acceptance criteria pointers

Drills: `docs/01-requirements-ba/50-advertising/06-acceptance-criteria.md` (AC-01 absence live; AC-02..AC-19 at amendment).
