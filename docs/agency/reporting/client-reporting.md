# Client Reporting (per-profile views/clicks/sales, rollups)

PLAN ref: P4-5 (leaderboard: views/clicks/CTR/sales). P4 scope — do NOT build in P1–P3. Metric definitions owned by `docs/link-to-bio/analytics/` (engagement) and `docs/shop/` + `monetize/sales/` (revenue); this doc owns agency scoping, rollups, and the leaderboard — never new counting.

## Purpose

Show agencies and their clients exactly how every managed profile performs — views, clicks, CTR, sales — individually and rolled up, with a leaderboard that ranks profiles fairly. Reporting reads existing aggregates through org-shaped lenses; no metric is recomputed, restated, or visible outside its org link, so numbers here always match the creator's own dashboard.

## Users

- Agency owner/manager: *As an Agency owner/manager, I want to compare client profiles, spot underperformers, and prove retainer value with combined reports so that managed performance is exact per profile and rolled up.* Compares client profiles, spots underperformers, proves retainer value with combined reports.
- Member: *As a Member, I want to see assigned clients' performance in working views so that daily work stays inside my assignment set.* Sees assigned clients' performance in working views.
- Client: *As a Client, I want to see my own profiles' performance identical to my dashboard plus rank context so that agency figures always match my own.* Sees their own profiles' performance (identical figures to their personal dashboard) plus their rank context where shared.

## Functional requirements

1. Per-profile client views: for each org-linked profile — views, clicks, CTR, engagement series, top links, revenue/units where shop data exists — rendered with the same components and formulas as the creator dashboard (shared read functions, not copied queries). A client comparing their dashboard to the agency view sees identical figures at the same `generated_at` or a bug is filed.
2. Combined reporting: org-level rollups (total views/clicks/revenue across linked profiles, per-profile contribution table, period compare) computed from the same aggregates at read time (no separate agency rollup store to drift). Custom report ranges within the underlying retention windows; exports (CSV/PDF-via-print) carry source + generated-at like all exports.
3. Leaderboard: rankable by views, clicks, CTR, revenue (one metric at a time, direction stated); minimum-activity floor for ranking eligibility (tiny-sample profiles ranked with a `low-traffic` badge, never hidden punitively); rank snapshots versioned per reporting period (last month's #1 stays #1 in last month's report after new data lands).
4. Scoping (absolute): staff see only org-linked profiles in their assignment set (managers: all linked; members: assigned only); clients see only their own linked profiles + their own leaderboard position context (never other clients' raw figures unless the org enables anonymized benchmarking — see Q2). Unlinked profiles vanish from all agency views immediately (roster unlink is the single enforcement point).
5. Scheduling/delivery (v1 lite): saved report views + manual send/share links (signed, expiring) for clients; scheduled emailed reports named v2 (waits on the campaign/scheduler posture — no bespoke mailer built here).
6. Discrepancy rule: agency view vs creator dashboard mismatch resolves by invalidating the agency read cache first (rollups are read-time; staleness is the only allowed cause, labeled with `as of`). No manual adjustments, no agency-side metric overrides — ever.

## Data / API touches

- No new metric tables. New: `report_views{saved_id, org_id, definition_json, created_by}` (saved definitions only — results always recomputed) + `leaderboard_snapshots{org_id, period, metric, rows_json, generated_at}` (versioned, immutable). Reads fan out to analytics + sales aggregates under org-link scoping; combined figures merged server-side. All PG via `worker/db/*`.
- Endpoints: `GET /api/v1/agency/reports/overview|profiles|leaderboard`, saved-view CRUD, share-link mint (signed, expiring), export (same audit discipline as all exports). Short-TTL private caching on rollups; unlink events purge related cache keys.
- Access control: every endpoint re-checks org membership + assignment + link status per request (reporting is a favorite IDOR target — client A must never reach client B via `profile_id` guessing).

## Acceptance criteria

- Identity drill: fixture figures match creator-dashboard values cell-for-cell across profiles, rollups, and exports at equal `generated_at`. *GWT: Given agency and creator-dashboard reads at equal `generated_at`; When compared; Then fixture figures match cell-for-cell across profiles, rollups, and exports.*
- Scope drill: member sees assigned only; client sees own only (+ own rank); unlinked profile disappears from views/exports/leaderboard within cache-purge SLA; cross-client `profile_id` guessing returns 403. *GWT: Given member, client, unlinked, and cross-client-guessing requests; When views/exports/leaderboard resolve; Then scoping holds, unlinked profiles vanish within cache-purge SLA, and guessing returns 403.*
- Leaderboard drill: ranking correct per metric with low-traffic badging; historical snapshot immutable after period close (new data does not rewrite it). *GWT: Given ranked profiles and a closed period; When leaderboard and snapshots evaluate; Then ranking is correct per metric with low-traffic badging and historical snapshots stay immutable.*
- Share drill: client share-links work without login until expiry, then 410; no session confusion with staff logins. *GWT: Given a signed client share-link before and after expiry; When opened; Then it works without login until expiry, then returns 410, with no staff-session confusion.*
- Load drill: 100-profile org rollup within read budget (no per-profile N+1 waterfall — batched aggregate reads). *GWT: Given a 100-profile org rollup; When reads fan out; Then the rollup completes within read budget via batched aggregate reads with no N+1 waterfall.*

## Out of scope

- Metric pipelines and formulas (`docs/link-to-bio/analytics/`, `monetize/sales/`); roster/linking enforcement (see `roster/team-roster.md`); editing sessions (see `access/act-as-user.md`); seat billing (see `billing/seat-billing.md`).
- Scheduled email delivery (v2 with campaign posture); white-label client portals (theming beyond standard tokens — not in v1).

## Open questions

- Q1: Report depth for clients (full analytics vs curated summary) — curated summary default with full on request?
- Q2: Anonymized cross-client benchmarking (percentiles) — opt-in per org, or never (strict isolation)?
