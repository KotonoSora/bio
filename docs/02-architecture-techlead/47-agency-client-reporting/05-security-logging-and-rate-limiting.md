# Agency Client Reporting - Security, Logging, and Rate Limiting (feature 47)

Phase 2 TL · BA sources: `docs/01-requirements-ba/47-agency-client-reporting/` (BR-01..BR-11, NFR-01..NFR-11). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. Read-model surface: guard-first scoping and hide-by-absence are the controls.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Guard-first: every org-scoped read resolves role + `profileIds[]` via `AccessGuard` before any aggregate; per-request re-check; 45 down → fail closed beyond the ≤ 60 s cache | AC-03, AC-07 |
| S-2 | No new counting: report code contains no ad-hoc aggregate SQL (imports shared read components only — grep gate); parity mismatch marks, never restates | AC-01 + grep gate |
| S-3 | Org isolation: no cross-org refs exist; benchmarking surfaces do not exist (by absence); rollups bounded to guarded `profileIds` | AC-06 |
| S-4 | Share safety: tokens signed, SHA-256 at rest, expiry ≤ 30 days, `noindex`, read-only; curated serialization is a server-side field set (revenue never serializes); revoke + events immediate | AC-05 |
| S-5 | Privacy: aggregates only (no visitor-level rows); reads not individually audited; share create/revoke + view changes audited 24 months; ops reads scoped + audited | Data-minimization audit |
| S-6 | Honesty: degraded sources show reason + last-good timestamp; missing revenue hides with reason; never stale-as-fresh, never zero-filled | AC-08 |
| S-7 | Purge integrity: 45's events purge `ag:access:*` + revoke shares ≤ 60 s; snapshot table append-only | AC-07 |
| S-8 | Leaderboard integrity: one rank function, stable ties, daily idempotent snapshots; method stated on-screen | AC-04 |
| S-9 | Credential confinement: zero token material in logs/payloads; share tokens shown once; internal reads use service JWTs | Log + payload scan |

## Rate limits (gateway + app; Redis db 6 `ag:rl:*`)

| Surface | Limit | On breach |
|---|---|---|
| Per-profile report reads | 600/h/user | 429 `throttled` |
| Combined reports | 120/h/user | 429 |
| Leaderboard reads | 300/h/user | 429 |
| Saved-view writes | 60/h/org | 429 |
| Share create | 30/h/org + 10/h/profile | 429 |
| Share opens | 600/h/IP per token | 429 + clamp |
| Internal sales-summary reads | 600/h/service | 429 |

## Degrade matrix

| Failure | Behavior |
|---|---|
| 45's access read down | Scoped reads fail closed beyond the ≤ 60 s cache; cached reads continue briefly with last-good access facts |
| Billing sales-summary down | Revenue sections hide with reason + last-good note; other metrics unaffected |
| Metric read component down | That section shows reason + last-good timestamp; never stale-as-fresh |
| Snapshot job down | Leaderboard shows the latest snapshot date honestly; alert after 2 misses |
| Redis down | Access guard fails closed (no cache); rate limits degrade to conservative fixed windows |
| Share service edge (token store) down | Shares fail closed (410-neutral); owner/manager reads unaffected |

## Audit and observability

Audits (24-month): `report.share_created`, `report.share_revoked`, `report.view_saved/updated/deleted`, `report.parity_mismatch`, ops scoped reads. Metrics: `report_reads{surface}`, `cache_hit_rate`, `purge_latency`, `shares{state}`, `snapshot_results`, `parity_mismatches`, `degraded_sections`. Zero visitor-level or cross-org labels.

## Support answers

"Why is my number different?" → parity path: same read components as the dashboard; a mismatch is marked + under review — numbers are never restated. "Why can't I see a client?" → guard result: assignment/link state (45). "Why did a share stop working?" → expiry/revoke trail; unlink or membership change kills shares by design. "Why is revenue missing?" → billing data availability (`available:false`) — hidden honestly, never zero-filled. "Why can't I compare agencies?" → isolation: no cross-org surface exists.
