# Monetize Overview - Architecture and Tech Stack (feature 32)

Phase 2 TL · Kernel of the money layer: registers `billing-service` (port 8088, `billing_db`, Redis db 10) hosting features 32-40 — the one new service this major introduces. BA sources: `docs/01-requirements-ba/32-monetize-overview/`. Consumer contracts fixed elsewhere are honored, never redefined: shop 23/24/25 (`order.granted.v1`, `order.refunded.v1`), leads 27 (status machine + internal status write), earnings 15 (order trace seam). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED (platform-wide): the legacy "billing module inside a monolith + Workers/Neon/Hono" verdict maps to `billing-service` in the mandated microservice topology (Spring Boot 4.1.1, PostgreSQL 18.6 per service). Registered in the baseline + `MIGRATION.md` in this same change. The extraction doctrine applies reciprocally: nothing splits out of it without ≥ 2 triggers. | Stack mandate; a single money owner preserves the uniform lifecycle. |
| D-2 | Registry as code: a Kotlin enum `PaymentRail` (eight members) + a capability map; never a database table. A ninth rail is additive: enum member, adapter, config rows, conformance row. | Closed set by construction; no drift, no dynamic rail injection. |
| D-3 | Config surfaces are versioned `ops_config` documents: eligibility matrix, display order (per surface), per-rail kill switches + pending TTL. Reads cached ≤ 5 s (`billing:cfg:{surface}` in Redis db 10); flips land through the audited ops route with a mandatory reason; effect ≤ 5 s. | Ops control without deploys (O-4); versioning makes flips reviewable. |
| D-4 | No-default enforcement is layered: create validates `provider`; refunds read the stored one; display order derives from config only; a CI grep gate (defaults, fallback orderings, favorite constants) plus unit tests make violations fail the build — not a review note. | BR-02 is normative at three points; the gate is mechanical. |
| D-5 | Conformance harness: an 8×N sandbox matrix runner producing a recorded per-release artifact; any rail's red cell blocks that rail's ship. The harness is built here; the per-rail rows run with feature 40. | Breadth never outruns conformance (O-3). |
| D-6 | Lifecycle map (owned jointly by 32-40, uniform for all rails): quote → create → handoff → verified confirmation → grant (product claim + ledger) → receipt → lifecycle jobs → refund/dispute. Return pages are display-only; nothing transitions from client input. | One flow to test, eight rails to wire. |
| D-7 | Audit spine: a single append-only `audit_log` (created with the first DDL, feature 33) records grants, transitions, refunds, disputes, config flips, ops overrides — secrets/signatures redacted. The ops trace route reads it per `orderCode`. | Evidence by orderCode (O-5). |
| D-8 | Notification pipeline: a module of `billing-service` (transactional email class) — the shared side-effect leads/shop/lifecycle features enqueue into (25/27/28's forward seam, now landed). Stream `notifications:q` (Redis db 10) + in-service consumer; template registry in code; class enum `transactional` only — a `marketing` request is 422 `marketing_not_accepted`; sends via the provider adapter (env/vault credentials); lead-bound sends (`ownerRef=leadId`) check feature 27's suppression (internal read) and write bounce/complaint outcomes back through 27's internal status path; dispatch ≤ 60 s p95; outage = backlog metric, grants unaffected. Extraction trigger recorded: sustained backlog or a second consumer class. | The references exist since batch 7; one owner, one queue, one class. |
| D-9 | Money API conventions: `/api/v1/billing/*` (legacy `/api/billing/*` mapped), `orderCode` non-sequential int64, RFC 9457 problem codes, additive-only under an OpenAPI diff gate, 5 s statement timeout, VND integers only. | BR-07/09; deterministic integration for shop and future majors. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Registry + capability map | `billing-service` | code enum; tests pin the eight members |
| Config store + ops routes | `billing-service` + Redis cache | `ops_config` versions; audited flips |
| Conformance harness | `billing-service` (CI job) | 8×N artifact per release |
| Orders/checkout (33), statuses/dispatch (34) | `billing-service` | first DDL (orders, audit_log, ops_config) |
| Ledger/plans (35), lifecycle (36), receipts (37), refunds (38), promos (39), rails (40) | `billing-service` | one deployable, one database |
| Audit spine + trace route | `billing-service` | append-only; ops-scoped reads |
| Notification pipeline | `billing-service` module | `notifications:q`; transactional only |

## Boundaries

Owns: registry, no-default enforcement, config surfaces, conformance gate, lifecycle map, audit/trace, notification pipeline, money API conventions. Does not own: rail mechanics (40); product/price truth (shop 22-26 — the fee engine stays there; the order snapshot is stored at checkout); lead status truth (27); fulfillment/course/booking effects (23/24/25 consume events); gated majors (48-53 consume only). No cross-service database access exists.

## Consumers

| Consumer | Contract |
|---|---|
| shop 23/24/25 | `order.granted.v1` / `order.refunded.v1` (shapes frozen there; restated in TL 03) |
| leads 27/28, shop 25, lifecycle 36, receipts 37 | `POST /internal/v1/notifications` (transactional) |
| earnings 15 | order-trace seam (read APIs from 34/38) |
| support/finance | `GET /api/v1/ops/billing/trace/{orderCode}` |
