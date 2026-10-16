# Audience Insights - Exception Handling and Errors (feature 13)

Phase 1 BA · This slice adds no public intake, so visitor-facing errors do not exist here; dashboard messages are exact user-visible text. `(inherited)` marks feature 12 handling.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Out-of-plan range (incl. direct API) | Reads | `422 range_clamped` + `allowed`; no bypass via deep links | "Available on your plan: 7/30/90d" |
| Unknown dimension param | Reads | `422 dimension_unknown` with allowed list | "Unknown view - choose device, geo, referrer, UTM, or returning" |
| Downgrade mid-session | Reads | Entitlement re-resolved per request; picker re-clamps on next fetch | "Available on your plan: 7/30/90d" |
| Previous-period span pruned | Reads | Overlay disabled with reason; deltas render `null` where undefined | "Not enough history for a previous-period comparison" |
| Year-ago overlay requested | Reads | Toggle ships disabled (DEVIATION FLAGGED, BR-06); no partial comparison is fabricated | "Not available yet on your plan - needs longer history" |
| UTM value over-long / disallowed chars | Projection | Lowercased, charset-stripped, truncated at 64 chars + counted; never rejects the hit | (invisible) |
| UTM keyspace spike (typo-split campaigns) | Projection/read | Top-25 + `Other (n)` keeps payload bounded; cardinality gauge alerts ops | (invisible) |
| Untagged links (UTM tab) | Dashboard | Unlock CTA with editor link, not an empty chart | "Tag links with utm_campaign to unlock this view" |
| CTR under 30 views in-window | Dashboard | `not enough data` instead of noisy percentages | "Not enough data (needs 30+ views)" |
| Retention job failure | Ops | Alert; job retries next night; reads keep serving within window (extra rows are harmless) | (ops alert) |
| Retention dry-run drift | Ops | `retention.would_delete` vs `retention.deleted` investigated | (ops alert) |
| Foreign profile param | Reads | `403 foreign_profile` (inherited) | "You do not have access to this profile" |
| Reads rate-limited | Reads | Cooldown with retry timer | "Too many requests - retry in {n}s" |
| Stale rollup while reading | Reads | Stale chip + lag label (inherited); numbers never fabricated fresh | "Data delayed - last updated {time}" |
| Session expired | Reads | Redirect to SSO; return to the same tab | (auth-owned) |
