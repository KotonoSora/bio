# Entitlement and Plans - Database Schema Design (feature 35)

Phase 2 TL · `billing_db` migration `V2__entitlement_ledger.sql` (chain row in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`). BA sources: `docs/01-requirements-ba/35-entitlement-and-plans/`.

## Flyway V2 (`V2__entitlement_ledger.sql`)

```sql
CREATE TABLE plans (
  id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
  features_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_plan_code CHECK (code IN ('free','pro'))
);

CREATE TABLE plan_terms (
  id TEXT PRIMARY KEY, plan_code TEXT NOT NULL REFERENCES plans(code),
  term TEXT NOT NULL, amount_vnd INT NOT NULL CHECK (amount_vnd >= 0),
  ai_credits INT NOT NULL DEFAULT 0 CHECK (ai_credits >= 0),
  version INT NOT NULL CHECK (version >= 1),
  status TEXT NOT NULL DEFAULT 'draft',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  activated_at TIMESTAMPTZ, retired_at TIMESTAMPTZ,
  CONSTRAINT chk_term CHECK (term IN ('monthly','yearly')),
  CONSTRAINT chk_term_st CHECK (status IN ('draft','active','retired')),
  CONSTRAINT uq_term_ver UNIQUE (plan_code, term, version)
);
CREATE UNIQUE INDEX uniq_active_term ON plan_terms (plan_code, term) WHERE status = 'active';
CREATE INDEX idx_term_lookup ON plan_terms (plan_code, status);

CREATE TABLE subscriptions (
  id TEXT PRIMARY KEY, user_id TEXT NOT NULL,
  plan TEXT NOT NULL CHECK (plan IN ('free','pro')),
  status TEXT NOT NULL CHECK (status IN ('active','grace','cancelled','superseded','expired')),
  plan_term_version TEXT NOT NULL REFERENCES plan_terms(id),
  period_start TIMESTAMPTZ NOT NULL, period_end TIMESTAMPTZ,
  provider TEXT, order_code BIGINT REFERENCES orders(order_code),
  closed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_sub_free CHECK (plan <> 'free' OR (period_end IS NULL AND provider IS NULL AND order_code IS NULL)),
  CONSTRAINT chk_sub_paid CHECK (plan <> 'pro' OR (period_end IS NOT NULL AND provider IS NOT NULL AND order_code IS NOT NULL))
);
CREATE UNIQUE INDEX uniq_sub_current ON subscriptions (user_id) WHERE status IN ('active','grace');
CREATE INDEX idx_subs_user ON subscriptions (user_id, created_at DESC);
CREATE INDEX idx_subs_period ON subscriptions (period_end) WHERE status IN ('active','grace','cancelled');

CREATE TABLE ai_credit_periods (
  user_id TEXT NOT NULL, kind TEXT NOT NULL,
  period_start TIMESTAMPTZ NOT NULL, period_end TIMESTAMPTZ,
  credits_total INT NOT NULL CHECK (credits_total >= 0),
  credits_used INT NOT NULL DEFAULT 0 CHECK (credits_used >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kind, period_start),
  CONSTRAINT chk_ai_bounds CHECK (credits_used <= credits_total)
);

CREATE TABLE ai_credit_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id TEXT NOT NULL, kind TEXT NOT NULL, period_start TIMESTAMPTZ NOT NULL,
  delta SMALLINT NOT NULL CHECK (delta <> 0),
  idempotency_key TEXT NOT NULL UNIQUE,
  return_of BIGINT REFERENCES ai_credit_events(id),
  reason TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uniq_ai_return ON ai_credit_events (return_of) WHERE return_of IS NOT NULL;
CREATE INDEX idx_ai_events_user ON ai_credit_events (user_id, created_at DESC);

ALTER TABLE orders ADD COLUMN plan_term_version TEXT REFERENCES plan_terms(id);
CREATE INDEX idx_orders_term ON orders (plan_term_version) WHERE plan_term_version IS NOT NULL;

ALTER TABLE plans, plan_terms, subscriptions, ai_credit_periods, ai_credit_events
  ENABLE ROW LEVEL SECURITY;

CREATE POLICY plan_read  ON plans FOR SELECT TO app_role USING (true);
CREATE POLICY plan_write ON plans FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');
CREATE POLICY term_read  ON plan_terms FOR SELECT TO app_role USING (true);
CREATE POLICY term_write ON plan_terms FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');
CREATE POLICY sub_owner ON subscriptions FOR SELECT TO app_role
  USING (user_id = current_setting('app.user_id', true));
CREATE POLICY sub_ops   ON subscriptions FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');
CREATE POLICY ai_owner ON ai_credit_periods FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY ai_ops   ON ai_credit_periods FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');
CREATE POLICY aie_owner ON ai_credit_events FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY aie_ops ON ai_credit_events FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');

GRANT SELECT, INSERT, UPDATE ON subscriptions, ai_credit_periods TO app_role;
GRANT SELECT, INSERT ON ai_credit_events TO app_role;
GRANT SELECT, INSERT, UPDATE ON plans, plan_terms TO app_role;

INSERT INTO plans (id, code, name, features_json) VALUES
 ('plan_free','free','Free','{"capabilities":{"profiles.limit":"1"},"claimIds":["one_profile","unlimited_links","tips_enabled","basic_resume"]}'),
 ('plan_pro','pro','Pro','{"capabilities":{"profiles.limit":"unlimited"},"claimIds":["unlimited_profiles","custom_domain","realtime_analytics","premium_templates","ai_jd_tailor"]}');
INSERT INTO plan_terms (id, plan_code, term, amount_vnd, ai_credits, version, status, activated_at)
 VALUES ('term_free_monthly_v1','free','monthly',0,0,1,'active',now());
-- paid terms: none seeded; the first price is a finance activation (BR-21)
```

## Semantics

- Statuses: `active|grace|trial` current (one per user, partial unique; `trial` via V3); `cancelled` serves to `period_end`; `superseded`/`expired` are history. `closed_at` = when currentness ended; resolver matrix = TL 01 D-5.
- Grant write (34's transaction): close current as `superseded` + insert new + audit (TL 01 D-4).
- Term rows: append-only; corrections are new versions (`version = max+1` per plan+term; drafts never edited); retire refused while `pending` orders reference the id.
- Yearly: `yearly = 12 x monthly_active - round(12 x monthly_active x discount / 100)` (half-up, integer); `discount` from `ops_config` `billing.catalog` (absent -> no discount); needs an active monthly row.
- Credits: window = the subscription's period (PK `user_id, kind, period_start`); `credits_total` stamped at window creation from the term row; bounds CHECK + keyed idempotency carry atomicity (TL 03 rules). Kind: code registry, no DB CHECK (16's no-DDL contract). PII: `user_id` scrubbed per 02's roster; rows retained (no DELETE grants).

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V2 runs clean; five tables + `orders.plan_term_version` visible; seeds = 2 plans + 1 free term; zero paid price exists (grep + SQL) |
| Current-row concurrency | Parallel grants -> one `active` row; loser `superseded`; partial unique never violated |
| Resolver matrix | active/grace -> plan; in-window cancelled -> plan; past-end cancelled -> free; superseded -> free |
| Debit race + return bounds | n parallel debits, n-1 credits -> n-1 win; replay returns the original result; a second return is refused; counters restore exactly |
| Activation/retire guards | Second active insert fails; retire blocked while a pending order references the version |
| RLS + immutability | Foreign `app.user_id` reads zero rows; ops context reads for support; events are append-only (no UPDATE/DELETE grant) |
| Single DDL owner | `grep -rn "CREATE TABLE subscriptions" docs/` matches exactly one file (this one) |
