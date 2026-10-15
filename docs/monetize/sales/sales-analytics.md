# Sales Analytics (funnels, revenue, provider performance)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `sales/sales-ledger.md` for placement). Facts there; presentation here. Distinct from creator page analytics (`docs/link-to-bio/`) and affiliate/advertiser readouts: this is the business's own money dashboard.

## Purpose

Define how the business reads its own sales motion: checkout-funnel conversion, revenue cuts, provider reliability, and refund/dispute health — all derived from the sales ledger and order events, never from drum-beating per-provider queries. Decisions this drives (kill-switch flips, provider ordering, campaign spend, dunning tuning) need shared, trustworthy numbers; per-provider dashboards disagree by definition, so this doc makes ours canonical.

## Users

- Operator/founder: answers "are we growing, where do we leak, which rail earns its keep" every morning.
- Finance: closes periods off ledger-derived revenue cuts with audit traceability.
- Support lead: spots anomaly spikes (failed-checkout waves, dispute clusters) before they become incidents.

## Functional requirements

1. Funnel analytics (checkout started → provider handoff → paid): stage counts and conversion rates sliced by provider, plan/product, campaign, device class, and day. Stages derive from existing events (checkout creation, handoff issuance, grant/failure transitions) — no new buyer-facing instrumentation; funnel tables are daily aggregates (`sales_funnel_daily{day, provider, plan_or_product, campaign?, started, handed_off, paid}`) rebuilt idempotently from the event trail.
2. Revenue cuts (all off `sales_lines` + `sales_adjustments`): by day/week/month, provider, plan vs product, campaign (with promo-code attribution), affiliate-attributed vs direct, new vs renewal (ledger source stamp). Every figure links back to its underlying lines for drill-down; no KPI exists that cannot be decomposed to order ids.
3. Provider health scorecards: authorization/grant success rate, median time-to-paid, webhook lag (grant minus provider confirmation), sweep-rescue rate, refund and dispute rates with thresholds that page ops (see Q1). Scorecards feed the checkout kill-switch and provider-ordering decisions in `payment/checkout-orchestration.md` with data instead of instinct.
4. Refund/dispute watch: rates by provider/product/campaign with anomaly alerting (spike vs trailing baseline); cohort retention for subscriptions (renewal rate by acquisition cohort and campaign) shared with the lifecycle doc's reminder tuning.
5. Access and freshness: dashboards refresh on an hourly-or-better cadence (aggregates, never live order rows); finance-period figures freeze at close with a named snapshot (re-runs after close produce identical numbers — close is a versioned cut, not a live query).
6. Privacy posture: business analytics contain no buyer PII beyond what ops roles already see; affiliate/creator-visible rollups stay on their own scoped endpoints (no cross-earner leakage through shared dashboards).

## Data / API touches

- New aggregate tables `sales_funnel_daily` + `sales_revenue_daily{day, dimensions..., gross_vnd, adjustments_vnd}` (idempotent rebuilds from ledger + order events; no buyer PII). All PG via `worker/db/*`; RLS restricts to operator/finance roles.
- Endpoints (restricted roles): `GET /api/v1/ops/analytics/funnel`, `.../revenue`, `.../providers`, `.../refunds` (parameterized slices + drill-down to order lists, never raw payloads). Export to CSV for finance tooling (one-click, audited).
- Alerting: threshold breaches (provider success dip, dispute spike, funnel cliff) route to the ops channel with the slice attached; alert rules versioned in config.

## Acceptance criteria

- Reconciliation drill: every dashboard figure decomposes exactly to ledger lines; finance close snapshot re-runs byte-identical.
- Funnel drill: synthetic traffic with known drop-offs reproduces exact stage counts per slice; provider kill-switch flip shows up in funnel shifts same-day.
- Health drill: degraded-provider simulation (delayed webhooks, elevated declines) trips the scorecard thresholds and pages with the right slice.
- Anomaly drill: dispute spike and funnel cliff both alert before a manual review would catch them (seeded data, measured lead time).
- Auth probe: non-ops roles on analytics endpoints → 403; affiliate/creator callers see only their scoped readouts, never business cuts.

## Out of scope

- Revenue facts and adjustments (see `sales/sales-ledger.md`); the unified ops console (see `sales/order-management.md`).
- Creator page analytics (`docs/link-to-bio/`); affiliate/advertiser scoped stats (their own minors); provider grant mechanics (their own minors); accounting/revenue-recognition reports; P4/P5.

## Open questions

- Q1: Scorecard thresholds — initial success-rate floors and dispute ceilings per provider (start from card-network norms + rail history)?
- Q2: Dashboard freshness — hourly aggregates confirmed, or near-real-time for funnel during launches?
