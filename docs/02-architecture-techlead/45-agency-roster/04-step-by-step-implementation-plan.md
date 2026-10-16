# Agency Roster - Step-by-Step Implementation Plan (feature 45)

Phase 2 TL · Package `com.app.account.agency`. BA sources: `docs/01-requirements-ba/45-agency-roster/`. Order: schema + role matrix first (every endpoint derives from it); act-as last (its gates consume everything else).

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V4__agency_roster.sql`: six tables + indexes (incl. partial unique presence lock) + RLS + append-only audit grants + drills | V3 applied | V4 drills green |
| T-02 | BE | `RoleMatrix`: server-side table generating the endpoint fixture; matrix checks on every route | T-01 | AC-02 |
| T-03 | BE | `OrgService`: create/read/patch, typed-confirm delete + grace, restore, hard-delete job | T-01 | AC-11 |
| T-04 | BE | `InviteService`: token mint (hash at rest), accept/decline/revoke, expiry job, invite-to-register, re-invite limits | T-02 | AC-01 |
| T-05 | BE | Membership transitions: role/status changes, suspend/reinstate, removal; audit before/after; instant-effect cache bound | T-02 | AC-03 |
| T-06 | BE | `LinkService`: staff-initiated pending, owner accept, either-side unlink + staff assignments (`client_assignments`) + event emission | T-02 | AC-04 |
| T-07 | BE | Subscription seam: staff-invite + act-as entitlement checks (46) + refusal path | T-04 | AC-12 |
| T-08 | BE | `ActSessionService`: gates, step-up gate, presence lock, TTLs (30 min/8 h), ≤ 3 cap, end/revoke/suspend paths | T-03..T-06 | AC-05, AC-09, AC-10 |
| T-09 | BE | Act-as identity class: token mint + claims; baseline 08 cross-edit; downstream allowlist seam documented + drilled | T-08 | AC-06 |
| T-10 | BE | Audit extension: `on_behalf_of_profile` + before/after; client history view; append-only verification | T-08 | AC-07 |
| T-11 | BE | Internal routes: seats (46) + access (47) (service JWT; counts + role/profileIds from roster) | T-05 | Seat + access seam drills |
| T-12 | FE | Users/Managers tabs, org switcher, invite dialogs, role/suspend/remove flows, delete/restore dialog | T-03..T-06 | UI flows complete |
| T-13 | FE | Act-as surfaces: start with step-up prompt, presence + act banners, history, client revoke + complaint flows | T-08, T-10 | UI flows complete |
| T-14 | BE/DevOps | Drills: matrix fixture, invite single-use, presence lock, effect timing, audit replay, delete grace, RLS probes, velocity alerts | T-01..T-13 | AC-01..AC-12 |
| T-15 | DevOps | Metrics + alerts: invites by state, act sessions active/denied by condition, revocations, complaint freezes, velocity anomalies | T-08..T-14 | NFR-11 |

## Sequencing notes

1. T-02 before every route: the matrix is the single authorization table; no endpoint ships with an ad-hoc check.
2. T-08/T-09 are the trust core: step-up freshness, presence lock, and the identity class seam are reviewed with the account-service owner and drilled before any client-facing rollout.
3. T-10's append-only grants (no UPDATE/DELETE on audit rows) are a CI gate.
4. T-07 consumes 46's entitlement read — land with 46's subscription endpoints or refuse staff invites honestly until they exist.
5. Feature gate: E4 scope audit — zero agency tables/endpoints in E0–E3 images (the skeleton's behavior limit made mechanical).

## Definition of done

AC-01..AC-12 green; matrix fixture + append-only + identity-class greps green in CI; full-tree validation (word caps + backticked path resolution) green; `MIGRATION.md` row 45 flips to `done` at the batch tail with `agency/roster/team-roster.md`, `agency/access/act-as-user.md`, and `platform/delivery/agency-teams.md` deleted.
