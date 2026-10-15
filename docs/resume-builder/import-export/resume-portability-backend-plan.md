# Resume Portability Backend Plan (export + import + adapters — server build steps)

PLAN ref: P2-5 scoped to resume (lossless round-trip, atomic import, draft-only adapters, fixture gate) + S-1/S-2/S-4/S-5/S-7. BA source: `docs/resume-builder/import-export/resume-portability.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Contracts in `docs/resume-builder/import-export/resume-portability-database-plan.md` (envelope, fixture, validation, atomicity — never redefined here). Wizard screens in the portability frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for lock-in-free resumes: streaming export, atomic validated import with dry-run, one-shot LinkedIn/GitHub adapters landing as drafts, and the fixture gate that proves schema changes stay migratable. Follow steps 1–8 in order. Imports are the adversary's cheapest path to stored data — every step below treats the file as hostile until proven otherwise.

## Users

- Backend developer: executes steps 1–8 in package `com.app.resume.portability`.
- QA: runs round-trip, rejection, draft-only, IDOR, XSS, and fixture drills.
- Job seeker / new user: backs up, restores, moves accounts, bootstraps from LinkedIn/GitHub.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`resume` module). Monolith pros: import atomicity is one transaction over documents + snapshots + block relink (partial restores impossible); adapters reuse the snapshot validator in-process; one audit log. Microservices cons avoided: an import service would distribute the atomicity this slice exists to guarantee. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, actuator, micrometer. Load posture: exports stream (chunked, backpressure-safe — 10-year histories never materialize in memory); imports bounded by the 2MB cap; adapter fetches are server-side with timeouts (10s), redirect limits (3), and response caps (1MB) — the fetch target is untrusted infrastructure.
2. Step 1 — contracts: implement the envelope, fixture path, and validation rules exactly as the database plan specifies (no parallel definitions — this file adds flows, not shapes).
3. Step 2 — `GET /api/v1/export/resume` (owner-scoped): query documents + snapshots + linked resume blocks → serialize envelope → stream download (`Content-Disposition: attachment; filename=resume-backup-{date}.json`). Unlimited, ungated (your data leaves freely — no quotas by design); audited. Export of another user's `profile_id` → 403 (all calls scope by session `user_id` + RLS; there is no cross-profile export path to probe).
4. Step 3 — `POST /api/v1/import/resume` (multipart file): gate order — size ≤2MB (`import_too_large`, pre-parse) → JSON parse (`import_malformed` with position) → `schema_version` known (`import_version_unsupported` naming found vs supported) → closed-schema validation → URL allowlist + string sanitization → `?dry_run=true` returns the would-create summary (counts + titles + warnings, zero writes) → confirmed call runs ONE transaction (documents + snapshots with fresh ids + block relink + `published_version` re-resolution by version number) → audit. Any failure → rollback to zero rows (asserted by row-count test, not by code reading).
5. Step 4 — `POST /api/v1/import/linkedin`: accept pasted profile URL OR uploaded LinkedIn export file (file path preferred — URL scraping stays out of v1 for ToS reasons); map positions/education/skills into the resume schema → create as DRAFT document (no snapshot published, nothing public) → response flags fields needing review (`review_required: [field paths]` — adapter output is never trusted as final). Requires explicit user review + Save Snapshot before anything renders publicly (the draft-only rule, enforced by creating documents with `is_public=false` and no snapshots until save).
6. Step 5 — `POST /api/v1/import/github`: input handle only (no token in v1); server-side fetch of public repos/profile (timeout/redirect/cap per step 1 load posture; client never sees tokens because none exist); map pinned/top repos + bio into projects/links draft → same draft-only landing + review flags as LinkedIn. Rate-limit per handle (adapter endpoints are SSRF-shaped: bounded fetch + allowlist egress are the guardrails).
7. Step 6 — bot/abuse hardening (uploads are the attack surface): Turnstile on import + adapter endpoints (S-5); per-user daily import quota (burst imports are exfiltration-shaped OR bomb-shaped — either way throttled); 2MB cap + parse timeouts defeat decompression/parse bombs; adapter fetch SSRF guards (no intranet egress, DNS-pinned? minimum: block private ranges + redirect cap); all abuse counted (`import.rejected{reason}`).
8. Step 7 — fixture gate: CI job imports `fixture-resume.json` on an empty database every build (schema change breaking the fixture fails `./gradlew build` — the migratability proof is a gate, not a checklist item). Round-trip test (fixture + one real resume) diffs ids/timestamps only.
9. Step 8 — tests + container: round-trip test; rejection matrix (malformed/oversized/newer-version → exact codes + zero rows); draft-only test (adapter import changes nothing public until save + publish); IDOR test (cross-profile 403 + RLS deny); XSS test (malicious import strings stripped on dashboard preview, public cv, AND re-export — triple-surface assertion); `@WebMvcTest` per code; same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (no new tables; writes go to documents/snapshots/blocks through the snapshot backend plan's invariants — imports respect single-public, versioning, and linkage rules, never bypass them). Endpoints: `GET /api/v1/export/resume` (streaming download); `POST /api/v1/import/resume` (`?dry_run` + confirm, atomic); `POST /api/v1/import/linkedin`; `POST /api/v1/import/github`. All owner-scoped, throttled, audited.

## Acceptance criteria

- Round-trip drill: fixture + real resume re-export diffs ids/timestamps only.
- Rejection drill: each malformed/oversized/newer file → exact code + zero rows (count-asserted).
- Draft-only drill: adapter imports change zero public state until Save Snapshot + publish (cv curl unchanged).
- Security drill: cross-profile 403 + RLS deny; triple-surface XSS strip; import bursts throttled; SSRF egress blocked (private-range probe fails closed); secrets grep clean.
- Fixture drill: clean-database fixture import green every build.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Envelope/fixture/validation contracts (database plan above); wizard screens (frontend plan); snapshot versioning internals (snapshot backend plan); template rendering; share links; full profile+blocks backup, shop/lessons/leads/domains (their majors); continuous sync (one-shot v1); paid delivery (shop + P3).

## Open questions

- Q1: LinkedIn URL scrape — stay file-paste-only in v1 (current), or accept the ToS risk?
- Q2: Scheduled emailed backups — on-demand only (current), or P4 automation?
