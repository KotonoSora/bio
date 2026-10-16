# Agency Client Reporting - Workflow and User Journey (feature 47)

Phase 1 BA · W-1..W-5. Rules: `docs/01-requirements-ba/47-agency-client-reporting/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/47-agency-client-reporting/03-api-contracts-and-payloads.md`.

## W-1 Per-profile report

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner/manager/member | opens a client profile report | access check via 45's read (cached ≤ 60 s) → period resolved | BR-05 |
| 2 | System | metrics resolve | views/clicks/CTR + trend from shared read components; top links (31); revenue section only where billing data exists | BR-01, BR-02 |
| 3 | System | a source is down | section shows reason + last-good timestamp; nothing fake renders | BR-08 |
| 4 | System | dashboard parity check | values equal the creator's dashboard (same components) or the metric is marked, never restated | BR-01 |

## W-2 Combined rollup

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner/manager | selects profiles + period | read-time sum across selections; per-profile rows shown beside the rollup | BR-03 |
| 2 | System | selection includes an unlinked profile | impossible — selections come from linked profiles only | BR-09 |
| 3 | Owner/manager | saves the scope | saved view per org (owner/manager shared) | BR-07 |

## W-3 Leaderboard

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner/manager | opens the leaderboard | ranks by the chosen metric over the period; method stated on-screen | BR-04 |
| 2 | System | ties occur | clicks desc, then stable profile id | BR-04 |
| 3 | System | daily cadence | `leaderboard_snapshots` row per org/metric records the standing | BR-04 |
| 4 | System | sales tab request without revenue data | tab hides honestly with a reason | BR-08 |

## W-4 Share

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner/manager | shares a report (curated or full) | signed token (hashed at rest), expiry ≤ 30 days, `noindex`, read-only | BR-06 |
| 2 | Client | opens the share link | curated summary (or full if explicitly shared); no login required | BR-06, Q-01 |
| 3 | Owner/manager | revokes the share | dead immediately; audit | BR-06, BR-10 |
| 4 | System | share expires | neutral expired page; no enumeration | BR-06 |

## W-5 Unlink / membership change

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | 45 | emits `org.client_link_changed.v1` / `org.membership_changed.v1` | report caches purge ≤ 60 s; shares for the affected profiles/roles revoke immediately | BR-09 |
| 2 | System | next read | scoping re-checked from fresh access facts | BR-05 |
| 3 | Creator (outside org) | opens their own dashboard | numbers unchanged — reporting never owned them | BR-01 |
