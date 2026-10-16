# Link Blocks - Context and Scope (feature 08)

Phase 1 BA · Feature 08 of 53 (link-to-bio) · Implemented by `docs/02-architecture-techlead/08-link-blocks/` (5 files). Extends the authoring domain of `profile-service`; consumers: feature 09 (theme treatment), feature 10 (SSR render), feature 12 (per-block analytics), features 17-21 (resume pointer).

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Ordered page content: blocks render in dashboard-defined order, and positions persist across reloads. | Drag order is identical on dashboard and public page (AC-01). |
| BO-2 | Frozen type superset: 13 types ship now; a 14th requires a spec amendment and a migration note. | Closed enum + DB CHECK; grep-gated in tests (AC-03). |
| BO-3 | Scheduling honesty: hidden or out-of-window blocks never leak publicly, and the dashboard always explains why. | Public windowed read drill green; `hiddenReason` shown per row (AC-02). |
| BO-4 | Data-only payloads: no HTML/JS/CSS fields exist; the renderer owns markup. | Injection drill: markup stripped, CSP holds (AC-05). |
| BO-5 | Bounded page weight: v1 cap of 250 blocks per profile. | 251st create -> `422 block_limit_reached` (AC-08). |

## In scope

- Block CRUD (create, edit, delete, duplicate) and atomic drag-reorder with dense server-assigned positions.
- Per-block visibility (`is_visible`) and schedule window (`visible_from` / `visible_until`).
- Per-type payload validation (13 types), link presentation options (sizes, thumbnail key, gallery carousel grouping), resume pointer block.
- Public windowed read for the SSR renderer: visible + currently scheduled, ordered by `position ASC, id ASC`.

## Out of scope

- Profile identity and namespace (feature 06); badge/followers (feature 07); theme treatment and avatar pipeline (feature 09); SSR markup, cache, purge (feature 10); analytics (feature 12).
- Resume documents and templates (features 17-21); shop products/courses/tips, leads, domains (their majors); P4 publishing/automations.

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | Create/read/update/delete/reorder own blocks | Authors page content; sees every block including hidden and out-of-window with reasons. |
| Visitor | Read via renderer only | Sees only visible, currently-scheduled blocks in served order. |
| Public-site service | Internal windowed read | Renders `/:username` from the internal blocks API; never writes. |

## Open questions (non-blocking; v1 defaults)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Max blocks per profile | 250 v1 cap, plan-configurable later | plan tiers ship block budgets |
| Q-02 | Carousel autoplay and thumbnail dimensions | autoplay off; platform-fixed 16:9 thumbnails | theme system requests per-block options |
| Q-03 | Position density as a DB exclusion constraint | service-serialized reorder (no DB exclusion) | contention evidence shows duplicate positions |
| Q-04 | `payload_json` shape checks in DB | validator-only | a bypassed-writer incident occurs |

Related specs: profile ownership `docs/01-requirements-ba/06-public-profile/02-user-stories-and-business-rules.md`; rendering `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md` (internal read pattern); resume pointer target features 17-21 (pending batch).
