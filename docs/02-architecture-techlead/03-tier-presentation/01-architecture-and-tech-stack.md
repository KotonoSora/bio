# Tier Presentation - Architecture and Tech Stack (feature 03)

Phase 2 TL · Service: `account-service` hosts the account-screen plan read; the public catalog read is frozen-contract-first and served by features 32-40. No new service, no new tables. Implements `docs/01-requirements-ba/03-tier-presentation/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | `GET /api/v1/plan` lives in `account-service`: entitlement snapshot (feature 01) + optional subscription projection (feature 36, forward). Pre-36, the subscription projection is omitted (never invented); `plan` resolves from the ledger projection (feature 35) within the `<= 30 s` bound. | One read for the account screen; absence stays the design (feature 15 pattern). |
| D-2 | `GET /api/v1/plans` (public pricing data) contract is frozen here; serving is feature 35's (billing-service) from active catalog rows, cache-first posture. | The page can be built against a stable contract before the monetize batch lands; the catalog remains the matrix truth. |
| D-3 | `profiles.limit` is registered in the feature 01 capability map: free `"1"`, pro `"unlimited"`; feature 06's create guard consumes it. | Resolves feature 06's Q-01 seam without hardcoding values in feature 06. |
| D-4 | The claim registry is a build artifact in the web repo (one row per claim: text pattern, enforcing spec path, config/catalog key); CI renders copy from it and fails on drift or literals. | "Every sentence resolves" becomes mechanically checkable, same discipline as the fee schedule. |
| D-5 | Config keys live in `ops_config` (`billing.catalog`): `yearlyDiscount` (validated `<= 44%` at load/flip) and `trialDays`; both absence-optional - unset means the claim is omitted. | Finance-owned values stay config; specs never carry placeholders. |
| D-6 | Presentation never gates: every capability check is server-side against the feature 01 snapshot (fail-closed); UI flags are never trusted. | Matches BR-09 and features 06-16's established pattern. |

## Stack and integration

- No new containers, no schema work (see `docs/02-architecture-techlead/03-tier-presentation/02-database-schema-design.md`); no new dependencies beyond the Spring stack already present in `account-service`.
- SPA: pricing page (public) + account plan panel. All values render from server payloads/config-driven registry; the SPA formats VND and never computes proration, discounts, or percentages itself.
- Cache posture: `/api/v1/plans` public cache-first (Nginx edge cache, 300 s TTL, versioned payload); `/api/v1/plan` per-user, `private, max-age=0` (snapshot freshness bound 30 s applies inside the service).
- Forward wiring: cancel/change/trial execution endpoints belong to features 36-39; this feature's dialogs call them and render their stated effects. No execution is reinvented here.

## Consumers

| Consumer | Contract |
|---|---|
| Feature 06 | `profiles.limit` capability semantics (guard `profile_limit_reached`) |
| Features 04, 09, 13-16 | `plan` (`free`/`pro`) via the feature 01 snapshot - unchanged by this feature |
| Features 32-40 | Claim registry + `GET /api/v1/plans` contract shape; catalog is the matrix truth |
| Web SPA | Registry-driven copy; drift test in CI |
