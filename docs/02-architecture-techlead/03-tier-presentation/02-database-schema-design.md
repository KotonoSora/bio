# Tier Presentation - Database Schema Design (feature 03)

Phase 2 TL · No new tables, no new columns, no migration. This file records that decision and the non-database artifacts the feature depends on. BA sources: `docs/01-requirements-ba/03-tier-presentation/` (BR-03, BR-04, BR-11).

## Why no schema

The legacy rule holds verbatim: presentation binds existing mechanics into promises; it owns no state. Plan, price, discount, trial, and lifecycle rows belong to features 32-40's catalog/ledger (`docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` database-per-service rule). `account_db` gains nothing: the entitlement snapshot (feature 01) already carries `plan` and the capability map; `profiles.limit` needs no column.

## Configuration keys (owned artifact)

| Key | Type | Validation | Absence behavior |
|---|---|---|---|
| `yearlyDiscountPct` | integer percent | `0 <= x <= 44` at load; invalid config refuses boot | Saving badge omitted; yearly price renders as plain 12x monthly |
| `trialDays` | integer days | `> 0`; one trial per user ever is enforced by feature 36 | Trial CTA omitted |

Both keys are read server-side; the SPA receives computed values only. No copy file may contain the numbers (BR-03).

## Claim registry (build artifact)

- Location: web repo `plans-claims.json`, one row per claim: `{claimId, textPattern, enforcement, key}` where `enforcement` is the exact enforcing spec path (feature doc) and `key` names a config/catalog field (`yearlyDiscountPct`, `plansResponse.prices`, ...).
- CI renders pricing/account copy from this file + config fixtures; failures: literal number found, claim without enforcement path, enforcement without claim (extra-claim check).
- Release archive (G7): registry export + rendered-copy snapshot per tag; diff vs previous tag reported.

## Forward reads (no storage here)

| Source | Contract | When |
|---|---|---|
| Catalog (features 32-40) | `GET /api/v1/plans` payload shape (frozen in `docs/02-architecture-techlead/03-tier-presentation/03-api-contracts-and-payloads.md`) | Pre-launch contract; serving at the monetize batch |
| Subscription projection (feature 36) | `GET /internal/v1/subscriptions/{userId}` | Absent fields until the batch lands |

## Verification drills

1. Boot drill: config with `yearlyDiscountPct=45` refuses startup; `=44` accepts; unset accepts with badge-off behavior.
2. Drift drill: seeded stale copy (hard-typed 44%) fails the CI test; fixed copy passes; extra claim fails.
3. No-schema drill: `grep -rn "CREATE TABLE" docs/02-architecture-techlead/03-tier-presentation/` returns zero hits.
