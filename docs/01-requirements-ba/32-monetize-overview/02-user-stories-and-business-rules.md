# Monetize Overview - User Stories and Business Rules (feature 32)

Phase 1 BA · Realized by `docs/02-architecture-techlead/32-monetize-overview/` (BR-01..BR-16). Statuses: feature 34 owns the canonical enum and legal edges; rails: feature 40 owns mechanics — consumed here, never redefined.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | buy plans/products and request refunds on any eligible rail | one consistent experience regardless of method | Must |
| US-02 | Implementer | find the owning feature for any money question | no guessing between minors | Must |
| US-03 | Support/ops/finance | trace any order by `orderCode` across provider refs, ledger, receipts, audit | one map for every money question | Must |
| US-04 | Ops | flip eligibility/display/kill switches in seconds, audited | rails operate without deploys | Must |
| US-05 | Release/QA | trust that all eight rails behave identically | breadth never outruns conformance | Must |

## Business rules

- BR-01 Registry (closed, code-owned): exactly eight rails — `payos`, `sepay`, `vnpay`, `zalopay`, `napas`, `google-pay`, `apple-pay`, `stripe`; a code constant, never a table. A ninth rail requires: a specified provider minor + registry entry + eligibility-config rows + a full conformance run — no contract changes. Rails without a specified minor cannot be offered.
- BR-02 No-default (normative, three enforcement points): `provider` is required on every checkout create (omitted/unknown → 422 with the eligible list); refund execution reads the order's stored provider and takes no provider parameter; display/listing order comes from ops config only (API order verbatim when absent). No hardcoded default or fallback ordering exists anywhere — CI grep gate, not review note.
- BR-03 Uniform lifecycle (every rail): quote (server re-priced, zero side effects, 15-min validity) → create (one `orders` row; non-sequential unpredictable int64 `orderCode` UNIQUE; `amount_vnd INT`; immutable provider; idempotent) → provider handoff (`next: url|qr|sheet|token`) → verified confirmation (rail-scheme auth, amount-checked, idempotent grant) → entitlement/product claim → receipt → lifecycle (renew/repurchase, grace, downgrade) → refund/dispute via the originating rail only. Return pages poll and render; they transition nothing.
- BR-04 Status vocabulary: the canonical set `pending | authorizing | paid | failed | cancelled | expired | refunded | disputed | chargeback_reversed` with only feature 34's legal edges; provider-native codes never surface to buyers or other services.
- BR-05 Data discipline: every money/ledger table carries `user_id TEXT NOT NULL` + index + RLS; dashboards scope by session `user_id`; webhooks authenticate by provider scheme and resolve ownership from stored rows, never from request-supplied identity; clients never write DB directly; sweeps/jobs own expiry and reconciliation; counters/throttles live in cache, never as truth.
- BR-06 Config surfaces (not tables): eligibility matrix (plan/product × provider), display-order profile, per-rail kill switches + per-rail pending TTL — versioned ops config with an audited flip endpoint (reason mandatory); reads cached ≤ 5 s; flips effective ≤ 5 s.
- BR-07 Money API shape: `orderCode` int64 in every money URL/payload (no sequential public ids), RFC 9457 problem responses with stable `code` strings, additive-only evolution under an OpenAPI diff gate.
- BR-08 Idempotency: a unique idempotency key per create + a 90 s fingerprint dedupe for keyless double-clicks; same key/fingerprint → one row + same handoff; key reuse across intents → 422.
- BR-09 Audit spine: every grant, transition, refund, dispute, config flip, and ops override appends `audit_log{actor, action, target, at}` with secrets/signatures redacted; a per-`orderCode` trail is replayable for support and finance.
- BR-10 Kill switches: disabling a rail delists it within seconds, routes in-flight pendings to that rail's settlement path with buyer messaging, and is audited; valid traffic on other rails is unaffected.
- BR-11 Conformance gate: the shared drill row (quote = charged = receipt to the VND; double-submit single row; replay no-op grant; expiry/cancel races; tampered amount no grant; bad signature 401; illegal-edge refusal; late confirmation no grant) must be green per rail before any rail change ships — recorded, not spot-checked.
- BR-12 Secrets: per-rail PSP keys via environment/vault only — never in repo, logs, or bundles; CI secrets grep; quarterly rotation drill with no downtime and old credentials dead.
- BR-13 Notification pipeline (transactional class): confirmations, notices, and receipts enqueue via one queued side-effect that never blocks a grant; transactional sends are never marketing-gated, while marketing never rides this pipeline; lead-bound sends honor feature 27's status machine (hard bounce/suppression respected); provider bounce/complaint signals write back through 27's internal status path; outage shows as visible backlog with grants unaffected.
- BR-14 VND integers end-to-end: `amount_vnd INT`; no floating point anywhere in money paths; rail-specific scaling (VNPay ×100) is normalized at that rail's edge (feature 40).
- BR-15 Traceability: any order traces by `orderCode` across provider references, ledger rows, receipts, credit notes, and audit — the single support answer.
- BR-16 Consumption rule: sales, affiliate, advertising, user-behavior, and data-analysis majors (gated 48-53) consume money events and read endpoints; they never move money and own no money tables.
