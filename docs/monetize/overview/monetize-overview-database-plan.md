# Monetize Overview Database Plan (registry + config + lifecycle map — no new tables)

PLAN ref: P3-1/P3-2/P3-3/P3-4 (money lifecycle map; no-default rule; eight equal rails) + S-1/S-6. BA source: `docs/monetize/overview/monetize-overview.md`. Consumed by every monetize slice (ownership map — referenced, never redefined). Core tables live in their owning slices: `orders` in the checkout-session database plan, `subscriptions` in the entitlement-ledger database plan, refunds/receipts/catalog/coupons in the lifecycle plans (each `CREATE TABLE` exactly once, there). No migration — this slice creates zero tables.

## Purpose

Define the money module's shared data posture in one place: the closed eight-rail registry, the no-default contract's enforcement points, the uniform lifecycle every rail shares, and which config surfaces (not tables) drive eligibility, display order, and kill switches. No `CREATE TABLE` here by design — a registry table would let checkout offer rails no specified minor covers (the accommodation guarantee would become advisory).

## Users

- Implementer: finds the owning slice for any money question without guessing (ownership map, rule 5).
- QA: runs the registry and no-default drills from rules 1–2.
- Support/ops/finance: traces any order by `orderCode` across provider refs, ledger rows, receipts, and audit.

## Functional requirements

1. Provider registry (closed, eight, code-owned): `payos | sepay | vnpay | zalopay | napas | google-pay | apple-pay | stripe` — a code constant (`ProviderRegistry`), never a row. If a rail has no specified provider minor + registry entry, checkout cannot offer it (the checkout-session validator rejects unknown codes against this constant; the database cannot contradict code). Ninth-rail path: specified minor + registry entry + eligibility-config rows + conformance to the uniform `next` shape and status map — no contract changes.
2. No-default contract (normative, enforced at three points): `provider` required on every checkout create (omitted/unknown → `422 {code: provider_required|unknown_provider, eligible[]}`); refund execution reads the order's stored `provider` (never a parameter); display/listing order comes from ops config (API order verbatim when config absent — never a code-level fallback). Grep gate finds no hardcoded provider default or fallback ordering in checkout/refund/plan-purchase code (CI gate, not a review note).
3. Money lifecycle (uniform, every rail): quote (server re-priced, zero side effects) → create (one `orders` row, numeric int64 `orderCode` UNIQUE, `amount_vnd INT`, immutable `provider`, idempotent) → provider handoff (`next: url|qr|sheet|token`) → verified confirmation (webhook/callback/IPN/query/sweep, amount-checked, idempotent grant) → ledger entitlement → receipt → lifecycle (renew/repurchase, grace, downgrade) → refund/dispute via originating rail only. Return pages poll and render; they transition nothing.
4. Config surfaces (not tables): eligibility matrix (plan/product × provider) + display-order profile + kill switches live in ops config (fast-read, versioned, audited flips) — config because ops must flip rails in seconds without deploys or migrations. Secrets per rail via env/vault (never repo/logs/bundles; quarterly rotation with P5). VND integers end-to-end (VNPay ×100 normalized at the edge).
5. Shared-minor ownership map (single source of membership questions): selection (eligible set) → session (quote/create/expire/return) → dispatch (routing + kill switches) → statuses (canonical enum + legal edges) → security (probe battery) → ledger (plan truth) → lifecycle (periods/grace/downgrade) → receipts (evidence) → catalog/terms/trials/coupons (priced + discounted entry) → refunds (reversal routing). Sales/affiliate/advertising/user-behavior/data-analysis consume money events, never move money (backlog majors, gated).
6. Data discipline: every business/ledger table `user_id TEXT NOT NULL` + index + RLS; dashboard scopes by session `user_id`; webhooks authenticate by provider scheme and resolve ownership from stored rows, never request identity. No direct DB writes from clients; sweeps/jobs own expiry/reconciliation. Counters/throttles in the cache layer (never truth).

## Data / API touches

- DDL: none — assertion: `grep -rn "CREATE TABLE" docs/monetize/overview/` returns nothing. Consumed tables (owned elsewhere): `orders`, `subscriptions`, refunds ledger, catalog/coupon ledgers, `audit_log` (append-only). Core endpoints (owned by their slices): `POST /api/billing/quote`, `POST /api/billing/checkout` (explicit `provider`), `GET /api/billing/payment-methods`, `GET /api/v1/plan`, history/receipts reads.

## Acceptance criteria

- Registry drill: all eight member paths reachable exactly as specified; no ninth rail offerable without minor + entry.
- No-default drill: omitted/unknown `provider` → 422 with eligible list on every intent; refund reads stored provider; grep gate clean.
- Lifecycle drill per rail (sandbox): quote = charged = receipt to the VND; single row on double-submit; exactly-once verified grant; ledger visible to all gates within SLA; expiry/cancel races safe; full refund revokes with re-grant refused.
- Baseline drill: IDOR 403 + RLS deny (S-1/S-6); XSS strip + CSP (S-2); Turnstile + WAF throttles on anonymous money endpoints (S-5); secrets grep clean.
- Violation drill: any registry table, provider default constant, or fallback ordering fails review/gate.

## Out of scope

- Table DDL (owning slices' database plans); dispatcher/session/grant/refund logic (their backend plans); rail mechanics (provider minors); ledger/lifecycle/receipts/catalog/trials/refund internals (lifecycle plans); shop delivery, leads campaigns, P4 publish/automate/agency, P5 scale-out.

## Open questions

- Q1: Stripe-native recurring at launch vs one-shot-only everywhere (BA Q1)?
- Q2: Display-order sourcing — static ops config vs experiment-driven (BA Q2, decide once here and propagate)?
- Q3: Partial refunds at launch — full-only + re-purchase, or partial ledger entries day one (BA Q3)?
