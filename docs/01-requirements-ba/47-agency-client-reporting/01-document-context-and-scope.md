# Agency Client Reporting - Document Context and Scope (feature 47)

Phase 1 BA · Feature 47 of 53 — agency 3/3. Shows agencies and their clients exactly how every managed profile performs — views, clicks, CTR, sales — individually and rolled up, with a leaderboard that ranks fairly. Sources: `agency/reporting/client-reporting.md`, `platform/delivery/agency-teams.md` (FR-4 leaderboard). Implemented by `docs/02-architecture-techlead/47-agency-client-reporting/`. Service: analytics-service (port 8085, `analytics_db`, Redis db 6) — metric definitions owned by 12/15/31 (engagement) and billing (revenue); this feature owns scoping, rollups, and the leaderboard — never new counting.

## Context

Numbers here must always match the creator's own dashboard: reporting reads the same aggregates (`profile_views`, `link_clicks`, `link_hits_daily`) through org-shaped lenses — same read components, no recomputation, no restatement. Revenue/units come from billing's internal summary read; where shop data does not exist, revenue sections hide honestly (never zero-filled fakes). Access facts come from 45's internal access read (role + assigned profiles), cached ≤ 60 s and purged by 45's events. No metric is visible outside its org link.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Per-profile exactness: views/clicks/CTR (+ revenue where data exists) identical to the creator's dashboard |
| OBJ-02 | Combined rollups read-time; per-profile truth stays first-class — a rollup never hides a profile |
| OBJ-03 | Fair leaderboard: stated method, deterministic tie-breaks, daily snapshots; no invented weighting |
| OBJ-04 | Scoping: owner/manager all linked profiles; member assigned only; client own only; shares signed, expiring, read-only |
| OBJ-05 | Honest gaps: missing data hides sections with reasons; never fake zeros; never stale-as-fresh |
| OBJ-06 | Unlink/membership effects purge caches and revoke shares immediately (event-driven) |

## Scope

In scope (SC-01..SC-06): `report_views` + `leaderboard_snapshots` (V4 in `analytics_db`); per-profile report; combined rollup; leaderboard + daily snapshots; saved views + share links (curated/full); access integration with 45's internal read; cache purge on 45's events; revenue delegation to billing's internal summary read.

Out of scope: metric definitions and counting (12/15/31); sales computation (billing/shop); roster, roles, act-as (45); seat billing (46); cross-org benchmarking (never in v1); a client-facing report builder (v1 is saved views + curated shares); report exports (not specified — shares cover delivery).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Owner/manager | C/R/U/D saved views + shares; R all linked profiles | Compares clients, spots underperformers, proves retainer value |
| Member | R assigned clients' working views | Sees assigned performance only |
| Client | R own profile (via curated share or own login) | Own numbers; never other clients |
| Support | R scoped (audited) | Resolves report questions; never cross-org |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Client report depth (reporting Q1) | Curated summary by default (views/clicks/CTR + trend + top links); full detail (including revenue) only when an owner/manager shares it explicitly |
| Q-02 | Anonymized benchmarking (reporting Q2) | Never in v1 — strict org isolation; no cross-org aggregates exist anywhere |
| Q-03 | Leaderboard metrics | Views, clicks, CTR tabs; a sales tab appears only where revenue data exists; ties break by clicks desc, then stable profile id; zero-traffic profiles rank last with honest zeros (no invented minimum-volume guard) |
| Q-04 | Share lifetime | ≤ 30 days, signed, revocable, read-only, `noindex`; revoked immediately on unlink or membership change |
| Q-05 | Rollup method | Read-time sum of per-profile truth over the selected period; ranges stated with timezone; no restated or modeled numbers exist |

## Constraints carried

No new counting — reporting code contains no ad-hoc aggregate SQL (grep gate; shared read components only); period filters 7/30/90/custom ≤ 366 days; aggregates only (no visitor-level data); RLS + per-request scoping; zero token material in logs or payloads.
