# Architecture Decision (monolith-first, split per use case)

PLAN ref: §0 Backend decision (Option A Workers Hono default vs Option B external API) + §0 Leave-Cloudflare signals + P5-3 gates. DEVIATION FLAGGED: selects Option B (Spring Boot 4.1.1 service, containerized, PG 18.6) as the primary path from P0 instead of Option A default. Contract frozen so A↔B stays proxy-only.

## Purpose

Fix when the system runs as one deployable and when it splits, using explicit pros/cons per use case instead of a blanket microservices or monolith mandate. Default is a modular monolith; a service boundary is extracted only when its trigger is met, measured, and recorded. This keeps P1–P3 velocity while preserving a clean split path for P4–P5 scale.

## Users

- Technical Director / reviewers: approve or reject any split proposal against the triggers below.
- Backend developers: build inside module boundaries so a future extract is a move, not a rewrite.
- Ops / QA: deploy, gate, and roll back one artifact by default; operate extra services only where justified.

## Functional requirements

1. Default topology: single Spring Boot 4.1.1 artifact, multi-module (`resume`, `bio`, `billing`, `shop`, `leads`, `platform`) with enforced package boundaries (ArchUnit). One container image per release (see `platform/containers/container-lifecycle.md`).
2. Decision framework (applied per use case, recorded in the proposing spec): team ownership (≥2 independent teams hitting merge contention), failure isolation (billing grant/webhook must survive content-deploy faults), scaling asymmetry (public SSR reads vs checkout writes differ by ≥10x at gates), data gravity (beacon/analytics write volume). Score all four; split requires ≥2 triggers firing plus gate numbers.
3. Per-use-case stance (v1): resume/bio/dashboard/shop/leads read-write CRUD stays in-monolith (single domain, joins `users→profiles→blocks→orders`, one audit log). Billing grant path stays in-monolith but in an isolated module with its own datasource role and kill switches. Beacon ingest, publish fan-out (`POST_QUEUE`/`SCHEDULER` equivalents), and AI tailor are the first split candidates at P4 — async, bursty, independently scalable.
4. Pros/cons binding: monolith pros (one transaction scope for billing+ledger, one RLS policy set, one release train, cheaper at 10k–100k concurrent) vs cons (noisy-neighbor deploys, single blast radius). Microservices pros (independent deploy/scale of hot paths) vs cons (distributed transactions, cross-service auth/IDOR surface, two-plus audit logs, doubled backup/restore drills). Any split proposal must show the con it accepts and its mitigation.
5. Split contract (frozen): JSON REST, cookie session, server-scoped `user_id`, idempotent `orderCode`/webhooks, no direct DB from client, no cross-service direct table access (API or replicated read-model only). Monolith modules already communicate through these interfaces internally so extraction changes wiring, not semantics.

## Data / API touches

- Module → schema mapping on the single PG 18.6 database (one database, per-module schemas; see `platform/stack/postgres-baseline.md`): `resume`, `bio`, `billing`, `shop`, `leads`. Cross-module reads via service interface or SQL view, never direct cross-schema writes except billing grant transaction.
- Public surface unchanged from PLAN: `/api/v1/*`, `/api/billing/*`, SSR `/:username`, `/:username/cv`, `/:username/shop`; versioned (`/api/v1`) with additive-only minor changes.
- Observability per module: Micrometer + `/actuator/health` liveness/readiness, structured logs with `module`, `orderCode` correlation where applicable.

## Acceptance criteria

- ArchUnit gate: zero cross-module package violations on every build.
- Split-proposal checklist: triggers evidenced (gate numbers from P1/P3/P5 exits), pros/cons + mitigations recorded, contract conformance tests green, rollback to monolith image demonstrated on staging.
- Load gates unchanged: P1 exit ≥10k concurrent reads p95 <500ms; P3 exit ≥100k mixed; P5 exit ≥1M read-heavy + 100k mixed — all against the containerized topology.
- Baseline: IDOR 403 cross-user on every module API (S-1/S-6), RLS deny at DB as app role, XSS strip + CSP on rendered surfaces (S-2), anonymous routes throttled (S-5).

## Out of scope

- Image build/run/promotion mechanics (see `platform/containers/container-lifecycle.md`); Spring starters/config (see `platform/stack/spring-boot-baseline.md`); PG HA/backup/replica detail (see `platform/stack/postgres-baseline.md`).
- P4 publish/automate/agency behavior and P5 ISR/replica execution (owning majors; this doc only governs where they run).

## Open questions

- Q1: First split candidate at P4 — beacon ingest or publish fan-out (decide at P3 exit on measured backlog)?
- Q2: Keep SSR in the Spring artifact or behind CDN edge workers as pure proxy (decide before P1c)?
