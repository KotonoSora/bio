# Advertising (Inventory, Serving, Creator Share, Retargeting) - Database Schema Design (feature 50) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/50-advertising/`. GATED: present state is **zero** — no ad table exists in any database, and none may be added before the amendment (BR-01, NFR-02). This file designs the future `ads_db` migration `V1__ads.sql`; the amendment record starts the chain.

## Present state: zero

| Probe | Assertion |
|---|---|
| Schema diff | No `ad_*`, `creator_ad_earnings`, `segments`, `segment_membership`, or `retarget_flights` table in any database |
| Migration chain | `ads_db` does not exist; no `V*__ads*.sql` file exists |
| Source grep | `DecisionService`, `MeasurementPipeline`, `SegmentEvaluator` absent from all source and bundles |
| Routing | No `/api/a/` redirect or internal decide route exists |

## Future Flyway V1 (`V1__ads.sql`, at amendment)

```sql
CREATE TABLE ad_placements (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK (kind IN ('platform_slot','creator_unit')),
  position TEXT NOT NULL,            -- e.g. below_profile_header | end_of_blocks
  format JSONB NOT NULL,             -- image+headline+cta constraints; no video/interstitials
  active BOOLEAN NOT NULL DEFAULT true,
  catalog_version TEXT NOT NULL
);

CREATE TABLE ad_flights (
  id TEXT PRIMARY KEY,
  advertiser_ref TEXT NOT NULL,
  placements TEXT[] NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  cap_impressions BIGINT,
  cap_spend_vnd BIGINT,
  status TEXT NOT NULL CHECK (status IN ('draft','scheduled','live','paused','ended')),
  priority INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_af_live ON ad_flights (status, starts_at, ends_at);

CREATE TABLE ad_creatives (
  id TEXT PRIMARY KEY,
  flight_id TEXT NOT NULL REFERENCES ad_flights(id),
  asset_ref TEXT NOT NULL,           -- R2, avatar-grade validation
  headline TEXT NOT NULL,
  cta_url TEXT NOT NULL,             -- https allowlist; rel="noopener sponsored ugc"
  approval TEXT NOT NULL CHECK (approval IN ('pending','approved','rejected','paused')),
  reject_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ad_impressions (
  day DATE NOT NULL,
  flight_id TEXT NOT NULL,
  placement_id TEXT NOT NULL,
  segment JSONB NOT NULL,            -- coarse segment dims only
  impressions BIGINT NOT NULL DEFAULT 0,
  excluded BIGINT NOT NULL DEFAULT 0, -- filter counts by reason in segment
  PRIMARY KEY (day, flight_id, placement_id)
);

CREATE TABLE ad_clicks (
  day DATE NOT NULL,
  flight_id TEXT NOT NULL,
  placement_id TEXT NOT NULL,
  clicks BIGINT NOT NULL DEFAULT 0,
  excluded BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (day, flight_id, placement_id)
);

CREATE TABLE creator_ad_earnings (
  id TEXT PRIMARY KEY,
  day DATE NOT NULL,
  profile_id TEXT NOT NULL,
  flight_id TEXT NOT NULL,
  amount_vnd BIGINT NOT NULL CHECK (amount_vnd >= 0),
  rate_snapshot JSONB NOT NULL,      -- {rateBp, version, effectiveFrom}
  status TEXT NOT NULL CHECK (status IN ('estimated','confirmed','paid','clawed_back')),
  linked_evidence TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cae_profile ON creator_ad_earnings (profile_id, day DESC);

CREATE TABLE segments (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL,          -- per-creator only; never pooled
  definition_version INT NOT NULL,
  event_set TEXT NOT NULL CHECK (event_set IN ('page_view','product_view','preview_finish','checkout_abandon','past_buyer')),
  lookback_days INT NOT NULL,
  membership_cap BIGINT NOT NULL,
  consent_basis TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE segment_membership (
  segment_id TEXT NOT NULL REFERENCES segments(id),
  member_ref TEXT NOT NULL,          -- opaque per-creator key
  entered_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,   -- TTL = privacy mechanism
  PRIMARY KEY (segment_id, member_ref)
);
CREATE INDEX idx_sm_expiry ON segment_membership (expires_at);

CREATE TABLE retarget_flights (
  id TEXT PRIMARY KEY,
  segment_id TEXT NOT NULL REFERENCES segments(id),
  surface TEXT NOT NULL CHECK (surface IN ('onsite','email')),
  control_pct INT NOT NULL DEFAULT 10,
  status TEXT NOT NULL CHECK (status IN ('draft','live','paused','ended')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## RLS and grants (at amendment)

```sql
-- ad_flights/ad_creatives: ops ALL; advertiser_ref-scoped reads for own reports
-- ad_impressions/ad_clicks: service/ops ALL; advertiser reads own flight rows only
-- creator_ad_earnings: own-profile reads; service/finance ALL
-- segments/segment_membership/retarget_flights: own-creator scoped; privacy-reviewer read; service ALL
GRANT SELECT, INSERT, UPDATE ON ad_flights, ad_creatives, retarget_flights TO app_role;
GRANT SELECT, INSERT, UPDATE ON ad_impressions, ad_clicks TO app_role;   -- aggregate upserts only
GRANT SELECT, INSERT, UPDATE ON creator_ad_earnings TO app_role;
GRANT SELECT, INSERT, DELETE ON segment_membership TO app_role;          -- expiry deletes
```

## Semantics

- Decision (D-2): eligibility = live window + placement + creator opt-in + category compatibility both directions + cap headroom; weighted selection by priority/caps, deterministic tie-breaks; cache per (placement, coarse segment) in Redis db 14 with short TTL; empty/error → collapse.
- Measurement (D-3): pings/clicks → queue → dedupe `ads:dedupe:{visitorHash}:{slot}:{creative}` (10-min TTL) → aggregate upserts; raw NDJSON in R2; evidence bundle per billed event; filters write `excluded` counts by reason.
- Filters (D-4): known bots excluded; viewport-verified impressions; click landing validation; velocity floods quarantined (excluded from billing aggregates) with review queue; cleared traffic re-enters aggregates only.
- Earnings (D-5): daily estimates from filtered aggregates × `rate_snapshot`; monthly close → `confirmed`; payout via 49's runner handoff (`POST /internal/v1/payout-batches/earner-lines`, earner-type `creator_ads`); `paid` on rail confirmation; clawbacks move only tainted rows with `linked_evidence`.
- Segments (D-7): evaluator consumes analytics/order streams (minutes cadence); membership rows enter on qualifying events and expire at lookback end; sizes shown with `as of`; per-creator keys are opaque (no cross-creator join).
- Reach (D-6/D-8): onsite = decisioning segment dimension; email = 48's campaign path with 48's eligibility arbitrating class per recipient; holdout slice per retarget flight (default 10%); no export surface exists.
- Retention (NFR-13): raw NDJSON per the analytics TTL precedent, then aggregate-only; evidence bundles per billing retention; membership TTL is the privacy mechanism (expiry, not cleanup).

## Drills (at amendment)

| Drill | Asserts |
|---|---|
| Fresh apply | V1 creates nine tables + indexes + RLS; separation probes: zero ad tables referenced from analytics code, zero analytics tables referenced from ads code |
| Decision parity | Eligibility matrix fixtures (window/placement/opt-in/category/caps) match hand-computed outcomes; collapse has zero layout shift |
| Counting | Dedupe storms → one; bots/viewport/landing filters exact; quarantine leaves billing aggregates clean |
| Earnings chain | Event → aggregate → ledger → statement reconciliation to the VND; monthly close flips; clawback proportional |
| Segment expiry | Membership enters/exits at lookback boundaries; per-creator isolation probe empty; TTL rows gone after expiry |
| Absence (live) | Until amendment: zero ad tables in every schema diff (AC-01) |
