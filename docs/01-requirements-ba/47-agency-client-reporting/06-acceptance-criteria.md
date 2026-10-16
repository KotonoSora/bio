# Agency Client Reporting - Acceptance Criteria (feature 47)

Phase 1 BA · BDD; drills run against `docs/02-architecture-techlead/47-agency-client-reporting/` implementations.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Accuracy match drill | Fixture aggregates for a profile; the creator's own dashboard | The org report and the dashboard render the same period | Values match exactly (same read components); a seeded mismatch marks the metric and never restates it |
| AC-02 | Rollup exactness drill | Three linked profiles with fixture numbers | The combined view renders | Rollup = exact sum of per-profile rows; per-profile rows always visible; no modeled numbers exist |
| AC-03 | Scoping drill | Owner/manager, member (assigned subset), client, unassigned member | Each reads reports | Owner/manager see all linked; member assigned only; client own only; unassigned 403; every read re-checks |
| AC-04 | Leaderboard fairness drill | Ties and zero-traffic fixtures across views/clicks/CTR | Rankings compute | Method stated on-screen; ties break clicks desc then stable id; zeros rank last honestly; daily snapshots record standings |
| AC-05 | Share lifecycle drill | Curated + full shares; expiry; manual revoke; unlink and membership-change events | Each path runs | Curated default excludes revenue; full requires owner/manager; expiry ≤ 30 days; revoke immediate; unlink/membership events kill shares |
| AC-06 | IDOR drill | Cross-org refs, unlinked profile refs, another org's share/view ids | Each is attempted | 404/403 with zero rows; no cross-org aggregate exists; share tokens are per-report and expiring |
| AC-07 | Purge timing drill | A live cached report; an unlink or membership change fires | The next read runs | Cache purged ≤ 60 s; shares dead immediately; scoping reflects fresh facts |
| AC-08 | Honesty drill | Missing revenue data, zero-traffic profile, downed metric source | Each section renders | Sections hide with reasons; zeros shown honestly; degraded source shows reason + last-good timestamp |
