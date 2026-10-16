# Agency Client Reporting - User Stories and Business Rules (feature 47)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/47-agency-client-reporting/`. Scope: `docs/01-requirements-ba/47-agency-client-reporting/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Agency owner/manager | compare client profiles, spot underperformers, prove retainer value with combined reports | managed performance is exact per profile and rolled up | High |
| US-02 | Member | see assigned clients' performance in working views | I can work my book without seeing the whole agency | High |
| US-03 | Client | see my own profile's report via a curated share or my login | I know what the agency delivers | High |
| US-04 | Owner/manager | rank profiles on a fair, stated method with dated standings | coaching decisions aren't guesswork | Medium |
| US-05 | Support | resolve "why is my number different?" from one evidence path | reporting disputes end in facts | Medium |

## Business rules

- **BR-01 Metric ownership (no recomputation).** Views/clicks/CTR and trend series come from the shared read components over `profile_views`/`link_clicks` (12/15) and short-link hits (31); revenue/units come from billing's internal summary read. Reporting code contains no ad-hoc aggregate SQL (grep gate). If a metric value ever differs from the creator's own dashboard, the report refuses to render that metric (marks it) — never restates it.
- **BR-02 Per-profile report.** Period filters 7/30/90/custom ≤ 366 days; shows views, clicks, CTR, trend series, top links (31), and revenue/units where billing data exists; ranges state timezone and boundaries.
- **BR-03 Combined rollup.** Read-time sum across the selected linked profiles over the period; per-profile rows are always shown alongside the rollup; the rollup equals the sum of per-profile truth exactly (no modeled numbers).
- **BR-04 Leaderboard (stated method).** Ranks linked profiles by the chosen metric (views/clicks/CTR; sales tab only where revenue data exists) over the period; method stated on-screen; ties break by clicks desc, then stable profile id; zero-traffic profiles rank last with honest zeros; daily `leaderboard_snapshots` record the standing for audit and trend.
- **BR-05 Scoping (per request).** Access facts come from 45's internal access read (role + assigned profiles), cached ≤ 60 s; owner/manager see all linked profiles; members see assigned clients only; clients see their own profile only; every read re-checks; cross-org reads are impossible by construction.
- **BR-06 Shares.** Signed, expiring ≤ 30 days, revocable, read-only, `noindex`; curated by default (no revenue); full detail only by explicit owner/manager action; shares die immediately on unlink or membership change (event-driven purge).
- **BR-07 Saved views.** Owner/manager save scope + metrics + period per org (shared across owner/manager roles); members have their own default working view; saved views never change another role's access.
- **BR-08 Honesty.** Missing data hides sections with reasons ("no sales yet", "analytics started {date}"); zero-traffic profiles show zeros honestly; no fake fills; a metric source that is down shows a reason + last-good timestamp (≤ 15 min cache TTL) — never stale-as-fresh.
- **BR-09 Unlink/membership effect.** `org.client_link_changed.v1` / `org.membership_changed.v1` purge report caches ≤ 60 s and revoke shares immediately; unlinked profiles vanish from org reports (their numbers remain the creator's).
- **BR-10 Audit + privacy.** Share create/revoke and saved-view changes audit (24 months); individual report reads are not audited (privacy); aggregates only — no visitor-level rows ever surface; ops reads are scoped and audited; zero token material.
- **BR-11 No-hostage symmetry.** Reporting follows 46's posture: lapse freezes staff tooling but never client report access to their own profile; clients can always see their own numbers via their own login.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/47-agency-client-reporting/06-acceptance-criteria.md`: accuracy match, scoping, rollup exactness, leaderboard fairness, share expiry/revocation, discrepancy, IDOR. GWT forms live there.
