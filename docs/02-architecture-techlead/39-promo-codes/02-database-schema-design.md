# Promo Codes - Database Schema Design (feature 39)

Phase 2 TL · `billing_db` migration `V6__promo_codes.sql` (chain row in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`). BA sources: `docs/01-requirements-ba/39-promo-codes/`. `orders` gains columns only; `promo` is documented server-minted (V1 leaves `provider` unconstrained).

## Flyway V6 (`V6__promo_codes.sql`)

```sql
CREATE TABLE promo_campaigns (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  kind_scope TEXT NOT NULL CHECK (kind_scope IN ('percent','fixed_vnd','term_extend')),
  value INT NOT NULL CHECK (value >= 1),
  budget_cap_vnd BIGINT NOT NULL CHECK (budget_cap_vnd > 0),
  max_redemptions INT NOT NULL CHECK (max_redemptions >= 1),
  per_user_limit INT NOT NULL DEFAULT 1 CHECK (per_user_limit >= 1),
  target TEXT NOT NULL CHECK (target IN ('new','winback','all','invite_only')),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','scheduled','live','paused','ended')),
  redeemed_vnd BIGINT NOT NULL DEFAULT 0 CHECK (redeemed_vnd >= 0),
  redemption_count INT NOT NULL DEFAULT 0 CHECK (redemption_count >= 0),
  created_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_campaign_window CHECK (ends_at > starts_at),
  CONSTRAINT chk_campaign_percent CHECK (kind_scope <> 'percent' OR value BETWEEN 1 AND 100),
  CONSTRAINT chk_campaign_cap CHECK (redeemed_vnd <= budget_cap_vnd
    AND redemption_count <= max_redemptions)
);

CREATE TABLE coupon_batches (
  id TEXT PRIMARY KEY,
  campaign_id TEXT NOT NULL REFERENCES promo_campaigns(id),
  channel_ref TEXT,
  prefix TEXT NOT NULL CHECK (prefix ~ '^[A-Z2-9]{2,10}$'),
  count INT NOT NULL CHECK (count BETWEEN 1 AND 100000),
  use_limit INT NOT NULL DEFAULT 1 CHECK (use_limit >= 1),
  per_user_limit INT NOT NULL DEFAULT 1 CHECK (per_user_limit >= 1),
  expires_at TIMESTAMPTZ NOT NULL,
  created_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  export_acked_at TIMESTAMPTZ
);

CREATE TABLE coupons (
  id TEXT PRIMARY KEY,
  code_hash TEXT NOT NULL UNIQUE,          -- HMAC-SHA256(COUPON_HASH_KEY, code)
  campaign_id TEXT NOT NULL REFERENCES promo_campaigns(id),
  batch_id TEXT NOT NULL REFERENCES coupon_batches(id),
  channel_ref TEXT,
  invited_user_id TEXT,
  use_limit INT NOT NULL CHECK (use_limit >= 1),
  uses INT NOT NULL DEFAULT 0 CHECK (uses >= 0),
  per_user_limit INT NOT NULL CHECK (per_user_limit >= 1),
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','redeemed','voided','expired')),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_coupon_uses CHECK (uses <= use_limit),
  CONSTRAINT chk_coupon_invite CHECK (invited_user_id IS NULL OR use_limit = 1)
);
CREATE INDEX idx_coupons_campaign ON coupons (campaign_id, status);

CREATE TABLE credits_ledger (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  days INT NOT NULL CHECK (days >= 1),
  source TEXT NOT NULL CHECK (source IN ('coupon','referral','goodwill')),
  source_ref TEXT NOT NULL,
  consumed BOOLEAN NOT NULL DEFAULT false,
  consumed_at TIMESTAMPTZ,
  subscription_id TEXT,
  granted_by TEXT NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_credit_consumed CHECK (consumed = (consumed_at IS NOT NULL))
);
CREATE INDEX idx_credits_open ON credits_ledger (user_id) WHERE NOT consumed;
CREATE INDEX idx_credits_user ON credits_ledger (user_id, granted_at DESC);

ALTER TABLE orders ADD COLUMN campaign_id TEXT REFERENCES promo_campaigns(id);
ALTER TABLE orders ADD COLUMN coupon_id TEXT REFERENCES coupons(id);
ALTER TABLE orders ADD COLUMN discount_vnd INT NOT NULL DEFAULT 0 CHECK (discount_vnd >= 0);
```

## RLS and grants

```sql
-- One ALL policy per table (own policy name); campaigns/batches/coupons only:
CREATE POLICY camp_ops ON promo_campaigns FOR ALL TO app_role
  USING (current_setting('app.context', true) IN ('ops','service'))
  WITH CHECK (current_setting('app.context', true) IN ('ops','service'));
```

Repeated verbatim for `coupon_batches`/`coupons` (no owner policies exist there). `credits_ledger`: same ops/service ALL policy + an owner SELECT policy (`user_id = current_setting('app.user_id', true)`); RLS enabled on all four. Grants (no DELETE anywhere): SELECT/INSERT/UPDATE on all four.

## Semantics

- Convention exception: campaigns/batches/coupons carry no `user_id` (no owner rows exist); buyer access is endpoint-mediated (`promo/validate` + own credits); all else follows 32's conventions.
- Budget decrement (D-4): one guarded `UPDATE` (`status='live'`, window, `redeemed_vnd + :discount <= cap`, `redemption_count + 1 <= max_redemptions`); rowcount 0 = neutral rejection. Restore subtracts the same values, guarded by the order's terminal transition firing once (`discount_vnd` immunizes compensation from catalog drift).
- Per-user serialization: `pg_advisory_xact_lock(hashtextextended(:user_id || ':' || :coupon_id, 0))` in the create transaction; grant re-checks under the same lock.
- Redemption at grant (D-6): guarded `UPDATE coupons SET uses = uses + 1, status = CASE WHEN uses + 1 = use_limit THEN 'redeemed' ELSE 'active' END WHERE id=? AND status='active' AND uses < use_limit`; rowcount 0 = the in-flight edge.
- `chk_campaign_cap` + guards make over-cap exposure impossible against races; auto-pause is a separate system update (audit `campaign.auto_paused`).
- Credits application (D-8): the job reads `idx_credits_open`, groups by user, requires an `active`/`grace` row, extends `period_end` by the day sum (36's write path), marks rows consumed with `subscription_id` in the same transaction; reruns write nothing.
- Coupon expiry sweep (D-9): active + past-expiry -> `expired`, batched, one summary audit (`coupon.expired`, count) per run — per-code audits would flood the spine.
- `invited_user_id` binds `invite_only` codes (single-use by constraint); matched at validate/create/grant.
- No new Redis keys; throttles reuse `bill:rl:*`; campaign status is never cached (pause <= 5 s).

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V6 runs after V5; 4 tables + 5 indexes + RLS visible; `orders` gains exactly 3 columns (old rows `discount_vnd = 0`) |
| Cap race | 50 concurrent creates at 99% burn: exactly the remaining budget succeeds; `chk_campaign_cap` never fires (guards absorb); auto-pause audited once |
| Window/percent guards | `ends_at <= starts_at`, percent 0/101, `value = 0` all fail; cap-less create impossible (NOT NULL) |
| Uses guard | `uses > use_limit` fails; single-use flip lands `redeemed`; multi-use advances |
| Credit flag | `consumed = true` without `consumed_at` fails; `idx_credits_open` scan excludes consumed rows |
| RLS probes | Buyer context reads zero rows on `coupons`/`promo_campaigns`; service context sees hashes only; owners read own credits only |
| Compensation | A coupon order forced terminal-without-grant restores exactly one decrement; sweep rerun restores nothing |
| Single DDL owner | `grep -rn "CREATE TABLE promo_campaigns\\|CREATE TABLE coupons" docs/` matches exactly this file |
| One repricing authority | grep: only the `promo` package writes discount fields; checkout calls the pricing service |
| No defaults | grep: no provider default/fallback in the promo branch; `promo` literal only in server-side mint + copy |
