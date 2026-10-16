# Sales Fees and Profit Calculator - Database Schema Design (feature 26)

Phase 2 TL · `shop_db` migration `V5__shop_fees_and_calculator.sql` (extends V4 from feature 25). BA sources: `docs/01-requirements-ba/26-shop-fees-and-calculator/` (BR-01..BR-17). Zero literal rate values appear below.

## DDL

```sql
CREATE TABLE shop.fee_rate_versions (
  rate_version  INTEGER PRIMARY KEY,
  status        TEXT NOT NULL DEFAULT 'draft',
  rounding_rule TEXT NOT NULL DEFAULT 'nearest_1000',
  requested_by  TEXT,
  approved_by   TEXT,
  published_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_rv_status CHECK (status IN ('draft','published','retired')),
  CONSTRAINT chk_rv_rounding CHECK (rounding_rule IN ('nearest_1000','exact')),
  CONSTRAINT chk_rv_dual CHECK (status = 'draft'
    OR (requested_by IS NOT NULL AND approved_by IS NOT NULL AND requested_by <> approved_by))
);

CREATE TABLE shop.fee_schedule (
  rate_version     INTEGER NOT NULL REFERENCES shop.fee_rate_versions(rate_version),
  tier             TEXT NOT NULL,
  kind             TEXT NOT NULL,
  pct_bp           INTEGER NOT NULL CHECK (pct_bp BETWEEN 0 AND 10000),
  rail_passthrough TEXT NOT NULL DEFAULT 'at_cost',
  payout_delay_days INTEGER CHECK (payout_delay_days >= 0),
  payout_min_vnd    INTEGER CHECK (payout_min_vnd >= 0),
  PRIMARY KEY (rate_version, tier, kind),
  CONSTRAINT chk_fs_tier   CHECK (tier IN ('free','pro')),
  CONSTRAINT chk_fs_kind   CHECK (kind IN ('merch','digital','paywall','course','tip','service','linkout')),
  CONSTRAINT chk_fs_rail   CHECK (rail_passthrough IN ('at_cost')),
  CONSTRAINT chk_fs_linkout CHECK (kind <> 'linkout' OR pct_bp = 0)
);

CREATE TABLE shop.partner_cost_table (
  template      TEXT NOT NULL,
  variant       TEXT NOT NULL,
  base_cost_vnd INTEGER NOT NULL CHECK (base_cost_vnd >= 0),
  updated_by    TEXT NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (template, variant),
  CONSTRAINT chk_cost_template CHECK (template IN ('tee','hoodie','mug','tote'))
);

CREATE TABLE shop.calculator_scenarios (
  scenario_id  TEXT NOT NULL,
  version      INTEGER NOT NULL CHECK (version >= 1),
  user_id      TEXT NOT NULL,
  product_id   TEXT,
  name         VARCHAR(120) NOT NULL,
  inputs_json  JSONB NOT NULL,
  outputs_json JSONB NOT NULL,
  rate_version INTEGER NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (scenario_id, version)
);

ALTER TABLE shop.products ADD COLUMN pinned_rate_version INTEGER;

CREATE INDEX idx_scenarios_user ON shop.calculator_scenarios (user_id, product_id, created_at DESC);

ALTER TABLE shop.fee_rate_versions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.fee_schedule        ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.partner_cost_table  ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.calculator_scenarios ENABLE ROW LEVEL SECURITY;

-- platform config: read-open to app paths, writes only under the svc context (ops routes)
CREATE POLICY fee_read     ON shop.fee_rate_versions FOR SELECT TO app_role USING (true);
CREATE POLICY fee_write    ON shop.fee_rate_versions FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
CREATE POLICY fs_read      ON shop.fee_schedule FOR SELECT TO app_role USING (true);
CREATE POLICY fs_write     ON shop.fee_schedule FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
CREATE POLICY cost_read    ON shop.partner_cost_table FOR SELECT TO app_role USING (true);
CREATE POLICY cost_write   ON shop.partner_cost_table FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

-- scenarios: owner business rows, append-only (no UPDATE policy = immutability)
CREATE POLICY scen_owner_read ON shop.calculator_scenarios FOR SELECT TO app_role
  USING (user_id = current_setting('app.user_id', true));
CREATE POLICY scen_owner_ins  ON shop.calculator_scenarios FOR INSERT TO app_role
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY scen_svc        ON shop.calculator_scenarios FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

GRANT SELECT, INSERT, UPDATE, DELETE ON shop.fee_rate_versions, shop.fee_schedule,
  shop.partner_cost_table TO app_role;
GRANT SELECT, INSERT, DELETE ON shop.calculator_scenarios TO app_role;
```

## Notes

- Integer-only math: `fee_vnd = round(gross_vnd * pct_bp / 10000)` under the version's rule (`nearest_1000` = half-up to the nearest 1 000; `exact` = half-up to 1 VND); `net_vnd = gross_vnd - platform_fee - rail_passthrough(at cost)`; long arithmetic, overflow-guarded (gross ≤ 99 999 999 per feature 22 bounds).
- Draft validation (app, before insert): every tier × kind pair present; free `pct_bp` ≥ pro per kind; tips present; rounding rule set — failures 422 `schedule_invalid`, nothing written.
- Append-only posture: a published version's lines are never edited; corrections ship as a new version (ops write path is draft-only by app rule + audit).
- `linkout` rows exist with `pct_bp = 0` for schedule completeness (the page states "no platform fee"); link-outs never create orders, so the engine is not called for them.
- Scenarios: no UPDATE/DELETE grant for owner paths; newest `version` per `scenario_id` is current; requote diffs read two versions.
- Historical orders pin their `rate_version` order-side (32-40); nothing here is rewritten retroactively.
- Schedule cache: service-side `fees:schedule:{rateVersion}` TTL 60 s (no new Redis database).

## Verification drills

1. Fresh apply V5 on PG `18.6`; `\d` shows CHECKs, the FK, policies.
2. Dual-control drill: self-approval UPDATE fails `chk_rv_dual`; two operators succeed.
3. Invariant drill: `linkout` with `pct_bp > 0` rejected by CHECK; free < pro rejected by draft validation.
4. Config RLS drill: a session context cannot INSERT fee rows; the svc context can; reads work for both.
5. Scenario drill: owner inserts version 1 then version 2; UPDATE attempts are denied (no policy path); foreign owner reads zero rows.
6. Pin drill: publish-price writes `pinned_rate_version`; a later rate publish leaves the row untouched.
