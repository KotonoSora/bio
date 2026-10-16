# Monetize Overview - Database Schema Design (feature 32)

Phase 2 TL · `billing_db` (PostgreSQL 18.6, per-service, Flyway on boot, RLS on). This feature creates no tables: the registry and config surfaces are code and config documents, never tables — the legacy slice's zero-table assertion is preserved. BA source: `docs/01-requirements-ba/32-monetize-overview/` (BR-01, BR-06).

## Table ownership map (each `CREATE TABLE` appears once — in the owning feature)

| Table | Owning feature | Migration |
|---|---|---|
| `orders` (non-sequential int64 `order_code` PK, `amount_vnd INT`, `provider`, `psp_ref`, fee snapshot `{grossVnd, feeLines[], netVnd, rateVersion}`, `extras`, `ship_to`, `status`) | 33 | `V1__billing_orders.sql` |
| `audit_log` (append-only) | 33 | same V1 |
| `ops_config` (append-versioned: key, version, value JSONB, actor, reason, at) | 33 | same V1 |
| `plans`, `plan_terms`, `subscriptions`, `ai_credit_periods`, `ai_credit_events` (+ `orders.plan_term_version` ALTER) | 35 | `V2__entitlement_ledger.sql` |
| lifecycle columns + trial provenance (`reminded_*`, `source`/`source_ref`; status/constraint/index evolution) | 36 | `V3__subscription_lifecycle.sql` |
| `proformas` (+ `orders.proforma_id`) | 37 | `V4__proformas.sql` |
| `refunds` (+ credit notes) | 38 | `V5__refunds.sql` |
| `promo_campaigns`, `coupon_batches`, `coupons`, `credits_ledger` (+ `orders` coupon columns) | 39 | `V6__promo_codes.sql` |
| `provider_events` (replay keys), `rail_exceptions` (bank-rail queue), `dispute_cases` | 40 | `V7__provider_rails.sql` |
| `org_subscriptions`, `seat_history`, `contact_transfers` (+ plan-domain ALTER: `subscriptions.plan` gains `agency`) | 46 | `V8__agency_seat_billing.sql` |

Conventions (binding on every row above): `TEXT` ids except `order_code` (int64, non-sequential); every business table carries `user_id TEXT NOT NULL` + index + RLS (owner + ops contexts, feature 12's policy shape); VND integer columns only; 5 s statement timeout; no cross-service FK (product/plan references are logical — `shop_db` and `profile_db` stay foreign).

## Config surfaces (documents, not tables)

| Document | Contents | Notes |
|---|---|---|
| `billing.eligibility` | plan/product × provider matrix + per-rail pending TTL (default 30 min; `sepay`/`napas` 1440) | versioned in `ops_config`; flips audited |
| `billing.display_order` | rail order per surface | API order verbatim when absent |
| `billing.kill_switches` | per-rail bool + reason + at | delist ≤ 5 s |
| `billing.catalog` | `yearlyDiscount`, `trialDays` (35) | plan-term rows are the price truth; this document holds only catalog-wide switches |
| `billing.lifecycle` | `graceDays` (36) | default 7; valid 1..30 |
| `billing.proforma` | `validityDays` (37) | default 7; valid 1..30 |
| `billing.seller` | seller block `{legalName, address, taxCode?, email}` (37) | absent = proformas unavailable, never a placeholder letterhead |
| `billing.promo` | `goodwillMaxDays`, `referralDays` (39) | no default numbers exist; absent config refuses the dependent issuance paths (`promo_config_absent`) |
| `billing.rails` | `sweepStaleMinutes`, `lateTransferGraceHours` (40) | defaults 30 / 24; valid ranges 5..240 / 1..72 |

Reads cached in Redis db 10 (`billing:cfg:{surface}`, TTL 5 s); the cache is never truth. Redis key inventory added by this feature: `bill:fp:{fingerprint}` (keyless dedupe, TTL 90 s), `notifications:q` (stream), `bill:rl:*` (rate tokens).

## Assertions (release-grade)

| Assertion | Check |
|---|---|
| Zero-table | `grep -rn "CREATE TABLE" docs/02-architecture-techlead/32-monetize-overview/ docs/01-requirements-ba/32-monetize-overview/` returns nothing |
| Violation drill | an injected registry table or provider-default constant fails the CI gates (grep + no-default tests) |
| One owner per table | each `CREATE TABLE` in the map above appears exactly once across the tree (batch-end check) |
| RLS coverage | tables above carry owner + ops policies (feature 12 shape) when their migrations land |

## Drills

| Drill | Asserts |
|---|---|
| Registry drill | eight enum members reachable; a ninth is not offerable (AC-01) |
| Config drill | flip → audit row → effect ≤ 5 s; no read serves config older than 5 s (AC-06) |
| No-default gate | CI grep + unit tests red on an injected default; green otherwise (AC-02) |

## Retention and deletion

Audit rows 24 months (platform). This feature owns no user rows; every table-owning feature (33/35/37/38) declares its purge duty into feature 02's deletion roster when its migration lands.
