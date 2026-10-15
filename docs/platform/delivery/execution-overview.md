# Execution Overview (BA specs to containerized increments)

PLAN ref: full build order P0→P5 + Security checklist S-1…S-7. TRACEABILITY: each increment below cites its BA doc set and PLAN step; no increment contradicts its BA spec without a flagged deviation. Stack baseline: `platform/stack/spring-boot-baseline.md` (Boot 4.1.1) + `platform/stack/kotlin-baseline.md` (Kotlin 2.4.21) + `platform/stack/gradle-build.md` (Gradle 9.8.1) + `platform/stack/postgres-baseline.md` (PG 18.6) + `platform/containers/container-lifecycle.md` (Docker/Podman) + `platform/architecture/architecture-decision.md` (modular monolith-first).

## Purpose

Turn the Business Analyst spec corpus into one buildable sequence: which BA docs are implemented in which increment, what enters/exits each increment, and which scope stays gated. Every increment ships as an independently runnable container image against PG 18.6 — no increment is done until its image passes the release gate.

## Users

- Technical Director / leads: sequence, staff, and accept increments.
- Developers / QA / Ops: build, verify, and promote exactly one increment at a time.

## Functional requirements

1. Governing rules (all increments): modular-monolith single image; single PG 18.6 (per-module schemas); frozen REST/cookie/`user_id`/idempotent-`orderCode` contract; Gradlew `9.8.1` + full `build` before any tag; per-release Docker AND Podman run drill. Carried BA corrections: SSO-only auth (no password credentials; see `docs/account/access/sso-login.md`), explicit-`provider` checkout/refund across the eight specified rails with no default (see `docs/monetize/overview/monetize-overview.md`), token-themes/`window.print()`-only PDF (see `docs/resume-builder/templates-print/resume-templates.md`).
2. E0 scaffold/platform (PLAN P0): Gradle/Kotlin/Boot/PG/compose baseline, Flyway V1 (users, sessions), health/actuator, CI `build` + image + SBOM. Exit: clean-checkout compose boot green under both runtimes.
3. E1 share + SEO (PLAN P1): `docs/link-to-bio/profile/*`, `docs/link-to-bio/blocks/link-blocks.md`, `docs/link-to-bio/appearance/themes-avatar.md`, `docs/link-to-bio/public-page/share-seo.md`, `docs/link-to-bio/analytics/profile-analytics.md` (batch counters via consumer; realtime-30min pro-gated), `docs/account/access/*`, `docs/account/plans/tier-presentation.md`. Exit: P1 load gate (10k concurrent reads p95 <500ms) + S-1/S-2/S-5/S-6 probes.
4. E2 resume + shop + leads (PLAN P2): `docs/resume-builder/documents/*`, `docs/resume-builder/templates-print/*`, `docs/resume-builder/sharing/*`, `docs/resume-builder/import-export/*`, `docs/shop/*` (catalog, POD merch, digital signed-URL, courses, tips, fees), `docs/leads/capture/*` + `docs/leads/manage/*` (CSV only), `docs/account/domains/*`, `docs/url-shortener/*` (short/QR/analytics serving resumes and bio). Exit: reprint-stable snapshots, paid-download and consent-timestamp drills green.
5. E3 monetize (PLAN P3): `docs/monetize/overview/*`, `docs/monetize/payment/*`, `docs/monetize/checkout/*`, `docs/monetize/billing/*`, `docs/monetize/plans/*`, `docs/monetize/subscription/*`, `docs/monetize/refunds/*`, `docs/monetize/invoices/*`, `docs/monetize/promo-code/*`, all eight provider minors (`payos`, `sepay`, `vnpay`, `zalopay`, `napas`, `google-pay`, `apple-pay`, `stripe`), `docs/monetize/organizations/agency-skeleton.md`. Exit: eight-rail sandbox grant/refund drills, 422-no-default drill, P3 gate (100k mixed).
6. E4 publish + automate + agency (PLAN P4, gated — never stubbed earlier): `docs/publish/*`, `docs/automate/*`, `docs/agency/*` (roster, act-as-user audited, reporting, seat billing). First split evaluations here (beacon ingest, fan-out) per the architecture triggers. BACKLOG (needs plan update before build): `docs/monetize/marketing/*`, `docs/monetize/affiliate/*`, `docs/monetize/advertising/*`, `docs/monetize/sales/*`, `docs/monetize/data-analysis/*`, `docs/monetize/user-behavior/*` — design-complete, build-gated.
7. E5 scale-out (PLAN P5, after P3 gate): replicas, ISR/pre-render, k6 1M-read/100k-mixed gates, quarterly restore + rotation drill.

## Data / API touches

- Traceability: increment → BA docs → PLAN steps → Flyway versions → image tag (`:<semver>-<sha>`), recorded in the release note. Migrations backward-compatible; breaking ones flagged with an app compatibility window.
- Promotion: same digest dev → staging → prod; per-release checks in `platform/delivery/release-gates.md`. No direct DB writes from clients/beacons; queue/consumer or server path only.
- Metrics: contract-test results, gate numbers, and probe outcomes attached to each promotion (not just pass/fail).

## Acceptance criteria

- Each increment exits only on: full Gradlew `build` green, container drills (Docker + Podman + compose cold boot + rollback) green, contract/OpenAPI diff clean, security checklist for its surface green (S-1…S-7 per PLAN), and its load gate where PLAN assigns one.
- Phase-boundary drill: E1→E2→E3 images each boot independently against empty PG 18.6 with Flyway from V1; previous-tag rollback demonstrated per increment.
- Scope drill: E4-gated and BACKLOG docs have no tables, endpoints, or jobs in E0–E3 images (grep + migration audit).

## Out of scope

- Per-release probe mechanics (see `platform/delivery/release-gates.md`); image/Dockerfile internals (see `platform/containers/container-lifecycle.md`); domain behavior (owning BA majors — consumed, never redefined here).

## Open questions

- Q1: E4 first split — beacon ingest or publish fan-out (decide at E3 exit on measured backlog)?
- Q2: Managed PG allowed for staging, or container-primary everywhere (same as PG-baseline Q2, decided once there)?
