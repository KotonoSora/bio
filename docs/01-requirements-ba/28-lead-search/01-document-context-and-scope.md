# Lead Search - Context and Scope (feature 28)

Phase 1 BA · Feature 28 of 53 — leads 2/2. Legacy source: `leads/manage/lead-search.md` (folded; deleted with this batch). Realized by `docs/02-architecture-techlead/28-lead-search/`; a module of `profile-service` on the same `profile_db` + RLS baseline. Capture mechanics are feature 27: `docs/01-requirements-ba/27-lead-forms/`.

## Purpose

Give creators a working view of their contacts: search by email or name, filter by source/status/consent/date/form/tag, open a full per-lead timeline, and act (note, tag, suppress, re-invite, export) — without ever seeing another creator's data or touching platform-wide audiences. Search is the daily tool; the audience machinery underneath belongs to the monetize batch.

## Objectives

| # | Objective |
|---|---|
| O-1 | Find any contact in two keystrokes: trigram substring search, typo-tolerant, exact-match first, pre-scoped to the creator. |
| O-2 | Work the list: combinable filters with countable results; the count shown before export (no count/export skew). |
| O-3 | Trust the record: one chronological timeline merging capture, consent, notes, tags, suppression, and (forward) purchase/booking/automation references — every entry actor-stamped. |
| O-4 | Honest actions: notes and tags are append-only and attributed; suppression is permanent and propagates; no manual consent grants and no bulk-message bypass exist. |
| O-5 | Leave cleanly: export is the only egress (audited, column-stable CSV, short-lived owner-scoped grant); purge removes every workspace artifact with the account. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | R own leads; C notes; C/D own tag assignments; C suppression; C export jobs; R/U read state | finds "the buyer from Saturday's drop," checks consent, notes, tags, suppresses on request |
| Support (future agency seat, 45-47 forward) | same creator-scoped view; every action attributed | help stays inside the creator's scope; no extra reach |
| Contact (indirect) | none | suppression honored, history provable |
| Ops | R audit | investigates abuse, never edits leads |

## In scope

Search (trigram, min 2 chars, exact-first, recent-first default, per-account query history); closed-set combinable filters + count discipline; detail timeline (lead events + consent history + provenance + notes/tags; forward read-only references: orders/booking/automation); notes; tags with per-account closed vocabulary; manual suppression with reason; re-invite with one-tap unsubscribe; async CSV export (column-stable, streamed, short-lived grant); mark-read; purge extension.

## Out of scope

Capture mechanics (feature 27); list lifecycle/hygiene, consent classes/preference center, broadcasts (monetize batch: `monetize/marketing/*`); campaigns and campaign-of-origin filter values (gated until broadcasts ship; the filter slot is recorded, not populated); retargeting/ad audiences (monetize batch); automation-built contacts (41-44 — correlated read-only, never merged); checkout opt-in capture (33-34 via feature 27's seam); agency seats themselves (45-47).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 search latency + async threshold | p95 ≤ 400 ms at 100 000 leads per creator; count p95 ≤ 300 ms with the same predicates as export; giant selections are the norm — all exports are async jobs (no sync path exists to time out). |
| Q2 tag vocabulary cap | 50 tags per account; tag strings ≤ 40 chars; auto-added on first use until the cap; no rename/delete; foreign vocabularies are invisible. |
| Q3 export ownership | **DEVIATION FLAGGED (first use in this batch)**: L0 FR4 requires the audited CSV export (column-stable, streamed, short-lived grant) to ship with this batch; legacy 28's delegation to the audience doc is superseded — the monetize/audience batch consumes this export surface instead of rebuilding it. Rationale: duplication risk, and L0 exit drills depend on the export existing here. |
| Q4 support seat | Forward-only: the shared view and actor attribution are designed now; the agency batch (45-47) wires the seat. |
