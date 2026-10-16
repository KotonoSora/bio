# Sales (Ledger, Analytics, Order Console) - Contracts and Payloads (feature 51) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/51-sales/`. GATED: none of these routes may appear in OpenAPI, the gateway, or any image until the spec amendment (AC-01). Reuse contracts: `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` (registry + events), `docs/02-architecture-techlead/38-refunds/` (refund execution), `docs/02-architecture-techlead/49-affiliate/` and `docs/02-architecture-techlead/50-advertising/` (payout ledger reads).

## Endpoints (reserved, absent until the amendment)

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/v1/ops/orders/search?q=` | support/ops role | Five identifier kinds: orderCode, buyer email/account, provider refs, receipt/credit-note ids; unknown → clean no-result |
| `GET /api/v1/ops/orders/{orderCode}/trail` | support/ops role | Assembled: order, canonical status, provider events, sales lines + adjustments, receipts, entitlement effects, attributions, scoped payout rows; p95 ≤ 500 ms |
| `POST /api/v1/ops/orders/{orderCode}/actions/{action}` | role per action | Support tier: `resend-receipt`, `re-drive-verify`, `escalate`; ops tier: `cancel-unpaid`, `link-exception`, `initiate-refund`, `subscription-fix`, `campaign-pause`; `?dryRun=true` previews exact effects; re-validates at execution |
| `POST /api/v1/ops/orders/{orderCode}/cases`, `GET /api/v1/ops/cases`, `PATCH /api/v1/ops/cases/{id}` | support/ops role | Statuses `open/investigating/waiting_buyer/resolved`; buyer mirror shows "under review" only |
| `POST /api/v1/ops/approvals`, `POST /api/v1/ops/approvals/{id}/approve\|reject` | ops/finance role | Maker-checker above the config floor; proposer ≠ approver; 24 h expiry; expired cannot execute |
| `GET /api/v1/ops/sales/funnel` | finance/operator role | Stages × slices (provider, plan/product, campaign, device class, day); hourly aggregates |
| `GET /api/v1/ops/sales/revenue` | finance/operator role | Cuts with decomposition links to order ids; gross vs net-of-known-fees columns |
| `POST /api/v1/ops/sales/close`, `GET /api/v1/ops/sales/close/{period}` | finance role | Versioned frozen snapshot; re-runs byte-identical |
| `GET /api/v1/ops/sales/scorecards`, `GET /api/v1/ops/sales/alerts` | operator role | Provider health + anomaly feed; thresholds config-owned; breaches paged |
| Scoped payout reads (49/50 register at amendment) | service JWT | Business trail shows payout state references only; zero cross-earner detail |

```kotlin
data class TrailView(val order: JsonNode, val status: String, val providerEvents: List<JsonNode>,
  val salesLines: List<JsonNode>, val adjustments: List<JsonNode>, val receipts: List<JsonNode>,
  val effects: List<JsonNode>, val attributions: List<JsonNode>, val payoutRefs: List<JsonNode>)
data class ActionPreview(val action: String, val effects: JsonNode, val eligible: Boolean, val reason: String?)
data class ApprovalView(val id: String, val action: String, val proposer: String, val status: String, val expiresAt: String)
data class ScorecardView(val provider: String, val successRate: Double, val medianTimeToPaidMs: Long,
  val webhookLagP95Ms: Long, val sweepRescueRate: Double, val refundRate: Double, val disputeRate: Double)
data class CloseSnapshotView(val period: String, val payload: JsonNode, val createdBy: String, val createdAt: String)
```

## Rules

- The console orchestrates: each action delegates to the owning feature's endpoint (33/34/36/38/40); no second lifecycle or financial state exists here.
- Stale-view actions fail closed; dry-run previews equal executions; bulk actions return per-order results.
- Facts append-only: no route can edit or delete a sales line or adjustment; corrections append with reason + actor.
- Above-floor money-moving actions require an approval record; approver ≠ proposer; expired approvals never execute.
- Analytics endpoints return aggregates only; every figure drill-downs to order ids; affiliate/creator detail stays behind 49/50 scopes.
- Provider-native codes are never quoted to buyers; plain-language state mapping is server-owned.
- No buyer PII beyond existing ops visibility; financial reads audited.

## Error catalog (reserved)

| Code | Status | UI Behavior |
|---|---|---|
| `no_result` | 200 (empty) | "No order found." |
| `no_sales_line` | 409 | Ops: "No sales line for this order — check grant state." |
| `ineligible_state` | 409 | Preconditions stated; action refused |
| `stale_view` | 409 | "This order changed — review the current state." |
| `role_required` | 403 | "Requires ops role." |
| `second_approver_required` | 409 | "A second approver is required." |
| `approval_expired` | 410 | "This approval expired — propose again." |
| `fees_unconfirmed` | - | Net-of-known labels "fees unconfirmed" |
| `snapshot_immutable` | 409 | Frozen period cannot change; adjustments stay open-period |
