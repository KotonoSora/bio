# Sales (Ledger, Analytics, Order Console) - Security, Logging, and Rate Limiting (feature 51) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/51-sales/`. GATED: S-1 is live now; all other controls bind the future build at the spec amendment. Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Gate enforcement (LIVE) | Tooling asserts zero sales surface each release (schema/OpenAPI/source/bundle); a hit blocks promotion — the gate is tool-enforced, not policy |
| S-2 | Fact integrity | Facts append-only (INSERT + SELECT grants); same-transaction projection; UNIQUE on `order_code`; no UPDATE/DELETE path in code or migrations (grep + migration-review gate) |
| S-3 | Action safety | Every action role-gated, precondition-checked, re-validated at execution (stale fails closed); dry-run previews equal executions; bulk actions report per-order results |
| S-4 | Dual control | Above-floor money-moving actions require a different approver; approvals are first-class records; 24 h expiry; expired cannot execute; self-approval impossible |
| S-5 | Orchestration-only console | Actions delegate to 33/34/36/38/40 endpoints; the console adds no financial state — no parallel refund, cancel, or subscription logic may exist in the diff |
| S-6 | Access scoping | Support tier vs ops tier vs finance/operator enforced server-side; buyers see only their case mirror; analytics denied to non-finance roles; financial reads audited |
| S-7 | Privacy | No buyer PII beyond existing ops visibility; affiliate/creator payout detail stays behind 49/50 scopes (trail shows references only); zero secrets in logs |
| S-8 | Reporting integrity | Aggregates derived from facts + events only; every figure decomposes to order ids; close snapshots frozen (re-runs byte-identical); no hand-adjusted dashboard numbers |
| S-9 | Alert integrity | Scorecard thresholds + anomaly rules config-owned and versioned; breaches page ops with the slice attached; kill-switch decisions recorded |
| S-10 | Plain-language boundary | Provider-native codes never reach buyers; buyer mirror shows "under review" only; internal notes never exposed |

## Rate limits (at amendment; billing-service Redis db 10)

| Surface | Limit | On breach |
|---|---|---|
| Console searches | 600/h/agent | 429 `throttled` |
| Ops actions | 120/h/operator | 429 |
| Approvals | 60/h/operator | 429 |
| Analytics reads | 300/h/finance role | 429 |
| Exports | 10/h/operator | 429 |

## Degrade matrix (at amendment)

| Failure | Behavior |
|---|---|
| Aggregates job down | Dashboards serve last refresh with its timestamp; facts and console unaffected; alert after 2 missed runs |
| Alerting down | Breaches logged; paging resumes on recovery; no silent loss |
| 49/50 scoped reads down | Trail renders without payout section (labeled "payout data unavailable"); console actions unaffected |
| Notification pipeline down | Receipt resends queue with backlog; order state unaffected |
| Approval store unavailable | Above-floor actions refuse (fail closed); below-floor actions unaffected |
| Search index stale | Lookup falls back to direct indexed queries; no cross-order leakage ever |

## Audit and observability (at amendment)

Audits (24-month): every console action with actor + reason, approval propose/approve/reject/expire, case transitions, corrections, close snapshots, exports, kill-switch flips. Metrics: grant-to-line counts, net revenue by slice, funnel stages, provider scorecards, dispute/refund rates, alert lead times, approval outcomes, trail latency — zero buyer labels.

## Support answers (at amendment)

"Where is my money?" → one trail from any identifier: status sentence, provider events, refund state, receipt. "Why was I charged?" → sales line + receipt + plan term version. "Why can't support refund this?" → state preconditions + ops escalation path. "Are we growing?" → funnel + revenue cuts that decompose to order ids. "Which rail earns its keep?" → scorecards with config thresholds. "Why does the number differ from last night?" → adjustment appended post-refresh; close snapshots explain frozen periods.
