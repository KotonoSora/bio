# Earnings + Engagement Dashboard (one money-and-attention view)

PLAN ref: P1d-2 (engagement side) + P2-2/P2-3/P3 (revenue side — flagged: earnings panels are phase-gated and render only as each revenue source ships; this doc builds no shop, order, or billing tables).

## Purpose

Put earnings and engagement side by side in one dashboard: views, clicks, and CTR next to revenue, units, and revenue-per-click — so a creator sees which attention actually pays. The dashboard is a read-only unification layer; every figure stays owned by its source system, and the dashboard never reconciles or restates money.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Creator | Owner-scoped read-only unified dashboard (`WHERE user_id = session.userId`, per-profile short-TTL private cache) | Engagement funnel (views → clicks → buys) and revenue tables per product/link in one screen. |
| Pro creator | Owner-scoped read-only unified dashboard with plan-based range clamping; realtime ticks bypass cache per realtime contract | Adds realtime sales ticks (from `analytics/realtime-global.md`) and longer history windows to the same view. |
| Support/ops | — | Explains any figure by tracing it to its source aggregate in one hop. |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | answer "which links earn?" | I see which attention actually pays | High |
| US-02 | Pro creator | realtime sales ticks and longer history windows in the same view | live and longer-window performance stay in one money-and-attention view | Medium |
| US-03 | Support/ops operator | explain any figure by tracing it to its source aggregate in one hop | every figure stays owned by its source system | Medium |

## Functional requirements

1. Unified funnel widgets: per-link rows showing views → clicks → CTR → units → revenue → revenue-per-click, each cell labeled with source + lag (beacon aggregates hourly; order rollups per their pipeline). Missing-source cells show "not yet available" — never zeros that imply measured absence.
2. Phase-gated earnings strips: tips revenue appears with P2 tips orders; product/course revenue with P2 shop; plan/affiliate payouts (if any) with their monetize sources. The dashboard queries a capability registry (which revenue rollups exist for this profile) before rendering each strip — pre-P2 profiles see a pure engagement dashboard with no stubs or greyed-out money panels that imply imminent data.
3. Sales-performance views: revenue daily series, top-earning links, per-product units, refund/void-adjusted net where the order source exposes it (gross vs net labeled per row; the dashboard never computes net itself).
4. Engagement side (owned by sibling docs, arranged here): overview totals, period compare, audience splits, and link performance — embedded as the same components the analytics tabs use, so numbers match exactly between tabs.
5. Traceability: every figure carries a hover/click-through to its source view (analytics tab or order list); no dashboard-only metric exists that cannot be opened at its origin. Export (CSV) includes source + generated-at columns per row.
6./mobile parity: the unified view is responsive-first and ships in the PWA shell (see `analytics/mobile-pwa.md`) with identical figures — arrangement may collapse, values never differ.
7. Read performance and owner scoping: unified reads stay owner-scoped with plan-based range clamping and serve from the per-profile short-TTL private cache; realtime ticks bypass cache per the realtime contract.

## Data / API touches

- No new tables. Reads only: beacon aggregates (`profile_views`, `link_clicks`, `traffic_segments`, `link_perf`) plus order/revenue rollups owned by shop/monetize docs via a read aggregator `GET /api/v1/analytics/unified?range=` that fans out server-side and merges. Owner scoping throughout (`WHERE user_id = session.userId`); plan-based range clamping inherited from the advanced tier.
- Capability registry: lightweight profile-level flags (e.g. `has_tip_orders`, `has_shop_orders`) maintained by the revenue sources' own pipelines — the dashboard reads flags, never infers from raw orders.
- Cache posture: unified responses cache per-profile for minutes (private, short TTL); realtime ticks bypass cache per the realtime doc's contract.

## Acceptance criteria

- Gating drill: pre-shop profile shows engagement-only dashboard with zero money-panel stubs; each shipped revenue source lights its strip within its pipeline lag. *GWT: Given a pre-shop profile with no shipped revenue source; When the dashboard renders; Then it shows engagement-only with zero money-panel stubs and each shipped source lights its strip within its pipeline lag.*
- Consistency drill: every figure on the unified view matches its source tab/ledger exactly at the same `generated_at`; CSV export reconciles cell-for-cell. *GWT: Given the unified view and source tabs at the same `generated_at`; When figures and CSV export are compared; Then every figure matches its source exactly cell-for-cell.*
- Attribution drill: revenue-per-click divides the correct revenue aggregate by the correct click aggregate for the same link + window (fixture-verified; mismatched windows fail review). *GWT: Given revenue and click aggregates for the same link + window; When revenue-per-click is computed; Then it divides the correct revenue aggregate by the correct click aggregate.*
- IDOR drill: user A unified reads for user B profile return 403 across both engagement and revenue sources. *GWT: Given user A requesting user B profile unified reads; When the request is evaluated; Then it returns 403 across both engagement and revenue sources.*
- Honesty drill: pipeline-lag labels present on every widget; refund-adjusted rows state gross-vs-net; no silent restatement anywhere. *GWT: Given any rendered widget or row; When displayed; Then pipeline-lag labels are present and refund-adjusted rows state gross-vs-net with no silent restatement.*

## Out of scope

- Beacon/queue/aggregate mechanics (see `analytics/profile-analytics.md`); audience methodology (see `analytics/audience-insights.md`); live-feed mechanics (see `analytics/realtime-global.md`); PWA shell (see `analytics/mobile-pwa.md`).
- Order, product, payout, and billing tables/APIs (their own majors — consumed read-only); campaign/ad-spend accounting (P4-growth); agency combined reporting (P4).

## Open questions

- Q1: Goal values (creator-set revenue targets with progress bars) — v1 motivator, or v2 gamification?
- Q2: Revenue attribution window (click day vs purchase day) — which day owns the sale in per-link rows?
