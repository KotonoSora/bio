# User Behavior (Journeys, Conversion, Retention) - Database Schema Design (feature 53) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/53-user-behavior/`. GATED: present state is **zero** — no behavior table exists in any database, and none may be added before the amendment (BR-01, NFR-02). This file designs the future `analytics-service` migration `V6__behavior.sql`; the amendment record starts it. Analytics chain: V1–V4 (47), V5 (52), V6 (this feature).

## Present state: zero

| Probe | Assertion |
|---|---|
| Schema diff | No `buyer_journeys_daily`, `friction_ranking`, `exit_reasons`, `retention_bands`, `adoption_flags`, or `winback_results` table in any database |
| Migration chain | `analytics_db` stops at V4 (V5 is 52's, gated); no `V6__behavior.sql` exists |
| Source grep | `JourneyAssembler`, `FrictionAnalyzer`, `RetentionScorer` absent from all source and bundles |
| Dependencies | No gating/pricing/fraud module imports any behavior module (gate: zero hits, forever) |

## Future Flyway V6 (`V6__behavior.sql`, at amendment)

```sql
CREATE TABLE buyer_journeys_daily (
  day DATE NOT NULL,
  segment JSONB NOT NULL DEFAULT '{}',     -- closed cut set only; sub-floor collapses to 'other'
  journeys BIGINT NOT NULL,
  stage_transitions JSONB NOT NULL,        -- counts per closed-vocabulary transition
  friction_counts JSONB NOT NULL,          -- marker -> count (outcome classes only)
  inferred_closures BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (day, segment)
);

CREATE TABLE friction_ranking (
  period TEXT NOT NULL,                    -- e.g. 2026-11
  marker TEXT NOT NULL,
  orders_touched BIGINT NOT NULL,
  abandon_lift DOUBLE PRECISION NOT NULL,
  segment_value BIGINT NOT NULL,
  score DOUBLE PRECISION NOT NULL,
  baseline DOUBLE PRECISION NOT NULL,
  playbook_ref TEXT,
  PRIMARY KEY (period, marker)
);

CREATE TABLE exit_reasons (
  period TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  plan TEXT NOT NULL,
  tenure_band TEXT NOT NULL,
  count BIGINT NOT NULL,
  PRIMARY KEY (period, reason_code, plan, tenure_band)
);

CREATE TABLE adoption_flags (
  user_key_hash TEXT NOT NULL,             -- owner-scoped analytics key; never a gate/pricing input
  plan TEXT NOT NULL,
  activated BOOLEAN NOT NULL,
  activated_at TIMESTAMPTZ,
  checklist_version TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_key_hash, plan)
);

CREATE TABLE retention_bands (
  day DATE NOT NULL,
  user_key_hash TEXT NOT NULL,
  band TEXT NOT NULL CHECK (band IN ('healthy','watch','at-risk')),
  reasons JSONB NOT NULL,                  -- contributing signals, visible per account
  score_version TEXT NOT NULL,
  PRIMARY KEY (day, user_key_hash)
);

CREATE TABLE winback_results (
  period TEXT NOT NULL,
  cohort TEXT NOT NULL,
  offer_kind TEXT NOT NULL,
  recency_band TEXT NOT NULL,
  won_back BIGINT NOT NULL DEFAULT 0,
  ignored BIGINT NOT NULL DEFAULT 0,
  unsubscribed BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (period, cohort, offer_kind, recency_band)
);
```

## RLS and grants (at amendment)

```sql
-- All six tables: operator/analyst roles only (RLS); service upsert; NO buyer/earner role grants
-- retention_bands + adoption_flags additionally readable ONLY by the risk-band internal read (36 seam)
GRANT SELECT, INSERT, UPDATE ON buyer_journeys_daily, friction_ranking, exit_reasons, winback_results TO app_role;
GRANT SELECT, INSERT, UPDATE ON retention_bands, adoption_flags TO app_role;
-- schema separation: gating/entitlement/fraud schemas hold no reference to these tables; CI dependency check enforces
```

## Semantics

- Assembly (BR-02/BR-03): batch job groups events by `journey_id` (per buyer + purchase intent, rotated per order); joins only on `(user_id or email hash) + order/proforma context`; logged-out traffic unattributed until identification; closure sweep finalizes stale trails (14 days inactivity; open proforma extends to 33's expiry) marked as inferred.
- K-anonymity (BR-05): readouts enforce the floor (default 10, config); sub-floor segments collapse to "other"; no raw export route exists; deletions clear raw trails per 52's classes — these aggregates are anonymous and stand.
- Friction ranking (BR-08): monthly; `score = orders_touched × abandon_lift × segment_value`; baselines stored with each score; correlation labeled; causal claims require a 52-registry experiment link.
- Segment cuts (BR-09): closed set only; renames versioned with overlap reporting (KPI discipline).
- Retention (BR-14/BR-15): adoption checklists versioned in config; bands computed from signals with reasons stored per account; score logic versioned; bands consumed only by the risk-band internal read (36) for reminder intensity + support readiness — dependency gate proves no gating/pricing/fraud consumption.
- Win-back (BR-17): results aggregated; sends ride 36/48 paths with 48's eligibility arbitrating consent at send time; consent-declined buyers absent by construction.
- Price-sensitivity (BR-12): read-only aggregate inputs; no personalized treatment path exists in this schema or any consumer.

## Drills (at amendment)

| Drill | Asserts |
|---|---|
| Fresh apply | V6 creates six tables + indexes + RLS; zero earner/buyer grants |
| Stitching | Logged-out browsing unattributed until identification; grep proves zero fingerprint/device-graph joins |
| K-floor | Sub-floor segments collapse in every readout; no export route exists |
| Sweep | Stale trails finalized inferred within window; zero orphans past retention |
| Dependency | CI check: gating/pricing/fraud schemas and code hold zero references to behavior tables |
| Absence (live) | Until amendment: zero behavior tables in every schema diff (AC-01) |
