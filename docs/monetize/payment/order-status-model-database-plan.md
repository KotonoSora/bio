# Order Status Model Database Plan (canonical enum + edges — no new tables)

PLAN ref: P3-1/P3-2 (one lifecycle for all rails; arbiter over provider-native states) + transition audit. BA source: `docs/monetize/payment/order-status-model.md`. Consumed by every rail adapter, sweep, and ops handler (transition authors; specified in their slices). The `orders.status` column CHECK text lives in the checkout-session database plan (next in queue — single definition; this file defines the enum, edges, and authority, never the DDL). No migration — this slice creates zero tables.

## Purpose

Define the single order lifecycle as data contract: the canonical enum, the legal-edge matrix, actor authority per edge, late-event rules, and the audit requirement that makes every trail replayable. Provider-native states map onto this enum in code (per-rail tables owned by rail slices); any new state is added here first or not at all.

## Users

- Backend developer (grant/sweep/ops paths): transitions only along legal edges via the guard (backend plan).
- Buyer surfaces: render one consistent vocabulary from canonical states (frontend plan).
- Support/ops: reads any order and knows precisely what can still happen to it.

## Functional requirements

1. Canonical enum (closed, nine values): `pending` (created, awaiting money) → `authorizing` (charge/sheet in flight, card/wallet rails) → `paid` (terminal success) | `failed` (terminal, provider-declined) | `cancelled` (terminal, buyer/ops) | `expired` (terminal, lapsed unpaid) | `refunded` (terminal, money returned) | `disputed` (frozen, chargeback open) → `chargeback_reversed` (terminal, dispute lost). `paid` feeds entitlements; `disputed` freezes them.
2. Legal-edge matrix (only these fire; everything else is rejected in code + tests): `pending → authorizing|paid|failed|cancelled|expired`; `authorizing → paid|failed|expired`; `paid → refunded|disputed`; `disputed → paid|chargeback_reversed` (won/lost). Terminal states never re-enter `pending`/`authorizing` (`paid` moves only toward refund/dispute). The matrix is a code table (`TransitionGuard.ALLOWED`), tested edge-by-edge per provider mapping.
3. Actor authority per edge: `pending → paid` ONLY via verified provider confirmations (webhook/callback/IPN/sync grant or status-query/sweep verify) through atomic, amount-checked transitions; `→ cancelled` via buyer/ops cancel endpoints (plus provider-side cancels mirrored in); `→ expired` via lifecycle/sweep jobs; `→ refunded|disputed|chargeback_reversed` via settlement handlers. Buyer clients and return pages transition nothing — they poll and render.
4. Late-event rule (uniform): confirmations arriving for terminal orders (`cancelled`/`expired`) are rejected with logging, never granted. Ops re-link runs only through audited exception flows on rail providers (SePay/NAPAS-style bank rails), never automatically — automation here would resurrect dead money.
5. Native-state mapping (code tables owned per rail slice): each rail documents its native states mapped onto the enum, including which native states are non-terminal and therefore transition nothing (a non-terminal native state firing a transition is a mapping bug, caught by the conformance matrix).
6. Audit contract: every transition appends `audit_log{actor, action, target, at}` with from→to + triggering event id (storage owned by platform identity; money slices append via `AuditPort`). Per-order trails replayable for support.
7. Buyer-visible projection: `status` (canonical) + `status_detail` (human string) — provider-native codes never leak to clients (projection owned by the read endpoint in the checkout-session slice; the rule lives here).

## Data / API touches

- DDL: none — assertion: `grep -rn "CREATE TABLE" docs/monetize/payment/` returns nothing. The `orders.status` CHECK text is owned by the checkout-session database plan (next in queue); this file owns the enum alphabet + edge matrix + authority (rules 1–4), which the CHECK mirrors. Any drift between CHECK alphabet and rule 1 fails review.
- Native mappings: per-rail adapter code (rail slices). Audit rows: platform `audit_log` via port.

## Acceptance criteria

- Matrix drill: every legal edge fires per provider mapping; every illegal edge refused (`expired → paid`, `failed → authorizing`, full cross-product asserted).
- Late-confirmation drill on `cancelled`/`expired` across all eight rails: zero grants, all logged.
- Enum drill: no off-enum value writable via any endpoint (CHECK + guard + test).
- Audit drill: random orders show complete from→to chains with event ids.
- Violation drill: any new status bypassing this file (rail-local enum addition without map entry) fails review.

## Out of scope

- Grant/verify/sweep mechanics (rail slices + checkout-session backend plan); dispatcher/selection (orchestration plans); probe battery (security plans); entitlement rows/periods (billing plans); receipts; P4/P5.

## Open questions

- Q1: `disputed → paid` (won) — restore original `period_end` or extend by frozen duration (BA Q1)?
- Q2: `partially_refunded` state in v1, or refunds ledger covers partials without a status (BA Q2)?
