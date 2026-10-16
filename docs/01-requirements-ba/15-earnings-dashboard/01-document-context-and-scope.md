# Earnings Dashboard - Context and Scope (feature 15)

Phase 1 BA · Feature 15 of 53 (link-to-bio, analytics; closes the analytics major) · Implemented by `docs/02-architecture-techlead/15-earnings-dashboard/` (5 files). Extends `analytics-service` (12-14): read-only unification layer, zero new tables, zero money math. Revenue sources are phase-gated and owned by shop 22-26 / monetize 32-40.

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Answer "which links earn?" in one screen: views -> clicks -> CTR -> units -> revenue -> revenue-per-click per link | Consistency drill: every figure matches its origin at the same `generatedAt` (AC-02) |
| BO-2 | Never restate money: every figure stays owned by its source system; no dashboard-side net computation, no money table | Violation drill: grep + finance review find no net math and no `CREATE TABLE` (AC-11) |
| BO-3 | Phase honesty: profiles with no shipped revenue source see a pure engagement dashboard - no stubs, no greyed money panels, no zero-filled cells | Gating drill: zero money keys in payload and DOM (AC-01) |
| BO-4 | Traceability: every figure opens at its origin (analytics tab or order list) in one hop, same window | Traceability drill: links land on the origin in the identical range (AC-08) |
| BO-5 | Cheap by construction: no new tables, bounded fan-out, hourly grain | Load drill: fan-out bounded, PG schema unchanged, 500-link history paginates at 25 (AC-07) |

## In scope

- Owner-only screen `/app/analytics/earnings` with the shared range picker (12-13 clamping) and `FetchState`.
- `GET /api/v1/analytics/unified?range=&points=&page=&profile=` - server-side fan-out over analytics aggregates (12-13) plus flag-gated revenue rollups; per-cell `{source, lag, generatedAt}`.
- `GET /api/v1/analytics/unified.csv?range=&profile=` - streamed CSV with per-row source columns.
- Capability registry read contract (four flags; storage owned by revenue pipelines); funnel formulas; gross-vs-net pass-through; totals rule.
- PWA parity (feature 11 shell); live sales ticks embedded client-side per feature 14's contract (never merged server-side).

## Out of scope

- Order/product/payout/billing tables and grant mechanics (shop 22-26, monetize 32-40 - consumed read-only, never redefined); campaign/ad-spend accounting and creator goals/gamification (v2 / gated backlog); agency combined reporting (45+); realtime mechanics (14); audience methodology (13); CSV scheduling/email delivery.

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | Read own unified view + own CSV export | Sees which attention pays; funnel and money columns in one screen |
| Pro creator | Same reads with 365d range and live ticks embedded (14's contract) | Longer history plus live sales feed next to unified figures |
| Support/ops | No cross-user read; traces a figure for a creator during an owner-shared session or audited internal tooling | Explains any figure by its source cell in one hop; no new support endpoint exists |
| Revenue services (22-26, 32-40) | Own flag + rollup storage | Publish capability flags and rollups; dashboard only reads |

## Resolved open questions

| # | Question | v1 resolution | Revisit trigger |
|---|---|---|---|
| Q-01 | Creator revenue targets with progress bars | Not built in v1 (gamification); renders nothing | v2 gamification |
| Q-02 | Attribution window (click day vs purchase day) | Purchase day owns the sale; a finance change amends this rule line, never branches code | finance review |
| Q-03 | 2 s per-source fan-out timeout | Fixed 2 s in v1; adaptive only on metrics evidence | `unified.fanout_ms` evidence at scale |
| Q-04 | CSV `generated_at` per row or per file | Per row - cell-for-cell reconciliation needs per-source timestamps | - |
| Q-05 | Per-cell retry vs jump to origin | Single view refetch per retry; origin jump stays a trace link | - |

Boundary notes: feature 14's abstract `has_orders` resolves here as the union of the four named flags (`has_tip_orders`, `has_shop_orders`, `has_course_orders`, `has_service_orders`); no second registry exists. CTR is consumed from 13's canonical `ctr_x1000` (window sums at read, round-half-up, null under 30 views) - feature 15 never re-derives it (13 BR-15, AC-08). Ranges clamp via 12-13 rules (free 7/30/90d, pro 365d). Realtime sales ticks are feature 14's domain: the earnings screen embeds the same live component and the unified endpoint never carries live numbers (no grain mixing server-side).

Related specs: base pipeline `docs/01-requirements-ba/12-profile-analytics/`; segments + CTR `docs/01-requirements-ba/13-audience-insights/`; live contracts `docs/02-architecture-techlead/14-realtime-global/03-api-contracts-and-payloads.md`; platform pins `docs/02-architecture-techlead/00-platform-baseline/`.
