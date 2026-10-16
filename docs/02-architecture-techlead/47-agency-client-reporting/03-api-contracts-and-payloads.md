# Agency Client Reporting - API Contracts and Payloads (feature 47)

Phase 2 TL · Report + share contracts. Auth/rate rules: `docs/02-architecture-techlead/47-agency-client-reporting/05-security-logging-and-rate-limiting.md`. Problems follow RFC 9457.

## Endpoints

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/agency/reports/profiles/{profileId}?from=&to=` | session (guard) | - | `200 ProfileReport` | 403 `report_scope_denied`; 404; 422 `range_invalid`; 429 |
| `GET /api/v1/agency/reports/combined?orgId=&profileIds=&from=&to=` | session (guard) | - | `200 CombinedReport` (rollup + per-profile rows) | 403; 422 `range_invalid`, `selection_invalid` (> 50); 429 |
| `GET /api/v1/agency/reports/leaderboard?orgId=&period=&metric=` | session (guard) | - | `200 LeaderboardView` (method + snapshotDate + rows) | 403; 422; 429 |
| `POST /api/v1/agency/reports/views` | session + CSRF (owner/manager) | `ViewCreateRequest` | `201 ViewView` | 403; 422; 429 |
| `GET /api/v1/agency/reports/views?orgId=` | session (owner/manager) | - | `200 List<ViewView>` | 403; 429 |
| `PATCH /api/v1/agency/reports/views/{id}`, `DELETE` | session + CSRF (owner/manager) | `ViewCreateRequest` partial | `200` / `200 {deleted: true}` | 403; 404; 429 |
| `POST /api/v1/agency/reports/shares` | session + CSRF (owner/manager) | `ShareCreateRequest` | `201 {shareId, url}` (token shown once) | 403; 422 `validation_failed` (scope/expiry); 429 |
| `POST /api/v1/agency/reports/shares/{id}/revoke` | session + CSRF (owner/manager) | - | `200 {revoked: true}` (audited) | 403; 404; 429 |
| `GET /api/v1/reports/share/{token}` | anonymous (signed) | - | `200 SharePayload` (curated or full per scope) | 410 `share_inactive`; 429 |

```kotlin
data class TrendPoint(val day: String, val value: Long)
data class TopLink(val blockId: String, val label: String, val clicks: Long)
data class RevenueBlock(val available: Boolean, val revenueVnd: Long? = null, val units: Long? = null)
data class ProfileReport(val profileId: String, val from: String, val to: String,
  val views: Long, val clicks: Long, val ctr: Double?, val trend: List<TrendPoint>,
  val topLinks: List<TopLink>?, val revenue: RevenueBlock, val degraded: List<String>)
data class CombinedReport(val rollup: ProfileReport, val profiles: List<ProfileReport>)
data class LeaderboardRow(val profileId: String, val value: Double, val rank: Int)
data class LeaderboardView(val metric: String, val method: String, val snapshotDate: String,
  val rows: List<LeaderboardRow>)
data class ViewCreateRequest(val orgId: String, val name: String, val profileIds: List<String>,
  val metrics: List<String>, val periodDays: Int)
data class ShareCreateRequest(val orgId: String, val profileIds: List<String>,
  val scope: String, val expiresInDays: Int)  // <= 30
```

## Rules

- Guard first: every org-scoped read resolves role + `profileIds[]` via `AccessGuard` (45's access read, cached ≤ 60 s) before any aggregate is touched; reads re-check per request.
- Parity: values come from the shared read components (12/15/31) and billing's internal summary; a mismatch marks the metric (`degraded`) and alerts — never restates; missing revenue serializes `RevenueBlock(available=false)` and the UI hides the section.
- Rollup: literal sum of per-profile rows; ≤ 50 profiles per request; per-profile rows always present.
- Leaderboard: `method` states the ranking + tie-breaks on-screen; `snapshotDate` is the latest daily standing; sales metric only when billing data exists.
- Shares: token shown once, SHA-256 at rest; expiry ≤ 30 days; curated scope serializes a fixed field set (revenue fields never leave the server); revoke + unlink/membership events kill immediately; expired/revoked → 410 neutral.
- Saved views: owner/manager CRUD per org; members have no saved-view surface.
- No visitor-level rows, cross-org refs, or token material in any payload.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `report_scope_denied` | 403 | "This client isn't assigned to you." |
| `range_invalid` | 422 | "Pick a range up to 366 days." |
| `selection_invalid` | 422 | "Up to 50 profiles per combined report." |
| `share_inactive` | 410 | Neutral expired/revoked page |

Reused: `throttled`, `validation_failed`.
