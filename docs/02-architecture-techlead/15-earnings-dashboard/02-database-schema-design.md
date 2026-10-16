# Earnings Dashboard - Database Schema Design: Zero-Schema Posture (feature 15)

Phase 2 TL · BA sources: `docs/01-requirements-ba/15-earnings-dashboard/02-user-stories-and-business-rules.md` (BR-02..BR-07), `07-non-functional-requirements.md` (NFR-15). Base tables: `docs/02-architecture-techlead/12-profile-analytics/02-database-schema-design.md`; audience tables: `docs/02-architecture-techlead/13-audience-insights/02-database-schema-design.md`.

## Assertion: zero PG surface

- `analytics_db` keeps migrations V1-V2 exactly as shipped by 12-13. No V3 for this feature; no `CREATE TABLE`, index, or RLS policy - review greps the migration set and fails any hit (a dashboard-owned money table would fork the ledger truth).
- The dashboard owns no money and stores no merged views beyond the response cache; every figure is read from a source aggregate at render time. Nothing here needs to survive a restart.

## Read contracts (the only "schema")

| Contract | Shape | Owner |
|---|---|---|
| Capability flags | Per-profile booleans: `has_tip_orders`, `has_shop_orders`, `has_course_orders`, `has_service_orders`; read before each strip; unknown flags ignored (closed set); union = feature 14's `has_orders` | Revenue pipelines (22-26, 32-40) own storage + maintenance; this feature defines names and read timing only |
| `RevenueRollupPort` | Per link + window: `units`, `revenue` (VND integer), gross/net label verbatim, source lag | Revenue pipelines; v1 absent (no flags exist yet) |
| Engagement read | Views, clicks per link + window; `ctr_x1000` window sums | 12-13 tables (`link_clicks`, `link_perf`, traffic tables) |

## Canonical formulas and labels

| Rule | Definition |
|---|---|
| CTR | Consumed `ctr_x1000` from 13 (window sums at read, round-half-up); null under 30 views; never re-derived here |
| `rpc` | `revenue / clicks` same link + window, round-half-up; null when clicks = 0 or revenue absent; mismatched windows fail review |
| Attribution | Purchase day owns the sale (rule line; finance changes amend it) |
| Gross vs net | Label passed through verbatim; no fee math exists in this feature (fees live with shop pricing); gross-only source -> no net column |
| Totals | Engagement sums always; money totals only when `moneyComplete` (every visible row has revenue); never summed across missing rows |

## Cache posture

- Unified responses: `private, max-age=60` (per profile + range); merged views only, never source truth; sources re-read on expiry.
- Live ticks bypass this cache (14's contract) and are never stored or merged server-side.

## Drills (DB-side gates)

| Drill | Assertion |
|---|---|
| Violation | Grep: zero DDL, zero fee/net math in this feature's code; finance review sign-off (AC-11) |
| Gating | Pre-revenue payload has zero money keys; flag set -> strip lights (fixture with a stub `RevenueRollupPort`) |
| Consistency | Unified figures == origin tables at one `generatedAt`; CSV cell-for-cell including source columns |
| Attribution | `rpc` fixture same-window passes; mismatched-window fixture fails review |
| Totals | `moneyComplete:false` hides money totals; engagement sums always present |

## Out of scope

- Endpoints + DTOs (`03-api-contracts-and-payloads.md`); tasks (`04-step-by-step-implementation-plan.md`); flag/rollup storage (22-26, 32-40); audience methodology (13); live mechanics (14).
